# T-007 — Event Bus interne & Contrats d'événements domaine

## 1. Objectifs et périmètre

Mettre à disposition un bus d'événements léger, embarqué et sans dépendance externe pour orchestrer les flux métier de Nexus
(matchmaking, arènes, économie, UI). Le bus doit :

- Découpler les producteurs (services métier, ring scheduler, persistance) des consommateurs (UI, MMF, monitoring) via un pattern
  publish/subscribe intra-process.
- Garantir le respect des règles de threading Paper : tout accès Bukkit/Paper doit être exécuté sur le thread principal, tandis
  que les traitements lourds (I/O, calcul) sont déportés sur les pools `ExecutorManager` (T-003).
- Fournir des mécanismes de contrôle de pression (coalescing, debounce, queues bornées) pour éviter de saturer le main thread
  ou les pools asynchrones.
- Offrir une instrumentation native (compteurs, latence, `/nexus dump`) pour diagnostiquer les flux et les handlers défaillants.

La présente spécification couvre les contrats d'API, les règles d'exécution, la configuration et les événements de domaine
initialement supportés. L'implémentation est réalisée en interne (pas de Guava EventBus ou équivalent).

## 2. Concepts et terminologie

- **DomainEvent** : valeur immuable (record Java) décrivant un fait métier. Porte un horodatage `Instant` et les identifiants
  nécessaires (arène, joueur, etc.).
- **EventBus** : service singleton orchestrant l'enregistrement des handlers, la mise en file des événements et leur dispatch.
- **EventHandler** : fonction consommateur (`void onEvent(E event, EventContext ctx)`) exécutée dans le contexte déterminé par les
  hints de dispatch et les options d'abonnement.
- **DispatchHints** : ensemble de drapeaux déclarés par le producteur pour guider le thread ciblé, la politique de coalescence,
  la portée (scope) ou la temporisation.
- **EventSubscription** : poignée retournée au moment de l'abonnement, permettant la désinscription et l'inspection de métadonnées
  (handlers actifs, options, statistiques locales).
- **Canal / Scope** : subdivision logique du bus (global, arène, joueur) utilisée pour isoler les files, quotas et coalescences.

## 3. Interfaces contractuelles

### 3.1 DomainEvent

```java
public sealed interface DomainEvent permits ArenaEvent, QueueEvent, ProfileEvent, EconomyEvent, UiEvent, SystemEvent {
    Instant at();
}
```

Chaque sous-type (`ArenaEvent`, `QueueEvent`, etc.) est une interface vide regroupant les records spécifiques. Tous les records
sont immuables, sérialisables et thread-safe par construction (pas de mutateurs, collections non modifiables).

### 3.2 EventBus

```java
public interface EventBus {
    <E extends DomainEvent> EventSubscription subscribe(
            Class<E> eventType,
            EventHandler<E> handler,
            SubscriptionOptions options);

    void unsubscribe(EventSubscription subscription);

    void post(DomainEvent event, DispatchHints hints);
}
```

- `SubscriptionOptions` encapsule : `Priority priority`, `Predicate<E> filter`, `Scope scope`, `CoalescingStrategy coalescing`,
  `boolean enforceMainThread`, `boolean allowReentrant`.
- `post` est non bloquant : l'événement est mis en file selon le scope et traité selon les hints.

### 3.3 EventHandler & EventContext

```java
@FunctionalInterface
public interface EventHandler<E extends DomainEvent> {
    void onEvent(E event, EventContext context) throws Exception;
}

public interface EventContext {
    DispatchHints hints();
    Scope scope();
    void bridgeToMain(Runnable task);
    boolean isOnMainThread();
    Executor computeExecutor();
    Executor ioExecutor();
}
```

- `EventContext#bridgeToMain` planifie une action Paper sur le thread principal (utilise `ExecutorManager#mainThread()` ou le
  scheduler Paper selon la plateforme).
- `computeExecutor` et `ioExecutor` exposent les pools T-003 pour les handlers souhaitant chaîner des traitements différés.

### 3.4 DispatchHints

`DispatchHints` est une valeur immuable (builder ou record) combinant les drapeaux suivants :

