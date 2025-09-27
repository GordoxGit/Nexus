package com.heneria.nexus.config;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Performs validation of each configuration file and materialises the
 * immutable runtime views consumed by the rest of the plugin.
 */
public final class ConfigValidator {

    private static final Locale DEFAULT_LOCALE = Locale.forLanguageTag("fr");
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Europe/Paris");
    private static final String DEFAULT_SERVER_MODE = "nexus";
    private static final Set<String> SUPPORTED_MAP_MODES = Set.of(
            "1v1", "2v2", "3v3", "4v4", "5v5", "ffa", "rush", "casual", "competitive");

    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public IssueCollector collector(String file, ReloadReport.Builder builder) {
        return new IssueCollector(file, builder);
    }

    public CoreConfig validateCore(YamlConfiguration yaml, IssueCollector issues) {
        Objects.requireNonNull(yaml, "yaml");
        Objects.requireNonNull(issues, "issues");

        String mode = readString(yaml, "server.mode", DEFAULT_SERVER_MODE, issues, false);
        Locale locale = parseLocale(readString(yaml, "server.language", DEFAULT_LOCALE.toLanguageTag(), issues, false),
                "server.language", issues);
        ZoneId zone = parseZone(readString(yaml, "server.timezone", DEFAULT_ZONE.getId(), issues, false), issues);

        int hudHz = boundedInt(yaml, "perf.hud_hz", 5, 1, 10, issues);
        int scoreboardHz = boundedInt(yaml, "perf.scoreboard_hz", 3, 2, 5, issues);
        int particlesSoft = positiveInt(yaml, "perf.particles_soft_cap", 1200, issues, true);
        int particlesHard = positiveInt(yaml, "perf.particles_hard_cap", 2000, issues, true);
        if (particlesHard <= particlesSoft) {
            issues.error("perf.particles_hard_cap", "Doit être strictement supérieur à perf.particles_soft_cap");
            particlesHard = particlesSoft + 1;
        }
        int maxEntities = nonNegativeInt(yaml, "perf.budget.max_entities", 200, issues);
        int maxItems = nonNegativeInt(yaml, "perf.budget.max_items", 128, issues);
        int maxProjectiles = nonNegativeInt(yaml, "perf.budget.max_projectiles", 64, issues);

        boolean ioVirtual = yaml.getBoolean("threads.io_virtual", yaml.getBoolean("executors.io.virtual", false));
        int ioPool = positiveInt(yaml, "threads.io_pool", yaml.getInt("executors.io.maxThreads", 3), issues, false);
        long ioKeepAlive = positiveLong(yaml, "threads.io_keep_alive_ms", yaml.getLong("executors.io.keepAliveMs", 30_000L), issues, false);
        int computePool = positiveInt(yaml, "threads.compute_pool", yaml.getInt("executors.compute.size", 1), issues, false);
        long shutdownAwait = positiveLong(yaml, "threads.shutdown.await_seconds", yaml.getLong("executors.shutdown.awaitSeconds", 5L), issues, false);
        long shutdownForce = positiveLong(yaml, "threads.shutdown.force_cancel_seconds", yaml.getLong("executors.shutdown.forceCancelSeconds", 3L), issues, false);
        int schedulerInterval = positiveInt(yaml, "threads.scheduler.main_check_interval_ticks", yaml.getInt("executors.scheduler.main_check_interval_ticks", 1), issues, false);

        boolean databaseEnabled = yaml.getBoolean("database.enabled", true);
        String jdbc = readString(yaml, "database.jdbc", "jdbc:mariadb://127.0.0.1:3306/nexus", issues, true);
        if (databaseEnabled && (jdbc == null || jdbc.isBlank())) {
            issues.error("database.jdbc", "Obligatoire lorsque database.enabled=true");
            jdbc = "jdbc:mariadb://127.0.0.1:3306/nexus";
        }
        String user = readString(yaml, "database.user", "nexus", issues, true);
        if (databaseEnabled && (user == null || user.isBlank())) {
            issues.error("database.user", "Obligatoire lorsque database.enabled=true");
            user = "nexus";
        }
        String password = readString(yaml, "database.password", "change_me", issues, false);
        int poolMax = positiveInt(yaml, "database.pool.maxSize", 10, issues, false);
        int poolMin = nonNegativeInt(yaml, "database.pool.minIdle", 2, issues);
        long poolTimeout = positiveLong(yaml, "database.pool.connTimeoutMs", 3000L, issues, true);
        long writeBehindSeconds = positiveLong(yaml, "database.write_behind_interval_seconds", 60L, issues, true);
        long profileCacheMaxSize = positiveLong(yaml, "database.cache.profiles.max_size", 1000L, issues, true);
        long profileCacheExpireMinutes = positiveLong(yaml, "database.cache.profiles.expire_after_access_minutes", 15L, issues, true);

        boolean exposeServices = yaml.getBoolean("services.expose-bukkit-services", false);
        long timeoutStart = positiveLong(yaml, "timeouts.startMs", 5000L, issues, true);
        long timeoutStop = positiveLong(yaml, "timeouts.stopMs", 3000L, issues, true);
        long watchdogReset = positiveLong(yaml, "timeouts.watchdog.reset_ms", 10_000L, issues, true);
        long watchdogPaste = positiveLong(yaml, "timeouts.watchdog.paste_ms", 8_000L, issues, true);
        boolean degradedEnabled = yaml.getBoolean("degraded-mode.enabled", true);
        boolean degradedBanner = yaml.getBoolean("degraded-mode.banner", true);
        int queueHz = positiveInt(yaml, "queue.tick_hz", 5, issues, true);
        int queueVip = nonNegativeInt(yaml, "queue.vip_weight", 0, issues);
        boolean strictMiniMessage = yaml.getBoolean("ui.minimessage.strict", true);

        int hologramHz = positiveInt(yaml, "holograms.update_hz", 5, issues, true);
        int hologramMaxVisible = positiveInt(yaml, "holograms.max_visible_per_instance", 80, issues, true);
        double hologramSpacing = positiveDouble(yaml, "holograms.line_spacing", 0.27D, issues, true);
        double hologramViewRange = positiveDouble(yaml, "holograms.view_range", 48.0D, issues, true);
        int hologramPoolText = nonNegativeInt(yaml, "holograms.pool.max_text_displays", 64, issues);
        int hologramPoolInteractions = nonNegativeInt(yaml, "holograms.pool.max_interactions", 32, issues);

        boolean analyticsEnabled = yaml.getBoolean("analytics.outbox.enabled", false);
        long flushIntervalSeconds = positiveLong(yaml, "analytics.outbox.flush_interval_seconds", 30L, issues, true);
        int analyticsMaxBatch = positiveInt(yaml, "analytics.outbox.max_batch_size", 200, issues, true);

        Map<String, CoreConfig.TitleTimesProfile> titleProfiles = new LinkedHashMap<>();
        ConfigurationSection timesSection = yaml.getConfigurationSection("ui.title.times");
        if (timesSection != null) {
            for (String key : timesSection.getKeys(false)) {
                ConfigurationSection profileSection = timesSection.getConfigurationSection(key);
                if (profileSection == null) {
                    issues.error("ui.title.times." + key, "Profil de titre invalide");
                    continue;
                }
                String basePath = "ui.title.times." + key;
                int fadeIn = readNonNegative(profileSection, "fadeIn", basePath + ".fadeIn", 5, issues);
                int stay = readPositive(profileSection, "stay", basePath + ".stay", 40, issues);
                int fadeOut = readNonNegative(profileSection, "fadeOut", basePath + ".fadeOut", 10, issues);
                try {
                    titleProfiles.put(key, new CoreConfig.TitleTimesProfile(fadeIn, stay, fadeOut));
                } catch (IllegalArgumentException exception) {
                    issues.error(basePath, exception.getMessage());
                }
            }
        }
        if (titleProfiles.isEmpty()) {
            issues.warn("ui.title.times", "Aucun profil valide, utilisation des valeurs par défaut");
            titleProfiles.put("short", new CoreConfig.TitleTimesProfile(5, 40, 10));
            titleProfiles.put("normal", new CoreConfig.TitleTimesProfile(10, 60, 20));
            titleProfiles.put("long", new CoreConfig.TitleTimesProfile(20, 100, 40));
        }

        ConfigurationSection bossBarSection = yaml.getConfigurationSection("ui.bossbar.defaults");
        CoreConfig.BossBarDefaults bossBarDefaults;
        if (bossBarSection != null) {
            String colorRaw = readString(bossBarSection, "color", "PURPLE", "ui.bossbar.defaults.color", issues);
            String overlayRaw = readString(bossBarSection, "overlay", "NOTCHED_20", "ui.bossbar.defaults.overlay", issues);
            BossBar.Color color = parseBossBarColor(colorRaw, "ui.bossbar.defaults.color", issues);
            BossBar.Overlay overlay = parseBossBarOverlay(overlayRaw, "ui.bossbar.defaults.overlay", issues);
            int updateEveryTicks = readPositive(bossBarSection, "updateEveryTicks", "ui.bossbar.defaults.updateEveryTicks", 10, issues);
            Set<BossBar.Flag> flags = parseBossBarFlags(bossBarSection, "ui.bossbar.defaults.flags", issues);
            try {
                bossBarDefaults = new CoreConfig.BossBarDefaults(color, overlay, flags, updateEveryTicks);
            } catch (IllegalArgumentException exception) {
                issues.error("ui.bossbar.defaults", exception.getMessage());
                bossBarDefaults = new CoreConfig.BossBarDefaults(BossBar.Color.PURPLE, BossBar.Overlay.NOTCHED_20, Set.of(), 10);
            }
        } else {
            issues.warn("ui.bossbar.defaults", "Section manquante, utilisation des valeurs par défaut");
            bossBarDefaults = new CoreConfig.BossBarDefaults(BossBar.Color.PURPLE, BossBar.Overlay.NOTCHED_20, Set.of(), 10);
        }

        CoreConfig.UiSettings uiSettings = new CoreConfig.UiSettings(strictMiniMessage, titleProfiles, bossBarDefaults);

        CoreConfig.ArenaSettings arenaSettings;
        CoreConfig.ExecutorSettings executorSettings;
        CoreConfig.PoolSettings poolSettings;
        CoreConfig.DatabaseSettings databaseSettings;
        CoreConfig.ServiceSettings serviceSettings = new CoreConfig.ServiceSettings(exposeServices);
        CoreConfig.TimeoutSettings timeoutSettings;
        CoreConfig.DegradedModeSettings degradedModeSettings = new CoreConfig.DegradedModeSettings(degradedEnabled, degradedBanner);
        CoreConfig.QueueSettings queueSettings;
        CoreConfig.HologramSettings hologramSettings;

        try {
            arenaSettings = new CoreConfig.ArenaSettings(hudHz, scoreboardHz, particlesSoft, particlesHard,
                    maxEntities, maxItems, maxProjectiles);
        } catch (IllegalArgumentException exception) {
            issues.error("perf", exception.getMessage());
            arenaSettings = new CoreConfig.ArenaSettings(5, 3, 1200, 2000, 200, 128, 64);
        }
        CoreConfig.ExecutorSettings.IoSettings ioSettings;
        try {
            ioSettings = new CoreConfig.ExecutorSettings.IoSettings(ioVirtual, Math.max(1, ioPool), Math.max(0L, ioKeepAlive));
        } catch (IllegalArgumentException exception) {
            issues.error("threads.io_pool", exception.getMessage());
            ioSettings = new CoreConfig.ExecutorSettings.IoSettings(false, 3, 30_000L);
        }
        CoreConfig.ExecutorSettings.ComputeSettings computeSettings;
        try {
            computeSettings = new CoreConfig.ExecutorSettings.ComputeSettings(Math.max(1, computePool));
        } catch (IllegalArgumentException exception) {
            issues.error("threads.compute_pool", exception.getMessage());
            computeSettings = new CoreConfig.ExecutorSettings.ComputeSettings(1);
        }
        CoreConfig.ExecutorSettings.ShutdownSettings shutdownSettings;
        try {
            shutdownSettings = new CoreConfig.ExecutorSettings.ShutdownSettings(Math.max(0L, shutdownAwait), Math.max(0L, shutdownForce));
        } catch (IllegalArgumentException exception) {
            issues.error("threads.shutdown", exception.getMessage());
            shutdownSettings = new CoreConfig.ExecutorSettings.ShutdownSettings(5L, 3L);
        }
        CoreConfig.ExecutorSettings.SchedulerSettings schedulerSettings;
        try {
            schedulerSettings = new CoreConfig.ExecutorSettings.SchedulerSettings(Math.max(1, schedulerInterval));
        } catch (IllegalArgumentException exception) {
            issues.error("threads.scheduler.main_check_interval_ticks", exception.getMessage());
            schedulerSettings = new CoreConfig.ExecutorSettings.SchedulerSettings(1);
        }
        executorSettings = new CoreConfig.ExecutorSettings(ioSettings, computeSettings, shutdownSettings, schedulerSettings);

        try {
            poolSettings = new CoreConfig.PoolSettings(poolMax, poolMin, poolTimeout);
        } catch (IllegalArgumentException exception) {
            issues.error("database.pool", exception.getMessage());
            poolSettings = new CoreConfig.PoolSettings(10, 2, 3000L);
        }
        CoreConfig.DatabaseSettings.ProfileCacheSettings profileCacheSettings;
        try {
            profileCacheSettings = new CoreConfig.DatabaseSettings.ProfileCacheSettings(
                    Math.max(1L, profileCacheMaxSize),
                    java.time.Duration.ofMinutes(Math.max(1L, profileCacheExpireMinutes)));
        } catch (IllegalArgumentException exception) {
            issues.error("database.cache.profiles", exception.getMessage());
            profileCacheSettings = new CoreConfig.DatabaseSettings.ProfileCacheSettings(1000L, java.time.Duration.ofMinutes(15L));
        }
        CoreConfig.DatabaseSettings.CacheSettings cacheSettings = new CoreConfig.DatabaseSettings.CacheSettings(profileCacheSettings);

        databaseSettings = new CoreConfig.DatabaseSettings(databaseEnabled, jdbc, user, password, poolSettings,
                java.time.Duration.ofSeconds(Math.max(1L, writeBehindSeconds)), cacheSettings);

        CoreConfig.TimeoutSettings.WatchdogSettings watchdogSettings;
        try {
            watchdogSettings = new CoreConfig.TimeoutSettings.WatchdogSettings(watchdogReset, watchdogPaste);
        } catch (IllegalArgumentException exception) {
            issues.error("timeouts.watchdog", exception.getMessage());
            watchdogSettings = new CoreConfig.TimeoutSettings.WatchdogSettings(10_000L, 8_000L);
        }
        try {
            timeoutSettings = new CoreConfig.TimeoutSettings(timeoutStart, timeoutStop, watchdogSettings);
        } catch (IllegalArgumentException exception) {
            issues.error("timeouts", exception.getMessage());
            timeoutSettings = new CoreConfig.TimeoutSettings(5000L, 3000L,
                    new CoreConfig.TimeoutSettings.WatchdogSettings(10_000L, 8_000L));
        }
        try {
            queueSettings = new CoreConfig.QueueSettings(queueHz, queueVip);
        } catch (IllegalArgumentException exception) {
            issues.error("queue", exception.getMessage());
            queueSettings = new CoreConfig.QueueSettings(5, 0);
        }
        try {
            hologramSettings = new CoreConfig.HologramSettings(hologramHz, hologramMaxVisible,
                    hologramSpacing, hologramViewRange, hologramPoolText, hologramPoolInteractions);
        } catch (IllegalArgumentException exception) {
            issues.error("holograms", exception.getMessage());
            hologramSettings = new CoreConfig.HologramSettings(5, 80, 0.27D, 48.0D, 64, 32);
        }

        CoreConfig.AnalyticsSettings.OutboxSettings outboxSettings;
        try {
            outboxSettings = new CoreConfig.AnalyticsSettings.OutboxSettings(analyticsEnabled,
                    java.time.Duration.ofSeconds(Math.max(1L, flushIntervalSeconds)), Math.max(1, analyticsMaxBatch));
        } catch (IllegalArgumentException exception) {
            issues.error("analytics.outbox", exception.getMessage());
            outboxSettings = new CoreConfig.AnalyticsSettings.OutboxSettings(false, java.time.Duration.ofSeconds(30L), 200);
        }
        CoreConfig.AnalyticsSettings analyticsSettings = new CoreConfig.AnalyticsSettings(outboxSettings);

        return new CoreConfig(mode, locale, zone, arenaSettings, executorSettings, databaseSettings,
                serviceSettings, timeoutSettings, degradedModeSettings, queueSettings, hologramSettings,
                analyticsSettings, uiSettings);
    }

