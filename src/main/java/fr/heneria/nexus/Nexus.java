package fr.heneria.nexus;

import fr.heneria.nexus.arena.manager.ArenaManager;
import fr.heneria.nexus.arena.repository.ArenaRepository;
import fr.heneria.nexus.arena.repository.JdbcArenaRepository;
import fr.heneria.nexus.command.NexusAdminCommand;
import fr.heneria.nexus.command.PlayCommand;
import fr.heneria.nexus.db.HikariDataSourceProvider;
import fr.heneria.nexus.listener.PlayerConnectionListener;
import fr.heneria.nexus.listener.AdminConversationListener;
import fr.heneria.nexus.listener.AdminPlacementListener;
import fr.heneria.nexus.listener.GameListener;
import fr.heneria.nexus.player.manager.PlayerManager;
import fr.heneria.nexus.player.repository.JdbcPlayerRepository;
import fr.heneria.nexus.player.repository.PlayerRepository;
import fr.heneria.nexus.economy.manager.EconomyManager;
import fr.heneria.nexus.admin.conversation.AdminConversationManager;
import fr.heneria.nexus.admin.placement.AdminPlacementManager;
import fr.heneria.nexus.shop.manager.ShopManager;
import fr.heneria.nexus.shop.repository.JdbcShopRepository;
import fr.heneria.nexus.shop.repository.ShopRepository;
import fr.heneria.nexus.game.kit.manager.KitManager;
import fr.heneria.nexus.game.kit.repository.KitRepository;
import fr.heneria.nexus.game.kit.repository.JdbcKitRepository;
import fr.heneria.nexus.game.manager.GameManager;
import fr.heneria.nexus.game.repository.MatchRepository;
import fr.heneria.nexus.game.repository.JdbcMatchRepository;
import fr.heneria.nexus.game.queue.QueueManager;
import fr.heneria.nexus.game.GameConfig;
import fr.heneria.nexus.sanction.SanctionManager;
import fr.heneria.nexus.sanction.repository.JdbcSanctionRepository;
import fr.heneria.nexus.sanction.repository.SanctionRepository;
import fr.heneria.nexus.npc.NpcManager;
import fr.heneria.nexus.npc.NpcRepository;
import fr.heneria.nexus.npc.JdbcNpcRepository;
import fr.heneria.nexus.npc.NpcListener;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;

public final class Nexus extends JavaPlugin {

    private HikariDataSourceProvider dataSourceProvider;
    private ArenaManager arenaManager;
    private PlayerManager playerManager;
    private EconomyManager economyManager;
    private ShopManager shopManager;
    private KitManager kitManager;
    private GameManager gameManager;
    private QueueManager queueManager;
    private SanctionManager sanctionManager;
    private NpcManager npcManager;

    @Override
    public void onEnable() {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(this.getClassLoader());

        try {
            this.dataSourceProvider = new HikariDataSourceProvider();
            this.dataSourceProvider.init(this);

            try (Connection connection = this.dataSourceProvider.getDataSource().getConnection()) {
                Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
                Liquibase liquibase = new Liquibase("db/changelog/master.xml", new ClassLoaderResourceAccessor(getClassLoader()), database);
                liquibase.update();
                getLogger().info("✅ Migrations de la base de données gérées par Liquibase.");
            }

            // CORRECTION: L'instance du plugin n'est plus passée ici
            PlayerRepository playerRepository = new JdbcPlayerRepository(this.dataSourceProvider.getDataSource());
            ShopRepository shopRepository = new JdbcShopRepository(this.dataSourceProvider.getDataSource());
            KitRepository kitRepository = new JdbcKitRepository(this.dataSourceProvider.getDataSource());
            MatchRepository matchRepository = new JdbcMatchRepository(this.dataSourceProvider.getDataSource());
            SanctionRepository sanctionRepository = new JdbcSanctionRepository(this.dataSourceProvider.getDataSource());

            this.arenaManager = new ArenaManager(new JdbcArenaRepository(this.dataSourceProvider.getDataSource()));
            this.playerManager = new PlayerManager(playerRepository);
            this.economyManager = new EconomyManager(this.playerManager, this.dataSourceProvider.getDataSource());
            this.shopManager = new ShopManager(shopRepository);
            KitManager.init(kitRepository);
            this.kitManager = KitManager.getInstance();
            this.kitManager.loadKits();
            NpcRepository npcRepository = new JdbcNpcRepository(this.dataSourceProvider.getDataSource());
            this.npcManager = new NpcManager(npcRepository);
            this.npcManager.loadNpcs();
            GameConfig.init(this);
            GameManager.init(this, this.arenaManager, this.playerManager, matchRepository, this.kitManager, this.shopManager, this.economyManager);
            this.gameManager = GameManager.getInstance();
            SanctionManager.init(sanctionRepository, playerRepository, this.economyManager);
            this.sanctionManager = SanctionManager.getInstance();
            QueueManager.init(this.gameManager, this.arenaManager, this.sanctionManager);
            this.queueManager = QueueManager.getInstance();

            AdminConversationManager.init(this.arenaManager, this.shopManager, shopRepository, this.kitManager, this);
            AdminPlacementManager.init();
            this.arenaManager.loadArenas();
            this.shopManager.loadItems();
            getLogger().info(this.arenaManager.getAllArenas().size() + " arène(s) chargée(s).");

            getCommand("nx").setExecutor(new NexusAdminCommand(this.arenaManager, this.shopManager, this.sanctionManager, this.kitManager, this.npcManager));
            getCommand("play").setExecutor(new PlayCommand(this.queueManager, this));
            // CORRECTION: L'instance du plugin (this) est maintenant passée au listener
            getServer().getPluginManager().registerEvents(new PlayerConnectionListener(this.playerManager, this.arenaManager, AdminPlacementManager.getInstance(), this), this);
            getServer().getPluginManager().registerEvents(new AdminConversationListener(AdminConversationManager.getInstance(), this), this);
            getServer().getPluginManager().registerEvents(new AdminPlacementListener(AdminPlacementManager.getInstance(), this.arenaManager, this), this);
            getServer().getPluginManager().registerEvents(new GameListener(this.gameManager, this, this.queueManager, this.sanctionManager), this);
            getServer().getPluginManager().registerEvents(new NpcListener(this.npcManager), this);

            getLogger().info("✅ Le plugin Nexus a été activé avec succès !");

        } catch (Exception e) {
            getLogger().severe("❌ Erreur critique lors du démarrage de Nexus :");
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Override
    public void onDisable() {
        if (this.playerManager != null) {
            // CORRECTION: Appel de la méthode qui existe réellement
            this.playerManager.unloadAllProfiles();
        }
        if (this.npcManager != null) {
            this.npcManager.removeAll();
        }
        if (this.dataSourceProvider != null) {
            this.dataSourceProvider.close();
        }
        getLogger().info("Le plugin Nexus a été désactivé.");
    }
}