| Hint | Description | Contraintes |
| --- | --- | --- |
| `SYNC_MAIN` | Dispatch sur le thread principal Paper. Si `post` est appelé hors main, l'événement est planifié via scheduler Paper. | Obligatoire pour tout handler susceptible d'appeler l'API Bukkit/Paper. |
| `ASYNC_COMPUTE` | Dispatch sur le pool compute (T-003). | Interdiction d'appels Bukkit. Guard runtime avec log `ERROR` si violation. |
| `ASYNC_IO` | Dispatch sur le pool IO (T-003). | Pour charges de persistance / réseau. |
| `COALESCE` | Active l'agrégation par clé. | Combine avec `CoalescingStrategy`. |
| `ARENA_SCOPED(<id>)` | Oriente vers la file dédiée à l'arène `ArenaId`. | File bornée selon config `events.queues.arena`. |
| `PLAYER_SCOPED(<uuid>)` | Oriente vers la file du joueur. | Optionnel pour événements UI ciblés. |
| `GLOBAL` | Canal par défaut (système). | File bornée `events.queues.global`. |
| `DEBOUNCE(ms)` | Ignore les événements arrivant plus tôt que la fenêtre pour la même clé. | Basé sur l'horodatage `Instant`. |

Les hints peuvent être combinés (ex. `ASYNC_IO + ARENA_SCOPED`). Si aucun hint de thread n'est fourni, la stratégie par défaut
du type d'événement est utilisée (voir §7).

### 3.5 EventSubscription

```java
public interface EventSubscription extends AutoCloseable {
    UUID id();
    Class<? extends DomainEvent> eventType();
    SubscriptionOptions options();
    void unsubscribe();
    @Override default void close() { unsubscribe(); }
    HandlerStats stats();
}
```

- `HandlerStats` regroupe le nombre d'événements reçus, le temps moyen/95e percentile, le nombre de drops par coalescing/debounce
  et le dernier thread observé.
- Les subscriptions sont stockées faiblement (référence faible) pour détecter les fuites ; `/nexus dump` rapporte les handlers
  orphelins.

## 4. Règles d'exécution & threading

1. **Main thread obligatoire** : tout handler exécuté avec hint `SYNC_MAIN` vérifie `Bukkit.isPrimaryThread()`. Si faux, l'événement
   est replanifié via `BukkitScheduler#runTask(plugin, ...)`. Le bridge gère également les plateformes Folia.
2. **Async compute / IO** : les hints `ASYNC_COMPUTE` et `ASYNC_IO` utilisent respectivement `ExecutorManager#compute()` et
   `ExecutorManager#io()`. Toute tentative d'appel Bukkit détectée (ex. par `ThreadGuard`) est refusée avec log `ERROR` et compteur
   `handlerErrors{type=threadViolation}`.
3. **Ordonnancement** : les handlers d'un même événement et d'un même scope sont triés par `Priority` (`LOW < NORMAL < HIGH`). Les
   handlers ayant la même priorité sont exécutés selon l'ordre FIFO d'inscription.
4. **Isolation** : les exceptions levées par un handler sont capturées, loggées (`WARN`), incrémentent `handlerErrors{type}` et ne
   perturbent pas les autres handlers ni le producteur.
5. **Re-entrance** : par défaut, le bus empêche la re-émission du même type d'événement par un handler tant que l'instance courante
   n'est pas entièrement dispatchée (guard par `ThreadLocal`). Cette protection est configurable par event type via
   `SubscriptionOptions#allowReentrant` (utiliser avec parcimonie).

## 5. Gestion de la pression & fiabilité

### 5.1 Files et saturation

- Chaque scope (global, arène, joueur) dispose d'une queue bornée configurée (`events.queues`).
- Politique en cas de saturation : `drop-oldest`. L'événement évincé est loggé en `WARN` (avec throttle) et incrémente
  `events.dropped{reason="queue_saturation", scope}`.
- Les producteurs peuvent inspecter `EventBus#post` via la valeur booléenne `DispatchOutcome` (exposer via overload) pour savoir si
  l'événement a été accepté ou droppé.

### 5.2 Coalescing

- Activé via `DispatchHints#COALESCE` et `CoalescingStrategy`. Exemple : `NexusHpChanged` utilise une clé `(arenaId, team)` et une
  fenêtre configurée (`events.coalesce.NexusHpChanged.windowMs`).
- Le bus conserve uniquement l'événement le plus récent par clé dans la fenêtre et incrémente `events.coalesced{type}` pour les
  occurrences fusionnées.
- Les handlers reçoivent un snapshot agrégé (event mis à jour avant dispatch).

### 5.3 Debounce

- Déclenché via hint `DEBOUNCE(ms)` ou configuration statique (`events.debounce`).
- Les événements arrivant plus tôt que la fenêtre glissante sont ignorés (non dispatchés). Compteur `events.debounced{type}` et
  log `TRACE` (optionnel) pour inspection.

### 5.4 Filtres & prédicats

- Les `Predicate<E> filter` sont évalués **avant** la mise en file. Si le filtre retourne `false`, l'événement est ignoré sans
  toucher aux quotas.