    public MessageBundle validateMessages(YamlConfiguration yaml, Locale expectedLocale, IssueCollector issues) {
        Objects.requireNonNull(yaml, "yaml");
        Objects.requireNonNull(issues, "issues");

        Locale defaultLocale = expectedLocale != null ? expectedLocale : DEFAULT_LOCALE;
        Locale locale = parseLocale(readString(yaml, "meta.locale", defaultLocale.toLanguageTag(), issues, false),
                "meta.locale", issues);
        if (expectedLocale != null && !localeMatches(locale, expectedLocale)) {
            issues.warn("meta.locale", "Locale déclarée " + locale.toLanguageTag()
                    + " différente du nom de fichier (" + expectedLocale.toLanguageTag() + ")");
        }
        MessageBundle.Builder builder = MessageBundle.builder(locale, Instant.now());

        if (yaml.contains("prefix")) {
            String rawPrefix = yaml.getString("prefix");
            if (rawPrefix != null) {
                parseComponent(rawPrefix, "prefix", issues).ifPresent(component -> builder.prefix(rawPrefix, component));
            }
        }

        ConfigurationSection root = yaml;
        for (String key : root.getKeys(false)) {
            if (key.equals("meta") || key.equals("prefix")) {
                continue;
            }
            Object value = root.get(key);
            consumeValue(builder, key, value, issues);
        }

        if (builder.isEmpty()) {
            issues.error("<root>", "Aucune entrée valide dans le fichier de messages");
        }

        return builder.build();
    }

