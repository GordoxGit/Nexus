# 🔌 API Documentation - Nexus Plugin

## Vue d'Ensemble

[cite_start]Cette documentation décrit toutes les APIs publiques du plugin Nexus, destinées aux développeurs souhaitant intégrer ou étendre les fonctionnalités du plugin. [cite: 534]

## 📋 Table des Matières

- [cite_start][Core API](#core-api) [cite: 535]
- [cite_start][Arena Management API](#arena-management-api) [cite: 535]
- [cite_start][Game Engine API](#game-engine-api) [cite: 535]
- [cite_start][Player Management API](#player-management-api) [cite: 535]
- [cite_start][Economy API](#economy-api) [cite: 535]
- [cite_start][Ranking System API](#ranking-system-api) [cite: 535]
- [cite_start][Event System API](#event-system-api) [cite: 535]
- [cite_start][Integration APIs](#integration-apis) [cite: 535]

## 🏗️ Core API

### NexusAPI - Point d'Entrée Principal

```java
public interface NexusAPI {
    
    /**
     * Obtient l'instance du gestionnaire d'arènes.
     * @return ArenaManager instance
     */
    [cite_start]ArenaManager getArenaManager(); [cite: 536]
    /**
     * Obtient l'instance du moteur de jeu.
     * @return GameEngine instance
     */
    [cite_start]GameEngine getGameEngine(); [cite: 538]
    /**
     * Obtient l'instance du gestionnaire de joueurs.
     * @return PlayerManager instance
     */
    [cite_start]PlayerManager getPlayerManager(); [cite: 540]
    /**
     * Obtient l'instance du gestionnaire économique.
     * @return EconomyManager instance
     */
    [cite_start]EconomyManager getEconomyManager(); [cite: 542]
    /**
     * Obtient l'instance du système de classement.
     * @return RankingManager instance
     */
    [cite_start]RankingManager getRankingManager(); [cite: 544]
    /**
     * Vérifie si le plugin est complètement initialisé.
     * @return true si initialisé, false sinon
     */
    [cite_start]boolean isInitialized(); [cite: 546]
    /**
     * Obtient la version du plugin.
     * @return Version string (ex: "1.0.0")
     */
    [cite_start]String getVersion(); [cite: 548]
}
```

### Accès à l'API

```java
// Dans votre plugin
public class YourPlugin extends JavaPlugin {
    
    private NexusAPI nexusAPI;
    @Override
    [cite_start]public void onEnable() { [cite: 550]
        // Attendre que Nexus soit chargé
        if (getServer().getPluginManager().getPlugin("Nexus") == null) {
            getLogger().severe("Nexus plugin not found!");
            [cite_start]getServer().getPluginManager().disablePlugin(this); [cite: 551]
            return;
        }
        
        // Obtenir l'API
        [cite_start]this.nexusAPI = NexusAPI.getInstance(); [cite: 552]
        [cite_start]if (!nexusAPI.isInitialized()) { [cite: 552]
            [cite_start]getLogger().warning("Nexus API not yet initialized"); [cite: 553]
        }
    }
}
```

## 🏟️ Arena Management API

### ArenaManager Interface

```java
public interface ArenaManager {
    
    /**
     * Crée une nouvelle arène.
     * @param name nom unique de l'arène
     * @param config configuration de l'arène
     * @return Arena créée
     * @throws ArenaAlreadyExistsException si le nom existe déjà
     * @throws IllegalArgumentException si la configuration est invalide
     */
    [cite_start]Arena createArena(String name, ArenaConfig config) throws ArenaAlreadyExistsException; [cite: 554]
    /**
     * Obtient une arène par son nom.
     * @param name nom de l'arène
     * @return Optional contenant l'arène si trouvée
     */
    [cite_start]Optional<Arena> getArena(String name); [cite: 556]
    /**
     * Obtient toutes les arènes d'un mode de jeu.
     * @param gameMode mode de jeu filtré
     * @return Collection d'arènes
     */
    [cite_start]Collection<Arena> getArenasByGameMode(GameMode gameMode); [cite: 558]
    /**
     * Obtient toutes les arènes disponibles.
     * @return Collection de toutes les arènes
     */
    [cite_start]Collection<Arena> getAllArenas(); [cite: 560]
    /**
     * Supprime une arène.
     * @param name nom de l'arène à supprimer
     * @return true si supprimée, false si introuvable
     */
    [cite_start]boolean deleteArena(String name); [cite: 561]
    /**
     * Recharge toutes les arènes depuis la base de données.
     */
    [cite_start]void reloadArenas(); [cite: 563]
    
    /**
     * Valide la configuration d'une arène.
     * @param config configuration à valider
     * @return ValidationResult avec détails des erreurs
     */
    [cite_start]ValidationResult validateArenaConfig(ArenaConfig config); [cite: 564]
}
```

### Arena Model

```java
public class Arena {
    
    /**
     * Obtient l'ID unique de l'arène.
     * @return ID de l'arène
     */
    [cite_start]public int getId(); [cite: 566]
    /**
     * Obtient le nom de l'arène.
     * @return nom de l'arène
     */
    [cite_start]public String getName(); [cite: 568]
    /**
     * Obtient le mode de jeu de l'arène.
     * @return GameMode enum
     */
    [cite_start]public GameMode getGameMode(); [cite: 570]
    /**
     * Obtient le nombre maximum de joueurs.
     * @return nombre max de joueurs
     */
    [cite_start]public int getMaxPlayers(); [cite: 572]
    /**
     * Obtient le nombre minimum de joueurs.
     * @return nombre min de joueurs
     */
    [cite_start]public int getMinPlayers(); [cite: 574]
    /**
     * Obtient les spawns d'une équipe.
     * @param teamId ID de l'équipe
     * @return Map des spawns (numéro → Location)
     */
    [cite_start]public Map<Integer, Location> getTeamSpawns(int teamId); [cite: 576]
    /**
     * Définit un spawn pour une équipe.
     * @param teamId ID de l'équipe
     * @param spawnNumber numéro du spawn
     * @param location position du spawn
     */
    [cite_start]public void setSpawn(int teamId, int spawnNumber, Location location); [cite: 578]
    /**
     * Obtient l'état actuel de l'arène.
     * @return ArenaState enum
     */
    [cite_start]public ArenaState getState(); [cite: 580]
    /**
     * Vérifie si l'arène est disponible pour un match.
     * @return true si disponible
     */
    [cite_start]public boolean isAvailable(); [cite: 582]
    /**
     * Obtient la région WorldGuard associée (optionnel).
     * @return Optional de ProtectedRegion
     */
    [cite_start]public Optional<ProtectedRegion> getRegion(); [cite: 584]
}
```

### ArenaConfig

```java
public record ArenaConfig(
    GameMode gameMode,
    int maxPlayers,
    int minPlayers,
    String worldName,
    Duration matchTimeout,
    Map<String, Object> customProperties
) {
    
    /**
     * Crée une configuration d'arène avec des valeurs par défaut.
     * @param gameMode mode de jeu
     * @param maxPlayers nombre max de joueurs
     * @return ArenaConfig avec défauts
     */
    [cite_start]public static ArenaConfig withDefaults(GameMode gameMode, int maxPlayers) { [cite: 586]
        return new ArenaConfig(
            gameMode,
            maxPlayers,
            gameMode.getMinPlayers(),
            "world",
 
            [cite_start]Duration.ofMinutes(20), [cite: 587]
            Map.of()
        );
    }
}
```

## 🎮 Game Engine API

### GameEngine Interface

```java
public interface GameEngine {
    
    /**
     * Crée un nouveau match.
     * @param arena arène pour le match
     * @param teams équipes participantes
     * @return Match créé
     * @throws ArenaNotAvailableException si l'arène n'est pas disponible
     */
    [cite_start]Match createMatch(Arena arena, List<Team> teams, MatchType type) throws ArenaNotAvailableException; [cite: 589]
    /**
     * Démarre un match.
     * @param match match à démarrer
     * @throws MatchAlreadyStartedException si déjà démarré
     */
    [cite_start]void startMatch(Match match) throws MatchAlreadyStartedException; [cite: 590]
    /**
     * Termine un match avec un résultat.
     * @param match match à terminer
     * @param result résultat du match
     */
    [cite_start]void endMatch(Match match, MatchResult result); [cite: 592]
    /**
     * Met en pause un match.
     * @param match match à pauser
     */
    [cite_start]void pauseMatch(Match match); [cite: 594]
    /**
     * Reprend un match en pause.
     * @param match match à reprendre
     */
    [cite_start]void resumeMatch(Match match); [cite: 596]
    /**
     * Obtient tous les matches actifs.
     * @return Collection des matches en cours
     */
    [cite_start]Collection<Match> getActiveMatches(); [cite: 598]
    /**
     * Obtient un match par son ID.
     * @param matchId ID du match
     * @return Optional du match
     */
    [cite_start]Optional<Match> getMatch(String matchId); [cite: 600]
    /**
     * Trouve le match d'un joueur.
     * @param playerId UUID du joueur
     * @return Optional du match actuel
     */
    [cite_start]Optional<Match> findPlayerMatch(UUID playerId); [cite: 602]
}
```

### Match Model

```java
public class Match {
    
    /**
     * Obtient l'ID unique du match.
     * @return ID du match
     */
    [cite_start]public String getMatchId(); [cite: 604]
    /**
     * Obtient l'arène du match.
     * @return Arena instance
     */
    [cite_start]public Arena getArena(); [cite: 606]
    /**
     * Obtient toutes les équipes du match.
     * @return List des équipes
     */
    [cite_start]public List<Team> getTeams(); [cite: 608]
    /**
     * Obtient l'état actuel du match.
     * @return MatchState enum
     */
    [cite_start]public MatchState getState(); [cite: 610]
    /**
     * Obtient la phase actuelle du jeu.
     * @return GamePhase enum
     */
    [cite_start]public GamePhase getCurrentPhase(); [cite: 612]
    /**
     * Obtient les statistiques du match.
     * @return MatchStatistics instance
     */
    [cite_start]public MatchStatistics getStatistics(); [cite: 614]
    /**
     * Obtient la configuration du match.
     * @return MatchConfig instance
     */
    [cite_start]public MatchConfig getConfig(); [cite: 616]
    /**
     * Obtient le temps de début du match.
     * @return Instant de début
     */
    [cite_start]public Instant getStartTime(); [cite: 618]
    /**
     * Obtient la durée écoulée du match.
     * @return Duration écoulée
     */
    [cite_start]public Duration getElapsedTime(); [cite: 620]
    /**
     * Ajoute un joueur au match.
     * @param player joueur à ajouter
     * @param team équipe de destination
     * @return true si ajouté avec succès
     */
    [cite_start]public boolean addPlayer(Player player, Team team); [cite: 622]
    /**
     * Retire un joueur du match.
     * @param playerId UUID du joueur
     * @return true si retiré avec succès
     */
    [cite_start]public boolean removePlayer(UUID playerId); [cite: 624]
    /**
     * Vérifie si un joueur participe au match.
     * @param playerId UUID du joueur
     * @return true si participant
     */
    [cite_start]public boolean isPlayerParticipating(UUID playerId); [cite: 626]
}
```

### Team Model

```java
public class Team {
    
    /**
     * Obtient l'ID de l'équipe.
     * @return ID numérique de l'équipe
     */
    [cite_start]public int getTeamId(); [cite: 628]
    /**
     * Obtient le nom de l'équipe.
     * @return nom de l'équipe
     */
    [cite_start]public String getName(); [cite: 630]
    /**
     * Obtient la couleur de l'équipe.
     * @return ChatColor de l'équipe
     */
    [cite_start]public ChatColor getColor(); [cite: 632]
    /**
     * Obtient tous les joueurs de l'équipe.
     * @return Set des UUIDs des joueurs
     */
    [cite_start]public Set<UUID> getPlayers(); [cite: 634]
    /**
     * Obtient le nombre de joueurs dans l'équipe.
     * @return nombre de joueurs
     */
    [cite_start]public int getPlayerCount(); [cite: 636]
    /**
     * Ajoute un joueur à l'équipe.
     * @param playerId UUID du joueur
     * @return true si ajouté
     */
    [cite_start]public boolean addPlayer(UUID playerId); [cite: 638]
    /**
     * Retire un joueur de l'équipe.
     * @param playerId UUID du joueur
     * @return true si retiré
     */
    [cite_start]public boolean removePlayer(UUID playerId); [cite: 640]
    /**
     * Vérifie si un joueur fait partie de l'équipe.
     * @param playerId UUID du joueur
     * @return true si membre
     */
    [cite_start]public boolean hasPlayer(UUID playerId); [cite: 642]
    /**
     * Obtient les statistiques d'équipe pour ce match.
     * @return TeamStatistics instance
     */
    [cite_start]public TeamStatistics getStatistics(); [cite: 644]
}
```

## 👤 Player Management API

### PlayerManager Interface

```java
public interface PlayerManager {
    
    /**
     * Obtient le profil d'un joueur.
     * @param playerId UUID du joueur
     * @return PlayerProfile instance
     */
    [cite_start]PlayerProfile getProfile(UUID playerId); [cite: 646]
    /**
     * Crée un nouveau profil joueur.
     * @param playerId UUID du joueur
     * @param playerName nom du joueur
     * @return PlayerProfile créé
     */
    [cite_start]PlayerProfile createProfile(UUID playerId, String playerName); [cite: 648]
    /**
     * Met à jour un profil joueur.
     * @param profile profil à mettre à jour
     */
    [cite_start]void updateProfile(PlayerProfile profile); [cite: 650]
    /**
     * Obtient les statistiques d'un joueur.
     * @param playerId UUID du joueur
     * @return PlayerStatistics instance
     */
    [cite_start]PlayerStatistics getStatistics(UUID playerId); [cite: 500]
    /**
     * Obtient les statistiques par mode de jeu.
     * @param playerId UUID du joueur
     * @param gameMode mode de jeu
     * @return PlayerStatistics pour le mode
     */
    [cite_start]PlayerStatistics getStatistics(UUID playerId, GameMode gameMode); [cite: 654]
    /**
     * Met à jour les statistiques après un match.
     * @param playerId UUID du joueur
     * @param matchResult résultat du match
     */
    [cite_start]void updateStatistics(UUID playerId, MatchResult matchResult); [cite: 656]
    /**
     * Vérifie si un joueur est en ligne.
     * @param playerId UUID du joueur
     * @return true si en ligne
     */
    [cite_start]boolean isOnline(UUID playerId); [cite: 658]
    /**
     * Obtient le joueur Bukkit associé.
     * @param playerId UUID du joueur
     * @return Optional du Player Bukkit
     */
    [cite_start]Optional<Player> getBukkitPlayer(UUID playerId); [cite: 660]
    /**
     * Sauvegarde tous les profils en cache.
     */
    [cite_start]void saveAllProfiles(); [cite: 662]
}
```

### PlayerProfile Model

```java
public class PlayerProfile {
    
    /**
     * Obtient l'UUID du joueur.
     * @return UUID du joueur
     */
    [cite_start]public UUID getPlayerId(); [cite: 663]
    /**
     * Obtient le nom d'affichage du joueur.
     * @return nom d'affichage
     */
    [cite_start]public String getDisplayName(); [cite: 665]
    /**
     * Définit le nom d'affichage.
     * @param displayName nouveau nom d'affichage
     */
    [cite_start]public void setDisplayName(String displayName); [cite: 667]
    /**
     * Obtient le rang actuel du joueur.
     * @return PlayerRank enum
     */
    [cite_start]public PlayerRank getRank(); [cite: 669]
    /**
     * Définit le rang du joueur.
     * @param rank nouveau rang
     */
    [cite_start]public void setRank(PlayerRank rank); [cite: 671]
    /**
     * Obtient le rating Elo actuel.
     * @return rating Elo
     */
    [cite_start]public int getEloRating(); [cite: 673]
    /**
     * Définit le rating Elo.
     * @param eloRating nouveau rating
     */
    [cite_start]public void setEloRating(int eloRating); [cite: 675]
    /**
     * Obtient les points économiques actuels.
     * @return nombre de points
     */
    [cite_start]public int getCurrentPoints(); [cite: 677]
    /**
     * Obtient les paramètres du joueur.
     * @return PlayerSettings instance
     */
    [cite_start]public PlayerSettings getSettings(); [cite: 679]
    /**
     * Obtient la date de dernière connexion.
     * @return Instant de dernière connexion
     */
    [cite_start]public Instant getLastSeen(); [cite: 681]
    /**
     * Met à jour la dernière connexion à maintenant.
     */
    [cite_start]public void updateLastSeen(); [cite: 683]
    
    /**
     * Vérifie si le profil a été modifié.
     * @return true si modifié
     */
    [cite_start]public boolean isDirty(); [cite: 684]
    /**
     * Marque le profil comme sauvegardé.
     */
    [cite_start]public void markClean(); [cite: 686]
}
```

## 💰 Economy API

### EconomyManager Interface

```java
public interface EconomyManager {
    
    /**
     * Obtient le nombre de points d'un joueur.
     * @param playerId UUID du joueur
     * @return nombre de points
     */
    [cite_start]int getPoints(UUID playerId); [cite: 687]
    /**
     * Vérifie si un joueur a assez de points.
     * @param playerId UUID du joueur
     * @param amount montant requis
     * @return true si assez de points
     */
    [cite_start]boolean hasEnoughPoints(UUID playerId, int amount); [cite: 689]
    /**
     * Ajoute des points à un joueur.
     * @param playerId UUID du joueur
     * @param amount montant à ajouter
     * @param reason raison de l'ajout
     * @return TransactionResult
     */
    [cite_start]TransactionResult addPoints(UUID playerId, int amount, String reason); [cite: 691]
    /**
     * Retire des points à un joueur.
     * @param playerId UUID du joueur
     * @param amount montant à retirer
     * @param reason raison du retrait
     * @return TransactionResult
     */
    [cite_start]TransactionResult removePoints(UUID playerId, int amount, String reason); [cite: 693]
    /**
     * Transfère des points entre joueurs.
     * @param fromPlayer UUID du donneur
     * @param toPlayer UUID du receveur
     * @param amount montant à transférer
     * @param reason raison du transfert
     * @return TransactionResult
     */
    [cite_start]TransactionResult transferPoints(UUID fromPlayer, UUID toPlayer, int amount, String reason); [cite: 695]
    /**
     * Obtient l'historique des transactions.
     * @param playerId UUID du joueur
     * @param limit nombre max de transactions
     * @return List des transactions récentes
     */
    [cite_start]List<Transaction> getTransactionHistory(UUID playerId, int limit); [cite: 697]
    /**
     * Calcule les points gagnés pour un résultat de match.
     * @param playerId UUID du joueur
     * @param matchResult résultat du match
     * @return nombre de points à ajouter
     */
    [cite_start]int calculateMatchReward(UUID playerId, MatchResult matchResult); [cite: 699]
}
```

### TransactionResult

```java
public class TransactionResult {
    
    /**
     * Vérifie si la transaction a réussi.
     * @return true si succès
     */
    [cite_start]public boolean isSuccess(); [cite: 701]
    /**
     * Obtient le message de résultat.
     * @return message explicatif
     */
    [cite_start]public String getMessage(); [cite: 703]
    /**
     * Obtient le nouveau solde après transaction.
     * @return nouveau solde
     */
    [cite_start]public int getNewBalance(); [cite: 705]
    /**
     * Obtient l'ID de la transaction.
     * @return ID unique de la transaction
     */
    [cite_start]public long getTransactionId(); [cite: 707]
    /**
     * Obtient le timestamp de la transaction.
     * @return Instant de la transaction
     */
    [cite_start]public Instant getTimestamp(); [cite: 709]
    /**
     * Crée un résultat de succès.
     * @param newBalance nouveau solde
     * @param transactionId ID de transaction
     * @return TransactionResult de succès
     */
    [cite_start]public static TransactionResult success(int newBalance, long transactionId); [cite: 711]
    /**
     * Crée un résultat d'échec.
     * @param reason raison de l'échec
     * @return TransactionResult d'échec
     */
    [cite_start]public static TransactionResult failure(String reason); [cite: 713]
}
```

## 🏆 Ranking System API

### RankingManager Interface

```java
public interface RankingManager {
    
    /**
     * Calcule le nouveau rating Elo après un match.
     * @param currentElo rating actuel
     * @param opponentElo rating de l'adversaire
     * @param matchResult résultat du match
     * @return nouveau rating Elo
     */
    [cite_start]int calculateNewElo(int currentElo, int opponentElo, MatchResult matchResult); [cite: 715]
    /**
     * Obtient le rang basé sur le rating Elo.
     * @param eloRating rating Elo
     * @return PlayerRank correspondant
     */
    [cite_start]PlayerRank getRankFromElo(int eloRating); [cite: 717]
    /**
     * Obtient le classement global.
     * @param limit nombre de joueurs à retourner
     * @return List des profils classés
     */
    [cite_start]List<PlayerProfile> getGlobalLeaderboard(int limit); [cite: 719]
    /**
     * Obtient le classement par mode de jeu.
     * @param gameMode mode de jeu
     * @param limit nombre de joueurs à retourner
     * @return List des profils classés
     */
    [cite_start]List<PlayerProfile> getLeaderboard(GameMode gameMode, int limit); [cite: 721]
    /**
     * Obtient la position d'un joueur dans le classement.
     * @param playerId UUID du joueur
     * @return position (1-indexed), -1 si non classé
     */
    [cite_start]int getPlayerRank(UUID playerId); [cite: 723]
    /**
     * Obtient la position d'un joueur par mode.
     * @param playerId UUID du joueur
     * @param gameMode mode de jeu
     * @return position (1-indexed), -1 si non classé
     */
    [cite_start]int getPlayerRank(UUID playerId, GameMode gameMode); [cite: 725]
    /**
     * Obtient la saison actuelle.
     * @return Season instance, null si aucune saison active
     */
    [cite_start]Season getCurrentSeason(); [cite: 727]
    /**
     * Démarre une nouvelle saison.
     * @param seasonConfig configuration de la saison
     * @return Season créée
     */
    [cite_start]Season startNewSeason(SeasonConfig seasonConfig); [cite: 729]
    /**
     * Termine la saison actuelle.
     * @param distributeRewards true pour distribuer les récompenses
     */
    [cite_start]void endCurrentSeason(boolean distributeRewards); [cite: 731]
}
```

## 📡 Event System API

### Événements Nexus

```java
// Événement de base
public abstract class NexusEvent extends Event {
    
    private static final HandlerList handlers = new HandlerList();
    @Override
    [cite_start]public HandlerList getHandlers() { [cite: 733]
        return handlers;
    }
    
    [cite_start]public static HandlerList getHandlerList() { [cite: 734]
        return handlers;
    }
}

// Événements d'arène
public class ArenaCreatedEvent extends NexusEvent {
    private final Arena arena;
    // constructeurs et getters
}

public class ArenaDeletedEvent extends NexusEvent {
    private final String arenaName;
    // constructeurs et getters
}

// Événements de match
public class MatchStartEvent extends NexusEvent {
    private final Match match;
    // constructeurs et getters
}

public class MatchEndEvent extends NexusEvent {
    private final Match match;
    private final MatchResult result;
    // constructeurs et getters
}

public class GamePhaseChangeEvent extends NexusEvent {
    private final Match match;
    private final GamePhase previousPhase;
    [cite_start]private final GamePhase newPhase; [cite: 740]
    // constructeurs et getters
}

// Événements de joueur
public class PlayerJoinMatchEvent extends NexusEvent implements Cancellable {
    [cite_start]private final Player player; [cite: 741]
    [cite_start]private final Match match; [cite: 741]
    [cite_start]private final Team team; [cite: 741]
    private boolean cancelled = false;
    // constructeurs, getters et méthodes Cancellable
}

public class PlayerLeaveMatchEvent extends NexusEvent {
    [cite_start]private final UUID playerId; [cite: 743]
    [cite_start]private final Match match; [cite: 743]
    [cite_start]private final LeaveReason reason; [cite: 743]
    // constructeurs et getters
}

public class PlayerEliminatedEvent extends NexusEvent {
    [cite_start]private final UUID playerId; [cite: 744]
    [cite_start]private final UUID killerId; [cite: 744]
    [cite_start]private final Match match; [cite: 744]
    // constructeurs et getters
}

// Événements de gameplay
public class CellCapturedEvent extends NexusEvent {
    [cite_start]private final Match match; [cite: 745]
    [cite_start]private final Team capturingTeam; [cite: 745]
    [cite_start]private final Location cellLocation; [cite: 745]
    // constructeurs et getters
}

public class CellDeliveredEvent extends NexusEvent {
    [cite_start]private final Match match; [cite: 746]
    [cite_start]private final UUID carrierId; [cite: 746]
    [cite_start]private final Team deliveryTeam; [cite: 746]
    [cite_start]private final Location nexusLocation; [cite: 746]
    // constructeurs et getters
}

public class NexusDestroyedEvent extends NexusEvent {
    [cite_start]private final Match match; [cite: 748]
    [cite_start]private final Team destroyedTeam; [cite: 748]
    [cite_start]private final UUID destroyerId; [cite: 748]
    // constructeurs et getters
}

// Événements économiques
public class PointsEarnedEvent extends NexusEvent {
    [cite_start]private final UUID playerId; [cite: 749]
    [cite_start]private final int pointsEarned; [cite: 749]
    [cite_start]private final String reason; [cite: 749]
    // constructeurs et getters
}

public class PlayerRankUpEvent extends NexusEvent {
    [cite_start]private final UUID playerId; [cite: 750]
    [cite_start]private final PlayerRank previousRank; [cite: 750]
    [cite_start]private final PlayerRank newRank; [cite: 750]
    // constructeurs et getters
}
```

### Exemple d'Utilisation des Événements

```java
@EventHandler
public void onMatchStart(MatchStartEvent event) {
    [cite_start]Match match = event.getMatch(); [cite: 751]
    [cite_start]getLogger().info("Match started: " + match.getMatchId()); [cite: 751]
    
    // Votre logique ici
    for (Team team : match.getTeams()) {
        for (UUID playerId : team.getPlayers()) {
            Player player = Bukkit.getPlayer(playerId);
            [cite_start]if (player != null) { [cite: 752]
                [cite_start]player.sendMessage("Match started! Good luck!"); [cite: 753]
            }
        }
    }
}

@EventHandler
public void onPlayerEliminated(PlayerEliminatedEvent event) {
    UUID victim = event.getPlayerId();
    [cite_start]UUID killer = event.getKillerId(); [cite: 754]
    
    if (killer != null) {
        // Logique pour les statistiques de kill
        PlayerStatistics stats = nexusAPI.getPlayerManager().getStatistics(killer);
        [cite_start]stats.incrementKills(); [cite: 755]
    }
}

@EventHandler(priority = EventPriority.HIGH)
public void onPlayerJoinMatch(PlayerJoinMatchEvent event) {
    [cite_start]Player player = event.getPlayer(); [cite: 756]
    // Vérifications personnalisées
    if (isPlayerBanned(player)) {
        [cite_start]event.setCancelled(true); [cite: 551, 757]
        [cite_start]player.sendMessage("You are banned from matches!"); [cite: 757]
    }
}
```

## 🔗 Integration APIs

### PlaceholderAPI Integration

```java
public class NexusPlaceholderExpansion extends PlaceholderExpansion {
    
    @Override
    public String getIdentifier() {
        [cite_start]return "nexus"; [cite: 758]
    }
    
    @Override
    public String onPlaceholderRequest(Player player, String params) {
        [cite_start]if (player == null) return ""; [cite: 759]
        [cite_start]NexusAPI api = NexusAPI.getInstance(); [cite: 552]
        PlayerProfile profile = api.getPlayerManager().getProfile(player.getUniqueId());
        
        return switch (params) {
            [cite_start]case "rank" -> profile.getRank().getDisplayName(); [cite: 760]
            [cite_start]case "elo" -> String.valueOf(profile.getEloRating()); [cite: 760]
            [cite_start]case "points" -> String.valueOf(profile.getCurrentPoints()); [cite: 760]
            [cite_start]case "wins" -> String.valueOf(api.getPlayerManager().getStatistics(player.getUniqueId()).getWins()); [cite: 760]
            [cite_start]case "losses" -> String.valueOf(api.getPlayerManager().getStatistics(player.getUniqueId()).getLosses()); [cite: 760]
            [cite_start]case "kills" -> String.valueOf(api.getPlayerManager().getStatistics(player.getUniqueId()).getKills()); [cite: 760]
            [cite_start]case "deaths" -> String.valueOf(api.getPlayerManager().getStatistics(player.getUniqueId()).getDeaths()); [cite: 761]
            [cite_start]case "kdr" -> String.format("%.2f", api.getPlayerManager().getStatistics(player.getUniqueId()).getKDR()); [cite: 761]
            [cite_start]case "match_status" -> getMatchStatus(player); [cite: 762]
            [cite_start]case "team_name" -> getTeamName(player); [cite: 762]
            [cite_start]case "arena_name" -> getArenaName(player); [cite: 762]
            default -> "";
        };
    }
    
    private String getMatchStatus(Player player) {
        [cite_start]Optional<Match> match = NexusAPI.getInstance().getGameEngine().findPlayerMatch(player.getUniqueId()); [cite: 602, 763]
        [cite_start]return match.map(m -> m.getState().getDisplayName()).orElse("Not in match"); [cite: 763]
    }
}
```

### Méthodes d'Extension Personnalisées

```java
public interface NexusExtension {
    
    /**
     * Appelé lors de l'initialisation de l'extension.
     */
    [cite_start]void onEnable(); [cite: 764]
    
    /**
     * Appelé lors de la désactivation de l'extension.
     */
    [cite_start]void onDisable(); [cite: 765]
    
    /**
     * Obtient le nom de l'extension.
     * @return nom de l'extension
     */
    [cite_start]String getName(); [cite: 766]
    /**
     * Obtient la version de l'extension.
     * @return version de l'extension
     */
    [cite_start]String getVersion(); [cite: 768]
    /**
     * Obtient les dépendances requises.
     * @return List des noms de plugins requis
     */
    [cite_start]List<String> getDependencies(); [cite: 770]
}

// Exemple d'extension
public class MyNexusExtension implements NexusExtension {
    
    @Override
    public void onEnable() {
        // Enregistrement d'événements personnalisés
        [cite_start]Bukkit.getPluginManager().registerEvents(new MyListener(), myPlugin); [cite: 772]
        // Ajout de commandes personnalisées
        [cite_start]NexusAPI.getInstance().getCommandManager().registerCommand(new MyCommand()); [cite: 552, 773]
    }
    
    @Override
    public void onDisable() {
        // Nettoyage des ressources
    }
}
```

## 🛠️ Utilities et Helpers

### Configuration Helper

```java
public class ConfigHelper {
    
    /**
     * Charge une configuration d'arène depuis un fichier YAML.
     * @param file fichier de configuration
     * @return ArenaConfig chargée
     * @throws IOException si erreur de lecture
     */
    [cite_start]public static ArenaConfig loadArenaConfig(File file) throws IOException; [cite: 774]
    /**
     * Sauvegarde une configuration d'arène dans un fichier.
     * @param config configuration à sauvegarder
     * @param file fichier de destination
     * @throws IOException si erreur d'écriture
     */
    [cite_start]public static void saveArenaConfig(ArenaConfig config, File file) throws IOException; [cite: 776]
    /**
     * Valide une configuration de match.
     * @param config configuration à valider
     * @return ValidationResult avec erreurs éventuelles
     */
    [cite_start]public static ValidationResult validateMatchConfig(MatchConfig config); [cite: 778]
}
```

### Location Helper

```java
public class LocationHelper {
    
    /**
     * Convertit une Location en string sérialisée.
     * @param location Location à sérialiser
     * @return String sérialisée
     */
    [cite_start]public static String serialize(Location location); [cite: 780]
    /**
     * Désérialise une string en Location.
     * @param serialized string sérialisée
     * @return Location désérialisée
     * @throws IllegalArgumentException si format invalide
     */
    [cite_start]public static Location deserialize(String serialized); [cite: 843]
    /**
     * Vérifie si une location est dans une région.
     * @param location location à vérifier
     * @param region région de référence
     * @return true si à l'intérieur
     */
    [cite_start]public static boolean isInside(Location location, ProtectedRegion region); [cite: 784]
    /**
     * Calcule la distance 2D entre deux locations.
     * @param loc1 première location
     * @param loc2 seconde location
     * @return distance horizontale
     */
    [cite_start]public static double distance2D(Location loc1, Location loc2); [cite: 786]
}
```

Cette API complète permet une intégration facile et puissante avec le plugin Nexus, offrant tous les outils nécessaires pour créer des extensions et intégrations personnalisées.