- Les filtres sont exécutés sur le thread du producteur ; ils doivent être non bloquants.

## 6. Canaux & scoping

| Scope | Description | Queue max par défaut | Usage principal |
| --- | --- | --- | --- |
| `GLOBAL` | Événements système ou cross-arène. | `events.queues.global.maxPending` (2048). | Config reload, matchmaking global. |
| `ARENA` | Un scope par `ArenaId`. | `events.queues.arena.maxPending` (512). | Gameplay, HUD, reset. |
| `PLAYER` | Un scope par `UUID`. | `events.queues.player.maxPending` (256). | UI ciblée, notifications. |

Les abonnements peuvent restreindre leur écoute à un sous-ensemble (ex. `options.scope().restrictToArena(arenaId)`).

## 7. Contrats d'événements du domaine

Tous les événements sont des records immuables avec horodatage `Instant` et identifiants pertinents. Les hints recommandés sont
spécifiés pour guider le dispatch.

### 7.1 Arène / gameplay

```java
public record ArenaPhaseChanged(
        ArenaId arena,
        ArenaPhase previous,
        ArenaPhase current,
        Instant at) implements ArenaEvent {}
```
- Hints par défaut : `SYNC_MAIN`, `ARENA_SCOPED(arena)`. Produit par `ArenaService`. Consommateurs : Ring Scheduler (T-004), HUD.

```java
public record ArenaScored(
        ArenaId arena,
        Team scorer,
        int newScore,
        Instant at) implements ArenaEvent {}
```
- Hints : `SYNC_MAIN`, `ARENA_SCOPED(arena)`.

```java
public record NexusHpChanged(
        ArenaId arena,
        Team team,
        int hpOld,
        int hpNew,
        Instant at) implements ArenaEvent {}
```
- Hints : `SYNC_MAIN`, `COALESCE`, `ARENA_SCOPED(arena)`. Coalescing clé `(arena, team)`, fenêtre `75 ms` (config par défaut).

```java
public record ArenaResetStarted(
        ArenaId arena,
        Duration estimate,
        Instant at) implements ArenaEvent {}

public record ArenaResetCompleted(
        ArenaId arena,
        Duration duration,
        Instant at) implements ArenaEvent {}
```
- Hints : `SYNC_MAIN`, `ARENA_SCOPED(arena)`.

### 7.2 Queue / matchmaking

```java
public record QueueJoined(
        UUID player,
        Mode mode,
        Instant at) implements QueueEvent {}
```
- Hints : `ASYNC_COMPUTE`, `PLAYER_SCOPED(player)`.

```java
public record QueueMatched(
        Mode mode,
        List<UUID> players,
        String mapId,
        Instant at) implements QueueEvent {}
```
- Hints : production `ASYNC_COMPUTE`, handlers critiques (téléport) utilisent `context.bridgeToMain()` pour interaction Bukkit.

### 7.3 Profils / économie

```java
public record ProfileLoaded(
        UUID player,
        ProfileSnapshot snapshot,
        Instant at) implements ProfileEvent {}
```
- Hints : `ASYNC_IO`, `PLAYER_SCOPED(player)`. Les consommateurs UI doivent rebasculer via `bridgeToMain`.

```java
public record CoinsChanged(
        UUID player,
        int before,
        int after,
        CoinsReason reason,
        Instant at) implements EconomyEvent {}
```
- Hints : `ASYNC_IO`, `PLAYER_SCOPED(player)`.

### 7.4 UI / HUD

```java
public record HudTick(
        ArenaId arena,
        ArenaPhase phase,
        Instant at) implements UiEvent {}
```
- Produit par le Ring Scheduler (T-004). Hints : `SYNC_MAIN`, `ARENA_SCOPED(arena)`. Non posté si `RingScheduler` détecte MSPT
  dégradé.

```java
public record BossbarUpdateRequested(
        ArenaId arena,
        BossbarKey key,
        float progress,
        Instant at) implements UiEvent {}
```
- Hints : `SYNC_MAIN`, `COALESCE`, `ARENA_SCOPED(arena)`, `DEBOUNCE(50ms)`.

### 7.5 Configuration / système

```java
public record ConfigReloaded(
        ReloadReport report,
        Instant at) implements SystemEvent {}
```
- Hints : `GLOBAL`, `ASYNC_COMPUTE`. Handlers nécessitant l'API Bukkit doivent se rebasculer via `bridgeToMain`.

## 8. Intégrations clés

