package org.Mona.monaDuels.stats;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;
import org.Mona.monaDuels.MonaDuels;
import org.Mona.monaDuels.config.ConfigManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public final class StatsManager {
   private final MonaDuels plugin;
   private final ConfigManager config;
   private File statsFile;
   private FileConfiguration stats;

   public StatsManager(MonaDuels plugin, ConfigManager config) {
      this.plugin = plugin;
      this.config = config;
      this.reload();
   }

   public void reload() {
      this.statsFile = new File(this.plugin.getDataFolder(), "stats.yml");
      if (!this.statsFile.exists()) {
         try {
            this.statsFile.createNewFile();
         } catch (IOException var2) {
            this.plugin.getLogger().log(Level.SEVERE, "Could not create stats.yml", (Throwable)var2);
         }
      }

      this.stats = YamlConfiguration.loadConfiguration(this.statsFile);
      if (!this.stats.isConfigurationSection("players")) {
         this.stats.createSection("players");
      }
   }

   public void save() {
      try {
         this.stats.save(this.statsFile);
      } catch (IOException var2) {
         this.plugin.getLogger().log(Level.SEVERE, "Failed to save stats.yml", (Throwable)var2);
      }
   }

   public StatsManager.EloResult recordDuel(Player winner, Player loser, String kitName, long durationMs) {
      UUID winnerId = winner.getUniqueId();
      UUID loserId = loser.getUniqueId();
      String kit = kitName.toLowerCase(Locale.ROOT);
      this.addTime(winnerId, durationMs);
      this.addTime(loserId, durationMs);
      this.increment(winnerId, "wins", 1);
      this.increment(loserId, "losses", 1);
      this.incrementKit(winnerId, kit, "wins", 1);
      this.incrementKit(loserId, kit, "losses", 1);
      int winnerElo = this.getKitElo(winnerId, kit);
      int loserElo = this.getKitElo(loserId, kit);
      int winnerChange = this.calculateEloChange(winnerElo, loserElo, true);
      int loserChange = this.calculateEloChange(loserElo, winnerElo, false);
      this.setKitElo(winnerId, kit, winnerElo + winnerChange);
      this.setKitElo(loserId, kit, Math.max(0, loserElo + loserChange));
      this.save();
      return new StatsManager.EloResult(winnerChange, loserChange, winnerElo + winnerChange, Math.max(0, loserElo + loserChange));
   }

   public int getKitElo(UUID playerId, String kitName) {
      String path = kitPath(playerId, kitName) + ".elo";
      return this.stats.getInt(path, this.config.defaultElo());
   }

   public long getTotalTimeMs(UUID playerId) {
      return this.stats.getLong(playerPath(playerId) + ".total-time-ms", 0L);
   }

   public int getWins(UUID playerId) {
      return this.stats.getInt(playerPath(playerId) + ".wins", 0);
   }

   public int getLosses(UUID playerId) {
      return this.stats.getInt(playerPath(playerId) + ".losses", 0);
   }

   private int calculateEloChange(int playerElo, int opponentElo, boolean won) {
      double expected = 1.0 / (1.0 + Math.pow(10.0, (double)(opponentElo - playerElo) / 400.0));
      double score = won ? 1.0 : 0.0;
      return (int)Math.round((double)this.config.eloKFactor() * (score - expected));
   }

   private void addTime(UUID playerId, long ms) {
      String path = playerPath(playerId) + ".total-time-ms";
      this.stats.set(path, this.stats.getLong(path, 0L) + ms);
   }

   private void increment(UUID playerId, String field, int amount) {
      String path = playerPath(playerId) + "." + field;
      this.stats.set(path, this.stats.getInt(path, 0) + amount);
   }

   private void incrementKit(UUID playerId, String kit, String field, int amount) {
      String path = kitPath(playerId, kit) + "." + field;
      this.stats.set(path, this.stats.getInt(path, 0) + amount);
   }

   private void setKitElo(UUID playerId, String kit, int elo) {
      this.stats.set(kitPath(playerId, kit) + ".elo", elo);
   }

   private static String playerPath(UUID playerId) {
      return "players." + playerId;
   }

   private static String kitPath(UUID playerId, String kit) {
      return playerPath(playerId) + ".kits." + kit;
   }

   public static String formatTotalTime(long ms) {
      long hours = ms / 3600000L;
      long minutes = ms % 3600000L / 60000L;
      if (hours > 0L) {
         return hours + "ч " + minutes + "м";
      } else {
         long seconds = ms % 60000L / 1000L;
         return minutes > 0L ? minutes + "м " + seconds + "с" : seconds + "с";
      }
   }

   public static record EloResult(int winnerChange, int loserChange, int winnerElo, int loserElo) {
   }
}