    private void consumeValue(MessageBundle.Builder builder, String path, Object value, IssueCollector issues) {
        if (value instanceof ConfigurationSection section) {
            for (String child : section.getKeys(false)) {
                consumeValue(builder, path + "." + child, section.get(child), issues);
            }
            return;
        }
        if (value instanceof String string) {
            parseComponent(string, path, issues).ifPresent(component -> builder.add(path, string, component));
            return;
        }
        if (value instanceof List<?> list) {
            List<Component> components = new ArrayList<>();
            List<String> raws = new ArrayList<>();
            int index = 0;
            for (Object element : list) {
                if (!(element instanceof String entry)) {
                    issues.warn(path, "Entrée de liste non textuelle ignorée (index=" + index + ")");
                    index++;
                    continue;
                }
                Optional<Component> parsed = parseComponent(entry, path + "[" + index + "]", issues);
                parsed.ifPresent(component -> {
                    raws.add(entry);
                    components.add(component);
                });
                index++;
            }
            if (!components.isEmpty()) {
                builder.addList(path, raws, components);
            }
            return;
        }
        issues.warn(path, "Type non supporté: " + (value == null ? "null" : value.getClass().getSimpleName()));
    }

    private Optional<Component> parseComponent(String raw, String path, IssueCollector issues) {
        try {
            return Optional.of(miniMessage.deserialize(raw));
        } catch (Exception exception) {
            issues.error(path, "MiniMessage invalide: " + exception.getMessage());
            return Optional.empty();
        }
    }