| Service | Rôle | Interaction avec le bus |
| --- | --- | --- |
| `ExecutorManager` (T-003) | Fournit les executors `main`, `compute`, `io`. | Utilisé pour router les hints de thread et pour `bridgeToMain`. |
| `RingScheduler` (T-004) | Consomme `ArenaPhaseChanged`, produit `HudTick`. | Accroche un handler `HIGH` pour changer de profil ; publie les ticks HUD. |
| `ConfigManager` (T-005) | Publie `ConfigReloaded`. | Les services abonnés adaptent leur configuration via le bus. |
| `MMF` (T-006) | Écoute `ArenaScored`, `ConfigReloaded`. | Publie titles/messages sur le main thread via `bridgeToMain`. |
| `MatchmakingService` | Publie `QueueMatched`. | Orchestration téléport via handler sync. |

## 9. Observabilité & instrumentation

- **Compteurs globaux** :
  - `events.posted{type}`
  - `events.dispatched{type, scope}`
  - `events.dropped{reason}` (`coalesced`, `debounced`, `queue_saturation`)
  - `events.latency_ms{type}` (histogramme)
  - `handlers.errors{type, cause}`
- **/nexus dump** (ajout section EventBus) :
  - Top 10 événements par volume (sur 1/5/15 min).
  - Latence moyenne/95e percentile par type.
  - Taille courante des queues par scope.
  - Liste des abonnements actifs (type, priorité, scope, thread cible).
- **Logs** :
  - `WARN` si un handler `SYNC_MAIN` dépasse `events.warnLongHandlerMs` (default 8 ms) — log avec identifiant handler, type, durée.
  - `WARN` (throttlé) si saturation récurrente (`queue_saturation`).
  - `ERROR` pour violation thread Bukkit (appel hors main).

## 10. Configuration (`config.yml`)

```yaml
events:
  coalesce:
    NexusHpChanged:
      windowMs: 75
      perArena: true
    BossbarUpdateRequested:
      windowMs: 50
      perArena: true
  debounce:
    BossbarUpdateRequested:
      windowMs: 50
  queues:
    global:
      maxPending: 2048
    arena:
      maxPending: 512
    player:
      maxPending: 256
  warnLongHandlerMs: 8
```

- Les fenêtres de coalescence sont configurables par type ; `perArena=true` force la clé `(arenaId, ...)`.
- `warnLongHandlerMs` protège le main thread : tout handler synchronisé dépassant la valeur déclenche un log.

## 11. Stratégie d'arrêt & sécurité

- `onDisable` : stoppe le bus, purge les queues (dispatch final optionnel), désinscrit toutes les subscriptions.
- Les handlers en vol sont attendus (grâce à un `CountDownLatch` ou `ExecutorManager#drain`) pour garantir une sortie propre.
- Les références des subscriptions sont faibles pour éviter les fuites ; un audit `/nexus dump` liste les handlers non fermés.

## 12. Plan de validation

1. **Threading** :
   - Poster `ArenaPhaseChanged` depuis un thread asynchrone : vérifier via log que le handler UI s'exécute sur `main` (`Thread.currentThread().getName()`).
   - Poster `ProfileLoaded` → handler compute puis `bridgeToMain` pour mise à jour UI.
2. **Coalescing** :
   - Émettre 20 `NexusHpChanged` en 50 ms → vérifier qu'un seul dispatch par `(arena, team)` se produit, compteur `events.coalesced` ≥ 19.
3. **Saturation** :
   - Configurer `arena.maxPending=1`, poster 3 événements rapides → 2 drops loggés (`WARN`) + compteur `queue_saturation`.
4. **Debounce** :
   - Poster `BossbarUpdateRequested` en rafale (<50 ms) → seuls les événements espacés d'au moins 50 ms sont dispatchés.
5. **Observabilité** :
   - `/nexus dump` affiche les métriques (top 10, queues, abonnés).
6. **Arrêt propre** :
   - `onDisable` → aucune tâche restante, queues vidées, logs confirmant l'arrêt.

## 13. Annexes

### 13.1 Priorités

```java
public enum Priority {
    LOW(-10),
    NORMAL(0),
    HIGH(10);

    private final int weight;

    Priority(int weight) { this.weight = weight; }
    public int weight() { return weight; }
}
```

### 13.2 Example d'abonnement UI

```java
EventSubscription sub = eventBus.subscribe(
        NexusHpChanged.class,
        (event, ctx) -> hudService.updateHp(event),
        SubscriptionOptions.builder()
                .priority(Priority.HIGH)
                .scope(Scope.forArena(event.arena()))
                .enforceMainThread(true)
                .build());

// onDisable
sub.close();
```

Cette spécification sert de référence pour l'implémentation de T-007.
