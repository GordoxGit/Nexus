package fr.heneria.nexus.game;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Centralise le chargement et la modification des règles de jeu.
 */
public class GameConfig {

    private static GameConfig instance;
    private final JavaPlugin plugin;

    private int roundsToWin;
    private double nexusMaxHealth;
    private int nexusSurchargesToDestroy;
    private double energyCellCaptureRadius;
    private int energyCellCaptureTimeSeconds;
    private int startingRoundPoints;
    private int killReward;
    private int assistReward;
    private int roundWinBonus;
    private int killAttributionTimeSeconds;

    private GameConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    public static void init(JavaPlugin plugin) {
        instance = new GameConfig(plugin);
    }

    public static GameConfig get() {
        return instance;
    }

    private void load() {
        FileConfiguration config = plugin.getConfig();
        roundsToWin = config.getInt("game-rules.rounds-to-win", 3);
        nexusMaxHealth = config.getDouble("game-rules.nexus-max-health", 100.0);
        nexusSurchargesToDestroy = config.getInt("game-rules.nexus-surcharges-to-destroy", 2);
        energyCellCaptureRadius = config.getDouble("game-rules.energy-cell.capture-radius", 5.0);
        energyCellCaptureTimeSeconds = config.getInt("game-rules.energy-cell.capture-time-seconds", 30);
        startingRoundPoints = config.getInt("game-rules.economy.starting-round-points", 100);
        killReward = config.getInt("game-rules.economy.kill-reward", 100);
        assistReward = config.getInt("game-rules.economy.assist-reward", 50);
        roundWinBonus = config.getInt("game-rules.economy.round-win-bonus", 200);
        killAttributionTimeSeconds = config.getInt("game-rules.kill-attribution-time-seconds", 10);
    }

    public int getRoundsToWin() {
        return roundsToWin;
    }

    public void setRoundsToWin(int value) {
        roundsToWin = value;
        save("game-rules.rounds-to-win", value);
    }

    public double getNexusMaxHealth() {
        return nexusMaxHealth;
    }

    public void setNexusMaxHealth(double value) {
        nexusMaxHealth = value;
        save("game-rules.nexus-max-health", value);
    }

    public int getNexusSurchargesToDestroy() {
        return nexusSurchargesToDestroy;
    }

    public void setNexusSurchargesToDestroy(int value) {
        nexusSurchargesToDestroy = value;
        save("game-rules.nexus-surcharges-to-destroy", value);
    }

    public double getEnergyCellCaptureRadius() {
        return energyCellCaptureRadius;
    }

    public void setEnergyCellCaptureRadius(double value) {
        energyCellCaptureRadius = value;
        save("game-rules.energy-cell.capture-radius", value);
    }

    public int getEnergyCellCaptureTimeSeconds() {
        return energyCellCaptureTimeSeconds;
    }

    public void setEnergyCellCaptureTimeSeconds(int value) {
        energyCellCaptureTimeSeconds = value;
        save("game-rules.energy-cell.capture-time-seconds", value);
    }

    public int getStartingRoundPoints() {
        return startingRoundPoints;
    }

    public void setStartingRoundPoints(int value) {
        startingRoundPoints = value;
        save("game-rules.economy.starting-round-points", value);
    }

    public int getKillReward() {
        return killReward;
    }

    public void setKillReward(int value) {
        killReward = value;
        save("game-rules.economy.kill-reward", value);
    }

    public int getAssistReward() {
        return assistReward;
    }

    public void setAssistReward(int value) {
        assistReward = value;
        save("game-rules.economy.assist-reward", value);
    }

    public int getRoundWinBonus() {
        return roundWinBonus;
    }

    public void setRoundWinBonus(int value) {
        roundWinBonus = value;
        save("game-rules.economy.round-win-bonus", value);
    }

    public int getKillAttributionTimeSeconds() {
        return killAttributionTimeSeconds;
    }

    public void setKillAttributionTimeSeconds(int value) {
        killAttributionTimeSeconds = value;
        save("game-rules.kill-attribution-time-seconds", value);
    }

    /**
     * Met à jour une valeur à partir de sa clé relative dans game-rules.
     */
    public void setValue(String key, double value) {
        switch (key) {
            case "rounds-to-win" -> setRoundsToWin((int) value);
            case "nexus-max-health" -> setNexusMaxHealth(value);
            case "nexus-surcharges-to-destroy" -> setNexusSurchargesToDestroy((int) value);
            case "energy-cell.capture-radius" -> setEnergyCellCaptureRadius(value);
            case "energy-cell.capture-time-seconds" -> setEnergyCellCaptureTimeSeconds((int) value);
            case "economy.starting-round-points" -> setStartingRoundPoints((int) value);
            case "economy.kill-reward" -> setKillReward((int) value);
            case "economy.assist-reward" -> setAssistReward((int) value);
            case "economy.round-win-bonus" -> setRoundWinBonus((int) value);
            case "kill-attribution-time-seconds" -> setKillAttributionTimeSeconds((int) value);
            default -> {
            }
        }
    }

    private void save(String path, Object value) {
        plugin.getConfig().set(path, value);
        plugin.saveConfig();
    }
}