    public MapsCatalogConfig validateMaps(YamlConfiguration yaml, IssueCollector issues) {
        Objects.requireNonNull(yaml, "yaml");
        Objects.requireNonNull(issues, "issues");

        ConfigurationSection rotationSection = yaml.getConfigurationSection("rotation");
        boolean rotationEnabled = rotationSection != null && rotationSection.getBoolean("enabled", true);
        boolean weightedPick = rotationSection != null && rotationSection.getBoolean("weighted_pick", true);
        boolean vetoVote = rotationSection != null && rotationSection.getBoolean("veto_vote", true);
        int minVotes = rotationSection != null ? nonNegativeInt(rotationSection, "min_votes_to_pick", 0, issues) : 0;
        MapsCatalogConfig.RotationSettings rotation = new MapsCatalogConfig.RotationSettings(rotationEnabled, weightedPick, vetoVote, minVotes);

        List<MapsCatalogConfig.MapEntry> entries = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        List<?> mapsList = yaml.getList("maps");
        if (mapsList == null || mapsList.isEmpty()) {
            issues.warn("maps", "Aucune map définie dans maps.yml");
        } else {
            int index = 0;
            for (Object element : mapsList) {
                if (!(element instanceof java.util.Map<?, ?> map)) {
                    issues.error("maps", "Entrée maps[" + index + "] invalide");
                    index++;
                    continue;
                }
                String id = Optional.ofNullable(map.get("id")).map(Object::toString).orElse(null);
                if (id == null || id.isBlank()) {
                    issues.error("maps", "maps[" + index + "].id manquant");
                    index++;
                    continue;
                }
                String normalized = id.toLowerCase(Locale.ROOT);
                if (!ids.add(normalized)) {
                    issues.error("maps", "maps[" + index + "] id dupliqué: " + id);
                    index++;
                    continue;
                }
                String display = Optional.ofNullable(map.get("display")).map(Object::toString).orElse(id);
                int weight = parseInt(map.get("weight"), 1);
                if (weight <= 0) {
                    issues.error("maps", "maps[" + index + "].weight doit être >= 1");
                    weight = 1;
                }
                Object modesRaw = map.get("modes");
                List<String> modes = new ArrayList<>();
                if (modesRaw instanceof List<?> list) {
                    for (Object mode : list) {
                        if (mode == null) {
                            continue;
                        }
                        String value = mode.toString().trim();
                        if (value.isEmpty()) {
                            continue;
                        }
                        String normalizedMode = value.toLowerCase(Locale.ROOT);
                        if (!SUPPORTED_MAP_MODES.contains(normalizedMode)) {
                            issues.error("maps", "Mode inconnu " + value + " pour " + id);
                        } else {
                            modes.add(value);
                        }
                    }
                }
                if (modes.isEmpty()) {
                    issues.warn("maps", "maps[" + index + "] sans modes valides");
                }
                try {
                    entries.add(new MapsCatalogConfig.MapEntry(id, display, weight, List.copyOf(modes)));
                } catch (IllegalArgumentException exception) {
                    issues.error("maps", "maps[" + index + "]: " + exception.getMessage());
                }
                index++;
            }
        }
        return new MapsCatalogConfig(rotation, List.copyOf(entries));
    }

