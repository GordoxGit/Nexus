package com.heneria.nexus.service;

import com.heneria.nexus.util.NexusLogger;
import java.lang.reflect.Constructor;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Lightweight service registry used to bootstrap the plugin without relying on
 * external dependency injection frameworks.
 */
public final class ServiceRegistry {

    private final NexusLogger logger;
    private final Map<Class<?>, Object> singletons = new ConcurrentHashMap<>();
    private final Map<Class<?>, ServiceDefinition<?>> definitions = new LinkedHashMap<>();
    private final Map<Class<?>, ServiceHolder<?>> holders = new LinkedHashMap<>();
    private List<ServiceHolder<?>> startOrder = List.of();
    private boolean wired;
    private boolean started;

    public ServiceRegistry(NexusLogger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Registers an already constructed singleton object to the registry.
     *
     * <p>Singletons are available for constructor injection and lookups but are
     * not managed by the service lifecycle.</p>
     */
    public synchronized <T> void registerSingleton(Class<T> type, T instance) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(instance, "instance");
        ensureNotRegistered(type);
        singletons.put(type, instance);
    }

    /**
     * Replaces a singleton value previously registered.
     */
    public synchronized <T> void updateSingleton(Class<T> type, T instance) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(instance, "instance");
        if (!singletons.containsKey(type)) {
            throw new IllegalStateException("No singleton registered for " + type.getName());
        }
        singletons.put(type, instance);
    }

    /**
     * Registers a service implementation. The registry will use constructor
     * injection to resolve dependencies and will manage the lifecycle hooks of
     * the created instance.
     */
    public synchronized <T> void registerService(Class<T> serviceType, Class<? extends T> implementationType) {
        Objects.requireNonNull(serviceType, "serviceType");
        Objects.requireNonNull(implementationType, "implementationType");
        if (!serviceType.isAssignableFrom(implementationType)) {
            throw new IllegalArgumentException(implementationType.getName()
                    + " n'implémente pas " + serviceType.getName());
        }
        ensureNotRegistered(serviceType);
        ServiceDefinition<T> definition = ServiceDefinition.create(serviceType, implementationType);
        definitions.put(serviceType, definition);
        holders.put(serviceType, new ServiceHolder<>(definition));
    }

    private void ensureNotRegistered(Class<?> type) {
        if (singletons.containsKey(type) || definitions.containsKey(type)) {
            throw new IllegalStateException("A provider is already registered for " + type.getName());
        }
    }

    /**
     * Wires the registry by resolving the dependency graph and invoking the
     * {@link LifecycleAware#initialize()} hook on each managed service.
     */
    public synchronized void wire(Duration initializationTimeout) {
        Objects.requireNonNull(initializationTimeout, "initializationTimeout");
        if (wired) {
            return;
        }
        detectMissingDependencies();
        List<ServiceHolder<?>> order = computeTopologicalOrder();
        Deque<Class<?>> stack = new ArrayDeque<>();
        for (ServiceHolder<?> holder : order) {
            holder.ensureInitialized(this, stack, initializationTimeout);
        }
        this.startOrder = List.copyOf(order);
        this.wired = true;
        logInitializationReport();
    }

    /**
     * Starts all managed services in topological order.
     */
    public synchronized void startAll(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        if (!wired) {
            throw new IllegalStateException("Registry not wired yet");
        }
        if (started) {
            return;
        }
        List<ServiceHolder<?>> startedHolders = new ArrayList<>();
        for (ServiceHolder<?> holder : startOrder) {
            try {
                holder.start(timeout);
                startedHolders.add(holder);
            } catch (ServiceRegistryException exception) {
                Throwable cause = exception.getCause() != null ? exception.getCause() : exception;
                logger.error("Échec du démarrage du service " + holder.definition.serviceType().getSimpleName(), cause);
                holder.markFailed(cause);
                startedHolders.reversed().forEach(other -> safeStop(other, timeout));
                throw exception;
            }
        }
        this.started = true;
    }

    /**
     * Stops all managed services in reverse topological order.
     */
    public synchronized void stopAll(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        if (!wired) {
            return;
        }
        startOrder.reversed().forEach(holder -> safeStop(holder, timeout));
        started = false;
    }

    private void safeStop(ServiceHolder<?> holder, Duration timeout) {
        try {
            holder.stop(timeout);
        } catch (ServiceRegistryException exception) {
            Throwable cause = exception.getCause() != null ? exception.getCause() : exception;
            logger.error("Erreur lors de l'arrêt du service " + holder.definition.serviceType().getSimpleName(), cause);
        }
    }

    /**
     * Retrieves a managed service or singleton registered for the given type.
     */
    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> type) {
        Objects.requireNonNull(type, "type");
        Object singleton = singletons.get(type);
        if (singleton != null) {
            return (T) singleton;
        }
        ServiceHolder<?> holder = holders.get(type);
        if (holder == null) {
            throw new IllegalStateException("No service registered for " + type.getName());
        }
        return (T) holder.instance;
    }

    /**
     * Returns an optional view over a managed service or singleton.
     */
    public <T> Optional<T> find(Class<T> type) {
        Objects.requireNonNull(type, "type");
        if (singletons.containsKey(type)) {
            return Optional.of(type.cast(singletons.get(type)));
        }
        ServiceHolder<?> holder = holders.get(type);
        if (holder == null || holder.instance == null) {
            return Optional.empty();
        }
        return Optional.of(type.cast(holder.instance));
    }

    /**
     * Returns immutable diagnostics for every registered service.
     */
    public Collection<ServiceStateSnapshot> snapshot() {
        List<ServiceStateSnapshot> states = new ArrayList<>();
        holders.values().forEach(holder -> states.add(holder.snapshot()));
        return Collections.unmodifiableList(states);
    }

    private void detectMissingDependencies() {
        for (ServiceDefinition<?> definition : definitions.values()) {
            for (Dependency dependency : definition.dependencies()) {
                if (dependency.type() == ServiceRegistry.class) {
                    continue;
                }
                if (dependency.optional()) {
                    continue;
                }
                if (!definitions.containsKey(dependency.type()) && !singletons.containsKey(dependency.type())) {
                    throw new IllegalStateException("Unsatisfied dependency " + dependency.type().getName()
                            + " for service " + definition.serviceType().getName());
                }
            }
        }
    }

    private List<ServiceHolder<?>> computeTopologicalOrder() {
        Map<Class<?>, VisitState> visitStates = new LinkedHashMap<>();
        List<ServiceHolder<?>> order = new ArrayList<>();
        for (Class<?> type : definitions.keySet()) {
            dfs(type, visitStates, order, new ArrayDeque<>());
        }
        return order;
    }

    private void dfs(Class<?> type,
                     Map<Class<?>, VisitState> visitStates,
                     List<ServiceHolder<?>> order,
                     Deque<Class<?>> stack) {
        VisitState state = visitStates.get(type);
        if (state == VisitState.PERMANENT) {
            return;
        }
        if (state == VisitState.TEMPORARY) {
            stack.addLast(type);
            throw new ServiceRegistryException("Cycle détecté: " + cycleToString(stack));
        }
        visitStates.put(type, VisitState.TEMPORARY);
        stack.addLast(type);
        ServiceDefinition<?> definition = definitions.get(type);
        for (Dependency dependency : definition.dependencies()) {
            Class<?> dependencyType = dependency.type();
            if (!definitions.containsKey(dependencyType)) {
                continue;
            }
            dfs(dependencyType, visitStates, order, stack);
        }
        stack.removeLast();
        visitStates.put(type, VisitState.PERMANENT);
        order.add(holders.get(type));
    }

    private String cycleToString(Deque<Class<?>> stack) {
        return stack.stream().map(Class::getSimpleName).reduce((left, right) -> left + " -> " + right).orElse("<cycle>");
    }

    private void logInitializationReport() {
        logger.info("=== Services enregistrés ===");
        String header = String.format(Locale.ROOT, "%-22s | %-30s | Dépendances", "Service", "Implémentation");
        logger.info(header);
        logger.info("-".repeat(header.length()));
        for (ServiceHolder<?> holder : startOrder) {
            ServiceDefinition<?> definition = holder.definition;
            String deps = definition.dependencies().isEmpty()
                    ? "—"
                    : definition.dependencies().stream()
                            .filter(dep -> dep.type() != ServiceRegistry.class)
                            .map(dep -> dep.type().getSimpleName() + (dep.optional() ? "?" : ""))
                            .reduce((left, right) -> left + ", " + right)
                            .orElse("—");
            logger.info(String.format(Locale.ROOT,
                    "%-22s | %-30s | %s",
                    definition.serviceType().getSimpleName(),
                    definition.implementationType().getSimpleName(),
                    deps));
        }
    }

    private Object resolveDependency(Dependency dependency, Deque<Class<?>> stack, Duration timeout) {
        Class<?> type = dependency.type();
        if (type == ServiceRegistry.class) {
            return this;
        }
        if (singletons.containsKey(type)) {
            return singletons.get(type);
        }
        ServiceHolder<?> holder = holders.get(type);
        if (holder != null) {
            if (stack.contains(type)) {
                stack.addLast(type);
                throw new ServiceRegistryException("Cycle détecté: " + cycleToString(stack));
            }
            stack.addLast(type);
            Object value = holder.ensureInitialized(this, stack, timeout);
            stack.removeLast();
            return value;
        }
        if (dependency.optional()) {
            return null;
        }
        throw new ServiceRegistryException("Aucun fournisseur disponible pour " + type.getName());
    }

    private enum VisitState {
        TEMPORARY,
        PERMANENT
    }

    private static final class ServiceDefinition<T> {

        private final Class<T> serviceType;
        private final Class<? extends T> implementationType;
        private final Constructor<? extends T> constructor;
        private final List<Dependency> dependencies;

        private ServiceDefinition(Class<T> serviceType,
                                  Class<? extends T> implementationType,
                                  Constructor<? extends T> constructor,
                                  List<Dependency> dependencies) {
            this.serviceType = serviceType;
            this.implementationType = implementationType;
            this.constructor = constructor;
            this.dependencies = dependencies;
        }

        static <T> ServiceDefinition<T> create(Class<T> serviceType, Class<? extends T> implementationType) {
            Constructor<? extends T> constructor = selectConstructor(implementationType);
            List<Dependency> dependencies = analyseDependencies(constructor);
            return new ServiceDefinition<>(serviceType, implementationType, constructor, dependencies);
        }

        private static <T> Constructor<? extends T> selectConstructor(Class<? extends T> implementation) {
            Constructor<?>[] constructors = implementation.getDeclaredConstructors();
            if (constructors.length != 1) {
                throw new IllegalArgumentException("Implementation " + implementation.getName()
                        + " must declare exactly one constructor for injection");
            }
            Constructor<? extends T> constructor = (Constructor<? extends T>) constructors[0];
            if (!constructor.canAccess(null)) {
                constructor.setAccessible(true);
            }
            return constructor;
        }

        private static List<Dependency> analyseDependencies(Constructor<?> constructor) {
            List<Dependency> dependencies = new ArrayList<>();
            for (Parameter parameter : constructor.getParameters()) {
                dependencies.add(toDependency(parameter));
            }
            return dependencies;
        }

        private static Dependency toDependency(Parameter parameter) {
            Class<?> rawType = parameter.getType();
            if (Optional.class.equals(rawType)) {
                Type parameterized = parameter.getParameterizedType();
                if (!(parameterized instanceof ParameterizedType parameterizedType)) {
                    throw new IllegalArgumentException("Optional dependency without type information for "
                            + parameter.getDeclaringExecutable());
                }
                Type actualType = parameterizedType.getActualTypeArguments()[0];
                if (!(actualType instanceof Class<?> dependencyType)) {
                    throw new IllegalArgumentException("Unsupported optional dependency type " + actualType);
                }
                return new Dependency(dependencyType, true);
            }
            return new Dependency(rawType, false);
        }

        Class<T> serviceType() {
            return serviceType;
        }

        Class<? extends T> implementationType() {
            return implementationType;
        }

        Constructor<? extends T> constructor() {
            return constructor;
        }

        List<Dependency> dependencies() {
            return dependencies;
        }
    }

    private static final class Dependency {
        private final Class<?> type;
        private final boolean optional;

        private Dependency(Class<?> type, boolean optional) {
            this.type = type;
            this.optional = optional;
        }

        Class<?> type() {
            return type;
        }

        boolean optional() {
            return optional;
        }
    }

    private final class ServiceHolder<T> {

        private final ServiceDefinition<T> definition;
        private volatile T instance;
        private volatile ServiceLifecycle lifecycle = ServiceLifecycle.NEW;
        private volatile Throwable lastError;
        private volatile boolean healthy = true;
        private Duration initializationDuration = Duration.ZERO;
        private Duration startDuration = Duration.ZERO;
        private Duration stopDuration = Duration.ZERO;

        private ServiceHolder(ServiceDefinition<T> definition) {
            this.definition = definition;
        }

        T ensureInitialized(ServiceRegistry registry, Deque<Class<?>> stack, Duration timeout) {
            if (instance != null) {
                return instance;
            }
            synchronized (this) {
                if (instance != null) {
                    return instance;
                }
                if (lifecycle == ServiceLifecycle.INITIALIZING) {
                    stack.addLast(definition.serviceType());
                    throw new ServiceRegistryException("Cycle détecté: " + cycleToString(stack));
                }
                lifecycle = ServiceLifecycle.INITIALIZING;
                long startTime = System.nanoTime();
                try {
                    Object[] args = new Object[definition.dependencies().size()];
                    for (int index = 0; index < definition.dependencies().size(); index++) {
                        Dependency dependency = definition.dependencies().get(index);
                        Object resolved = resolveDependency(dependency, stack, timeout);
                        args[index] = dependency.optional() ? Optional.ofNullable(resolved) : Objects.requireNonNull(resolved);
                    }
                    T created = instantiate(args);
                    instance = created;
                    invokeInitialize(timeout);
                    initializationDuration = Duration.ofNanos(System.nanoTime() - startTime);
                    lifecycle = ServiceLifecycle.INITIALIZED;
                    updateHealth();
                    return created;
                } catch (ServiceRegistryException exception) {
                    lifecycle = ServiceLifecycle.FAILED;
                    lastError = exception.getCause();
                    throw exception;
                } catch (Throwable throwable) {
                    lifecycle = ServiceLifecycle.FAILED;
                    lastError = throwable;
                    throw new ServiceRegistryException("Impossible d'initialiser " + definition.serviceType().getSimpleName(), throwable);
                }
            }
        }

        private T instantiate(Object[] args) throws ReflectiveOperationException {
            Constructor<? extends T> constructor = definition.constructor();
            return constructor.newInstance(args);
        }

        private void invokeInitialize(Duration timeout) {
            if (!(instance instanceof LifecycleAware lifecycleAware)) {
                return;
            }
            CompletableFuture<Void> future = lifecycleAware.initialize();
            await("initialize", timeout, future);
        }

        void start(Duration timeout) {
            ensureInitialized(ServiceRegistry.this, new ArrayDeque<>(), timeout);
            if (!(instance instanceof LifecycleAware lifecycleAware)) {
                lifecycle = ServiceLifecycle.STARTED;
                return;
            }
            synchronized (this) {
                if (lifecycle == ServiceLifecycle.STARTED) {
                    return;
                }
                lifecycle = ServiceLifecycle.STARTING;
                long startTime = System.nanoTime();
                try {
                    CompletableFuture<Void> future = lifecycleAware.start();
                    await("start", timeout, future);
                    startDuration = Duration.ofNanos(System.nanoTime() - startTime);
                    lifecycle = ServiceLifecycle.STARTED;
                    updateHealth();
                } catch (ServiceRegistryException exception) {
                    lifecycle = ServiceLifecycle.FAILED;
                    lastError = exception.getCause();
                    throw exception;
                } catch (Throwable throwable) {
                    lifecycle = ServiceLifecycle.FAILED;
                    lastError = throwable;
                    throw new ServiceRegistryException("Erreur lors du démarrage de "
                            + definition.serviceType().getSimpleName(), throwable);
                }
            }
        }

        void stop(Duration timeout) {
            if (!(instance instanceof LifecycleAware lifecycleAware)) {
                lifecycle = ServiceLifecycle.STOPPED;
                return;
            }
            synchronized (this) {
                if (lifecycle == ServiceLifecycle.STOPPED || lifecycle == ServiceLifecycle.NEW) {
                    return;
                }
                lifecycle = ServiceLifecycle.STOPPING;
                long startTime = System.nanoTime();
                try {
                    CompletableFuture<Void> future = lifecycleAware.stop();
                    await("stop", timeout, future);
                    stopDuration = Duration.ofNanos(System.nanoTime() - startTime);
                    lifecycle = ServiceLifecycle.STOPPED;
                } catch (ServiceRegistryException exception) {
                    lifecycle = ServiceLifecycle.FAILED;
                    lastError = exception.getCause();
                    throw exception;
                } catch (Throwable throwable) {
                    lifecycle = ServiceLifecycle.FAILED;
                    lastError = throwable;
                    throw new ServiceRegistryException("Erreur lors de l'arrêt de "
                            + definition.serviceType().getSimpleName(), throwable);
                }
            }
        }

        void markFailed(Throwable throwable) {
            this.lifecycle = ServiceLifecycle.FAILED;
            this.lastError = throwable;
            this.healthy = false;
        }

        private void await(String phase, Duration timeout, CompletableFuture<Void> future) {
            Objects.requireNonNull(timeout, "timeout");
            try {
                if (!timeout.isZero() && !timeout.isNegative()) {
                    future.orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS).join();
                } else {
                    future.join();
                }
            } catch (Exception exception) {
                Throwable cause = unwrap(exception);
                throw new ServiceRegistryException("Phase " + phase + " en échec pour "
                        + definition.serviceType().getSimpleName(), cause);
            }
        }

        private Throwable unwrap(Throwable throwable) {
            if (throwable instanceof ServiceRegistryException registryException) {
                return registryException.getCause();
            }
            Throwable cause = throwable.getCause();
            return cause != null ? cause : throwable;
        }

        private void updateHealth() {
            if (instance instanceof LifecycleAware lifecycleAware) {
                healthy = lifecycleAware.isHealthy();
                lastError = lifecycleAware.lastError().orElse(lastError);
            }
        }

        ServiceStateSnapshot snapshot() {
            return new ServiceStateSnapshot(
                    definition.serviceType(),
                    lifecycle,
                    healthy,
                    Optional.ofNullable(lastError),
                    initializationDuration,
                    startDuration,
                    stopDuration,
                    definition.dependencies().stream()
                            .filter(dependency -> dependency.type() != ServiceRegistry.class)
                            .map(Dependency::type)
                            .toList());
        }
    }
}

*** End Patch
