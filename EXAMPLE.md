# Exemple d'utilisation de l'API Nexus

Le fragment suivant illustre comment un plugin externe peut dépendre de Nexus et
utiliser les services Bukkit exposés ainsi que les événements personnalisés.

```java
package com.example.nexusaddon;

import com.heneria.nexus.api.ArenaHandle;
import com.heneria.nexus.api.ArenaService;
import com.heneria.nexus.api.QueueService;
import com.heneria.nexus.api.events.NexusArenaEndEvent;
import com.heneria.nexus.api.events.NexusArenaStartEvent;
import com.heneria.nexus.api.events.NexusPlayerKillEvent;
import java.util.Collection;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Team;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public final class MyNexusAddon extends JavaPlugin implements Listener {

    private ArenaService arenaService;
    private QueueService queueService;

    @Override
    public void onEnable() {
        RegisteredServiceProvider<ArenaService> arenaProvider =
                getServer().getServicesManager().getRegistration(ArenaService.class);
        if (arenaProvider == null) {
            getLogger().severe("Nexus n'est pas présent sur le serveur.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        arenaService = arenaProvider.getProvider();

        RegisteredServiceProvider<QueueService> queueProvider =
                getServer().getServicesManager().getRegistration(QueueService.class);
        if (queueProvider != null) {
            queueService = queueProvider.getProvider();
        }

        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Connecté à l'API Nexus !");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("listnexusarenas")) {
            return false;
        }
        Collection<ArenaHandle> arenas = arenaService.instances();
        sender.sendMessage("Arènes actives : " + arenas.size());
        for (ArenaHandle arena : arenas) {
            sender.sendMessage(" - " + arena.id() + " (map: " + arena.mapId() + ", phase: " + arena.phase() + ")");
        }
        if (queueService != null) {
            sender.sendMessage("Joueurs en file : " + queueService.stats().totalPlayers());
        }
        return true;
    }

    @EventHandler
    public void onArenaStart(NexusArenaStartEvent event) {
        getLogger().info("L'arène " + event.getArena().id() + " vient de démarrer.");
    }

    @EventHandler
    public void onArenaEnd(NexusArenaEndEvent event) {
        Team winner = event.getWinner();
        String winnerName = winner == null ? "Aucun" : winner.getName();
        Bukkit.broadcastMessage("Arène terminée : " + event.getArena().id() + " — gagnants : " + winnerName);
    }

    @EventHandler
    public void onNexusKill(NexusPlayerKillEvent event) {
        Player killer = event.getKiller();
        Player victim = event.getVictim();
        if (killer != null) {
            Bukkit.broadcastMessage(killer.getName() + " a éliminé " + victim.getName() + " dans Nexus !");
        }
    }
}
```

Ajoutez simplement la dépendance Maven/Gradle suivante dans votre plugin pour
compiler contre l'API Nexus :

```xml
<dependency>
    <groupId>com.heneria</groupId>
    <artifactId>nexus</artifactId>
    <version>${nexus.version}</version>
    <scope>provided</scope>
</dependency>
```