    public EconomyConfig validateEconomy(YamlConfiguration yaml, IssueCollector issues) {
        Objects.requireNonNull(yaml, "yaml");
        Objects.requireNonNull(issues, "issues");

        int winCoins = nonNegativeInt(yaml, "coins.win_per_match", 20, issues);
        int loseCoins = nonNegativeInt(yaml, "coins.lose_per_match", 5, issues);
        int firstWin = nonNegativeInt(yaml, "coins.first_win_bonus", 50, issues);
        if (winCoins < loseCoins) {
            issues.error("coins.win_per_match", "Doit être >= coins.lose_per_match");
            winCoins = loseCoins;
        }
        EconomyConfig.CoinsSettings coinsSettings = new EconomyConfig.CoinsSettings(winCoins, loseCoins, firstWin);

        int seasonDays = positiveInt(yaml, "battlepass.season_days", 90, issues, true);
        int tiers = positiveInt(yaml, "battlepass.tiers", 65, issues, true);
        int xpWin = nonNegativeInt(yaml, "battlepass.xp_per_match.win", 120, issues);
        int xpLose = nonNegativeInt(yaml, "battlepass.xp_per_match.lose", 60, issues);
        if (xpWin < xpLose) {
            issues.error("battlepass.xp_per_match.win", "Doit être >= battlepass.xp_per_match.lose");
            xpWin = xpLose;
        }
        EconomyConfig.XpPerMatch xpPerMatch = new EconomyConfig.XpPerMatch(xpWin, xpLose);
        EconomyConfig.BattlePassSettings battlePassSettings = new EconomyConfig.BattlePassSettings(seasonDays, tiers, xpPerMatch);

        Map<String, EconomyConfig.ClassEntry> classEntries = new LinkedHashMap<>();
        ConfigurationSection classesSection = yaml.getConfigurationSection("shop.classes");
        if (classesSection == null || classesSection.getKeys(false).isEmpty()) {
            issues.warn("shop.classes", "Aucune classe définie dans la boutique");
        } else {
            for (String classId : classesSection.getKeys(false)) {
                String path = "shop.classes." + classId;
                long cost = classesSection.getLong(classId, 0L);
                if (!classesSection.isSet(classId)) {
                    issues.warn(path, "Valeur manquante, utilisation de 0");
                }
                if (cost < 0L) {
                    issues.error(path, "Le coût doit être >= 0");
                    cost = 0L;
                }
                try {
                    classEntries.put(classId, new EconomyConfig.ClassEntry(cost));
                } catch (IllegalArgumentException exception) {
                    issues.error(path, exception.getMessage());
                }
            }
        }

        Map<String, EconomyConfig.CosmeticEntry> cosmeticEntries = new LinkedHashMap<>();
        ConfigurationSection cosmeticsSection = yaml.getConfigurationSection("shop.cosmetics");
        if (cosmeticsSection == null || cosmeticsSection.getKeys(false).isEmpty()) {
            issues.warn("shop.cosmetics", "Aucun cosmétique défini dans la boutique");
        } else {
            for (String cosmeticId : cosmeticsSection.getKeys(false)) {
                ConfigurationSection cosmeticSection = cosmeticsSection.getConfigurationSection(cosmeticId);
                String basePath = "shop.cosmetics." + cosmeticId;
                if (cosmeticSection == null) {
                    issues.error(basePath, "Configuration invalide, section attendue");
                    continue;
                }
                long cost = cosmeticSection.getLong("cost", 0L);
                if (!cosmeticSection.isSet("cost")) {
                    issues.warn(basePath + ".cost", "Valeur manquante, utilisation de 0");
                }
                if (cost < 0L) {
                    issues.error(basePath + ".cost", "Le coût doit être >= 0");
                    cost = 0L;
                }
                String type = cosmeticSection.getString("type");
                if (type == null || type.isBlank()) {
                    issues.error(basePath + ".type", "Type obligatoire pour le cosmétique");
                    continue;
                }
                try {
                    cosmeticEntries.put(cosmeticId, new EconomyConfig.CosmeticEntry(type, cost));
                } catch (IllegalArgumentException exception) {
                    issues.error(basePath, exception.getMessage());
                }
            }
        }

        EconomyConfig.ShopSettings shopSettings = new EconomyConfig.ShopSettings(classEntries, cosmeticEntries);

        return new EconomyConfig(coinsSettings, battlePassSettings, shopSettings);
    }

