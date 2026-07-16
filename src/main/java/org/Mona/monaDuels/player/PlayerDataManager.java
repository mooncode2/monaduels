package org.Mona.monaDuels.player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;
import org.Mona.monaDuels.MonaDuels;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

public final class PlayerDataManager {
   private final MonaDuels plugin;
   private File dataFile;
   private FileConfiguration data;

   public PlayerDataManager(MonaDuels plugin) {
      this.plugin = plugin;
   }

   public void load() {
      this.dataFile = new File(this.plugin.getDataFolder(), "player-data.yml");
      if (!this.dataFile.exists()) {
         try {
            this.dataFile.createNewFile();
         } catch (IOException var2) {
            this.plugin.getLogger().log(Level.SEVERE, "Could not create player-data.yml", (Throwable)var2);
         }
      }

      this.data = YamlConfiguration.loadConfiguration(this.dataFile);
      if (!this.data.isConfigurationSection("players")) {
         this.data.createSection("players");
      }
   }

   public void save() {
      if (this.data != null && this.dataFile != null) {
         try {
            this.data.save(this.dataFile);
         } catch (IOException var2) {
            this.plugin.getLogger().log(Level.SEVERE, "Failed to save player-data.yml", (Throwable)var2);
         }
      }
   }

   public void setLastKit(UUID playerId, String kitName) {
      if (kitName != null && !kitName.isBlank()) {
         this.data.set(path(playerId) + ".last-kit", kitName.toLowerCase(Locale.ROOT));
         this.save();
      }
   }

   public String getLastKit(UUID playerId) {
      return this.data.getString(path(playerId) + ".last-kit", "");
   }

   public void setLastGameMode(UUID playerId, String gameMode) {
      if (gameMode != null && !gameMode.isBlank()) {
         this.data.set(path(playerId) + ".last-game-mode", gameMode.toLowerCase(Locale.ROOT));
         this.save();
      }
   }

   public String getLastGameMode(UUID playerId) {
      return this.data.getString(path(playerId) + ".last-game-mode", "");
   }

   public void setCelebration(UUID playerId, String celebrationId) {
      if (celebrationId != null && !celebrationId.isBlank()) {
         this.data.set(path(playerId) + ".celebration", celebrationId.toLowerCase(Locale.ROOT));
         this.save();
      }
   }

   public String getCelebration(UUID playerId) {
      return this.data.getString(path(playerId) + ".celebration", "");
   }

   public void setKitCelebration(UUID playerId, String kitName, String celebrationId) {
      if (kitName != null && !kitName.isBlank() && celebrationId != null && !celebrationId.isBlank()) {
         this.data.set(path(playerId) + ".kit-celebrations." + kitName.toLowerCase(Locale.ROOT), celebrationId.toLowerCase(Locale.ROOT));
         this.save();
      }
   }

   public String getKitCelebration(UUID playerId, String kitName) {
      return kitName == null || kitName.isBlank()
         ? ""
         : this.data.getString(path(playerId) + ".kit-celebrations." + kitName.toLowerCase(Locale.ROOT), "");
   }

   public boolean isBossBarEnabled(UUID playerId) {
      return this.data.getBoolean(path(playerId) + ".settings.bossbar", true);
   }

   public void setBossBarEnabled(UUID playerId, boolean enabled) {
      this.data.set(path(playerId) + ".settings.bossbar", enabled);
      this.save();
   }

   public boolean isScoreboardEnabled(UUID playerId) {
      return this.data.getBoolean(path(playerId) + ".settings.scoreboard", true);
   }

   public void setScoreboardEnabled(UUID playerId, boolean enabled) {
      this.data.set(path(playerId) + ".settings.scoreboard", enabled);
      this.save();
   }

   public boolean hasKitLayout(UUID playerId, String kitName) {
      if (kitName == null || kitName.isBlank()) {
         return false;
      }

      return this.data.isConfigurationSection(layoutPath(playerId, kitName));
   }

   public void setKitLayout(UUID playerId, String kitName, ItemStack[] inventory, ItemStack[] armor, ItemStack offhand) {
      if (kitName == null || kitName.isBlank()) {
         return;
      }

      String base = layoutPath(playerId, kitName);
      this.data.set(base + ".inventory", toList(inventory, 36));
      this.data.set(base + ".armor", toList(armor, 4));
      this.data.set(base + ".offhand", offhand);
      this.save();
   }

   public void clearKitLayout(UUID playerId, String kitName) {
      if (kitName != null && !kitName.isBlank()) {
         this.data.set(layoutPath(playerId, kitName), null);
         this.save();
      }
   }

   public ItemStack[] getKitLayoutInventory(UUID playerId, String kitName) {
      return this.readItemArray(layoutPath(playerId, kitName) + ".inventory", 36);
   }

   public ItemStack[] getKitLayoutArmor(UUID playerId, String kitName) {
      return this.readItemArray(layoutPath(playerId, kitName) + ".armor", 4);
   }

   public ItemStack getKitLayoutOffhand(UUID playerId, String kitName) {
      return this.data.getItemStack(layoutPath(playerId, kitName) + ".offhand");
   }

   public boolean hasKitTrim(UUID playerId, String kitName) {
      if (kitName == null || kitName.isBlank()) {
         return false;
      }

      return this.data.isConfigurationSection(trimPath(playerId, kitName));
   }

   public void setKitTrim(UUID playerId, String kitName, String patternKey, String materialKey) {
      if (kitName != null && !kitName.isBlank()) {
         String base = trimPath(playerId, kitName);
         this.data.set(base + ".pattern", patternKey);
         this.data.set(base + ".material", materialKey);
         this.save();
      }
   }

   public String getKitTrimPattern(UUID playerId, String kitName) {
      return this.data.getString(trimPath(playerId, kitName) + ".pattern");
   }

   public String getKitTrimMaterial(UUID playerId, String kitName) {
      return this.data.getString(trimPath(playerId, kitName) + ".material");
   }

   public void clearKitTrim(UUID playerId, String kitName) {
      if (kitName != null && !kitName.isBlank()) {
         this.data.set(trimPath(playerId, kitName), null);
         this.save();
      }
   }

   private static String trimPath(UUID playerId, String kitName) {
      return path(playerId) + ".kit-trims." + kitName.toLowerCase(Locale.ROOT);
   }

   private ItemStack[] readItemArray(String path, int size) {
      List<?> list = this.data.getList(path);
      if (list == null) {
         return null;
      }

      ItemStack[] result = new ItemStack[size];

      for (int i = 0; i < Math.min(size, list.size()); i++) {
         if (list.get(i) instanceof ItemStack stack) {
            result[i] = stack;
         }
      }

      return result;
   }

   private static List<ItemStack> toList(ItemStack[] source, int size) {
      List<ItemStack> list = new ArrayList<>(size);

      for (int i = 0; i < size; i++) {
         list.add(source != null && i < source.length ? source[i] : null);
      }

      return list;
   }

   private static String layoutPath(UUID playerId, String kitName) {
      return path(playerId) + ".kit-layouts." + kitName.toLowerCase(Locale.ROOT);
   }

   private static String path(UUID playerId) {
      return "players." + playerId;
   }
}