    private Locale parseLocale(String tag, String path, IssueCollector issues) {
        try {
            Locale locale = Locale.forLanguageTag(tag == null ? DEFAULT_LOCALE.toLanguageTag() : tag);
            if (locale == null || locale.toLanguageTag().isBlank()) {
                throw new IllegalArgumentException("Locale vide");
            }
            return locale;
        } catch (Exception exception) {
            issues.warn(path, "Locale invalide, utilisation de fr" + " (" + exception.getMessage() + ")");
            return DEFAULT_LOCALE;
        }
    }

    private boolean localeMatches(Locale left, Locale right) {
        if (left == null || right == null) {
            return false;
        }
        if (left.toLanguageTag().equalsIgnoreCase(right.toLanguageTag())) {
            return true;
        }
        String leftLanguage = left.getLanguage();
        String rightLanguage = right.getLanguage();
        return !leftLanguage.isEmpty()
                && leftLanguage.equalsIgnoreCase(rightLanguage);
    }

    private ZoneId parseZone(String id, IssueCollector issues) {
        try {
            return ZoneId.of(id == null ? DEFAULT_ZONE.getId() : id);
        } catch (Exception exception) {
            issues.warn("server.timezone", "Fuseau horaire invalide, utilisation de " + DEFAULT_ZONE.getId());
            return DEFAULT_ZONE;
        }
    }

    private String readString(YamlConfiguration yaml, String path, String def, IssueCollector issues, boolean required) {
        if (!yaml.isSet(path)) {
            if (required) {
                issues.warn(path, "Valeur manquante, utilisation de " + def);
            }
            return def;
        }
        String value = yaml.getString(path);
        if (value == null) {
            if (required) {
                issues.error(path, "Valeur manquante");
            }
            return def;
        }
        return value;
    }

    private int boundedInt(YamlConfiguration yaml, String path, int def, int min, int max, IssueCollector issues) {
        int value = yaml.getInt(path, def);
        if (!yaml.isSet(path)) {
            issues.warn(path, "Valeur manquante, utilisation de " + def);
            return value;
        }
        if (value < min || value > max) {
            issues.error(path, "Doit être compris entre " + min + " et " + max + ");");
            return Math.max(min, Math.min(max, value));
        }
        return value;
    }

    private int positiveInt(YamlConfiguration yaml, String path, int def, IssueCollector issues, boolean warnOnMissing) {
        int value = yaml.getInt(path, def);
        if (!yaml.isSet(path) && warnOnMissing) {
            issues.warn(path, "Valeur manquante, utilisation de " + def);
        }
        if (value <= 0) {
            issues.error(path, "Doit être > 0");
            return Math.max(1, value);
        }
        return value;
    }

    private double positiveDouble(YamlConfiguration yaml, String path, double def, IssueCollector issues, boolean warnOnMissing) {
        double value = yaml.getDouble(path, def);
        if (!yaml.isSet(path) && warnOnMissing) {
            issues.warn(path, "Valeur manquante, utilisation de " + def);
        }
        if (value <= 0D) {
            issues.error(path, "Doit être > 0");
            return def;
        }
        return value;
    }

    private long positiveLong(YamlConfiguration yaml, String path, long def, IssueCollector issues, boolean warnOnMissing) {
        long value = yaml.getLong(path, def);
        if (!yaml.isSet(path) && warnOnMissing) {
            issues.warn(path, "Valeur manquante, utilisation de " + def);
        }
        if (value <= 0L) {
            issues.error(path, "Doit être > 0");
            return Math.max(1L, value);
        }
        return value;
    }

    private int nonNegativeInt(YamlConfiguration yaml, String path, int def, IssueCollector issues) {
        int value = yaml.getInt(path, def);
        if (!yaml.isSet(path)) {
            issues.warn(path, "Valeur manquante, utilisation de " + def);
            return value;
        }
        if (value < 0) {
            issues.error(path, "Doit être >= 0");
            return Math.max(0, value);
        }
        return value;
    }

    private int nonNegativeInt(ConfigurationSection section, String path, int def, IssueCollector issues) {
        if (!section.isSet(path)) {
            issues.warn(path, "Valeur manquante, utilisation de " + def);
            return def;
        }
        int value = section.getInt(path, def);
        if (value < 0) {
            issues.error(path, "Doit être >= 0");
            return Math.max(0, value);
        }
        return value;
    }

    private int readNonNegative(ConfigurationSection section, String key, String path, int def, IssueCollector issues) {
        if (!section.isSet(key)) {
            issues.warn(path, "Valeur manquante, utilisation de " + def);
            return def;
        }
        int value = section.getInt(key, def);
        if (value < 0) {
            issues.error(path, "Doit être >= 0");
            return Math.max(0, value);
        }
        return value;
    }

    private int readPositive(ConfigurationSection section, String key, String path, int def, IssueCollector issues) {
        if (!section.isSet(key)) {
            issues.warn(path, "Valeur manquante, utilisation de " + def);
            return def;
        }
        int value = section.getInt(key, def);
        if (value <= 0) {
            issues.error(path, "Doit être > 0");
            return Math.max(1, value);
        }
        return value;
    }

    private String readString(ConfigurationSection section, String key, String def, String path, IssueCollector issues) {
        if (!section.isSet(key)) {
            issues.warn(path, "Valeur manquante, utilisation de " + def);
            return def;
        }
        String value = section.getString(key);
        if (value == null || value.isBlank()) {
            issues.warn(path, "Valeur vide, utilisation de " + def);
            return def;
        }
        return value;
    }

    private BossBar.Color parseBossBarColor(String raw, String path, IssueCollector issues) {
        if (raw == null || raw.isBlank()) {
            issues.warn(path, "Valeur manquante, utilisation de PURPLE");
            return BossBar.Color.PURPLE;
        }
        try {
            return BossBar.Color.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            issues.error(path, "Couleur invalide: " + raw);
            return BossBar.Color.PURPLE;
        }
    }

    private BossBar.Overlay parseBossBarOverlay(String raw, String path, IssueCollector issues) {
        if (raw == null || raw.isBlank()) {
            issues.warn(path, "Valeur manquante, utilisation de NOTCHED_20");
            return BossBar.Overlay.NOTCHED_20;
        }
        try {
            return BossBar.Overlay.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            issues.error(path, "Overlay invalide: " + raw);
            return BossBar.Overlay.NOTCHED_20;
        }
    }

    private Set<BossBar.Flag> parseBossBarFlags(ConfigurationSection section, String path, IssueCollector issues) {
        if (!section.isList("flags")) {
            return Set.of();
        }
        List<String> entries = section.getStringList("flags");
        if (entries.isEmpty()) {
            return Set.of();
        }
        EnumSet<BossBar.Flag> flags = EnumSet.noneOf(BossBar.Flag.class);
        int index = 0;
        for (String entry : entries) {
            if (entry == null || entry.isBlank()) {
                index++;
                continue;
            }
            String normalized = entry.trim().toUpperCase(Locale.ROOT);
            try {
                flags.add(BossBar.Flag.valueOf(normalized));
            } catch (IllegalArgumentException exception) {
                issues.warn(path + "[" + index + "]", "Flag inconnu: " + entry);
            }
            index++;
        }
        return Set.copyOf(flags);
    }

    private int parseInt(Object value, int def) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String string) {
            try {
                return Integer.parseInt(string.trim());
            } catch (NumberFormatException ignored) {
                return def;
            }
        }
        return def;
    }

    public static final class IssueCollector {

        private final String file;
        private final ReloadReport.Builder builder;
        private boolean hasErrors;

        private IssueCollector(String file, ReloadReport.Builder builder) {
            this.file = Objects.requireNonNull(file, "file");
            this.builder = Objects.requireNonNull(builder, "builder");
        }

        public void warn(String path, String message) {
            builder.warn(file, path, message);
        }

        public void error(String path, String message) {
            hasErrors = true;
            builder.error(file, path, message);
        }

        public boolean hasErrors() {
            return hasErrors;
        }
    }
}
