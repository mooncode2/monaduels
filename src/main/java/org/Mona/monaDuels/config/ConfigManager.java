package org.Mona.monaDuels.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import org.Mona.monaDuels.MonaDuels;
import org.Mona.monaDuels.util.LocationUtil;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public final class ConfigManager {
   private final MonaDuels plugin;
   private FileConfiguration config;
   private FileConfiguration arenas;
   private FileConfiguration messages;
   private File arenasFile;

   public ConfigManager(MonaDuels plugin) {
      this.plugin = plugin;
   }

   public void loadAll() {
      this.plugin.saveDefaultConfig();
      this.plugin.saveResource("arenas.yml", false);
      this.plugin.saveResource("messages.yml", false);
      this.plugin.saveResource("map-pools.yml", false);
      this.ensureKitsFolder();
      this.reloadConfig();
      this.reloadArenas();
      this.reloadMessages();
   }

   public void reloadAll() {
      this.reloadConfig();
      this.reloadArenas();
      this.reloadMessages();
   }

   public void reloadConfig() {
      this.plugin.reloadConfig();
      this.config = this.plugin.getConfig();
   }

   public void reloadArenas() {
      this.arenasFile = new File(this.plugin.getDataFolder(), "arenas.yml");
      this.arenas = YamlConfiguration.loadConfiguration(this.arenasFile);
   }

   public void reloadMessages() {
      File file = new File(this.plugin.getDataFolder(), "messages.yml");
      if (!file.exists()) {
         this.plugin.saveResource("messages.yml", false);
      }

      YamlConfiguration defaults = null;
      if (this.plugin.getResource("messages.yml") != null) {
         try (InputStream in = this.plugin.getResource("messages.yml")) {
            defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
         } catch (IOException var8) {
            this.plugin.getLogger().log(Level.WARNING, "Could not load default messages.yml from jar", (Throwable)var8);
         }
      }

      this.messages = YamlConfiguration.loadConfiguration(file);
      if (defaults != null) {
         this.messages.setDefaults(defaults);
         this.messages.options().copyDefaults(true);
      }
   }

   public void saveArenas() {
      try {
         this.arenas.save(this.arenasFile);
      } catch (IOException var2) {
         this.plugin.getLogger().log(Level.SEVERE, "Failed to save arenas.yml", (Throwable)var2);
      }
   }

   private void ensureKitsFolder() {
      File kits = new File(this.plugin.getDataFolder(), "kits");
      if (!kits.exists()) {
         kits.mkdirs();
      }
   }

   public String duelWorld() {
      return this.config.getString("duel-world", "duels");
   }

   public Location lobby() {
      return LocationUtil.readLocation(this.config.getConfigurationSection("lobby"));
   }

   public void setLobby(Location location) {
      ConfigurationSection lobby = this.config.getConfigurationSection("lobby");
      if (lobby == null) {
         lobby = this.config.createSection("lobby");
      }

      LocationUtil.writeLocation(lobby, location);
      this.plugin.saveConfig();
   }

   public int countdownSeconds() {
      return Math.max(0, this.config.getInt("countdown", 5));
   }

   public int postKillDelaySeconds() {
      return Math.max(0, this.config.getInt("post-kill-delay-seconds", 5));
   }

   public boolean matchFoundEnabled() {
      return this.config.getBoolean("display.match-found.enabled", true);
   }

   public String matchFoundActionBar() {
      return this.config.getString("display.match-found.action-bar", "&8[&7D&8] &7Дуэль // &fНайдено: &e&l⚔ Матч найден ⚔");
   }

   public String matchFoundTitle() {
      return this.config.getString("display.match-found.title", "&e&l⚔ Матч найден ⚔");
   }

   public String matchFoundSubtitle() {
      return this.config.getString("display.match-found.subtitle", "&7Телепортация на арену...");
   }

   public int matchFoundStaySeconds() {
      return Math.max(1, this.config.getInt("display.match-found.stay-seconds", 2));
   }

   public int matchFoundFadeInTicks() {
      return Math.max(0, this.config.getInt("display.match-found.fade-in-ticks", 5));
   }

   public int matchFoundFadeOutTicks() {
      return Math.max(0, this.config.getInt("display.match-found.fade-out-ticks", 5));
   }

   public boolean countdownChatEnabled() {
      return this.config.getBoolean("display.countdown.chat", true);
   }

   public boolean countdownTitleEnabled() {
      return this.config.getBoolean("display.countdown.title.enabled", true);
   }

   public String countdownTitleTick() {
      return this.config.getString("display.countdown.title.tick", "&e{seconds}");
   }

   public String countdownSubtitleTick() {
      return this.config.getString("display.countdown.title.subtitle-tick", "&7Дуэль скоро...");
   }

   public String countdownTitleStart() {
      return this.config.getString("display.countdown.title.start", "&a&lДУЭЛЬ!");
   }

   public String countdownSubtitleStart() {
      return this.config.getString("display.countdown.title.subtitle-start", "&7Удачи!");
   }

   public int countdownTitleFadeInTicks() {
      return Math.max(0, this.config.getInt("display.countdown.title.fade-in-ticks", 5));
   }

   public int countdownTitleStayTicks() {
      return Math.max(0, this.config.getInt("display.countdown.title.stay-ticks", 20));
   }

   public int countdownTitleFadeOutTicks() {
      return Math.max(0, this.config.getInt("display.countdown.title.fade-out-ticks", 5));
   }

   public boolean nametagHpEnabled() {
      return this.config.contains("display.nametag.enabled")
         ? this.config.getBoolean("display.nametag.enabled")
         : this.config.getBoolean("display.tab.enabled", true);
   }

   public String nametagFormat() {
      String value = this.config.getString("display.nametag.format");
      return value != null ? value : this.config.getString("display.tab.opponent-format", " &c{hp}{hearts}");
   }

   public String nametagHeartsSymbol() {
      String value = this.config.getString("display.nametag.hearts-symbol");
      return value != null ? value : this.config.getString("display.tab.hearts-symbol", "❤");
   }

   public int nametagHpDecimals() {
      return this.config.contains("display.nametag.hp-decimals")
         ? Math.max(0, this.config.getInt("display.nametag.hp-decimals"))
         : Math.max(0, this.config.getInt("display.tab.hp-decimals", 1));
   }

   public boolean scoreboardEnabled() {
      return this.config.getBoolean("display.scoreboard.enabled", true);
   }

   public String scoreboardTitle() {
      return this.config.getString("display.scoreboard.title", "&c&lДУЭЛЬ");
   }

   public List<String> scoreboardLines() {
      List<String> lines = this.config.getStringList("display.scoreboard.lines");
      return lines.isEmpty()
         ? List.of(
            "&8&m────────────",
            "&7Противник: &f{opponent}",
            "&c{hearts} HP: &f{opponent_hp}",
            "&8&m────────────",
            "&a▸ Твой пинг: &f{your_ping} &7ms",
            "&c▸ Пинг врага: &f{opponent_ping} &7ms"
         )
         : lines;
   }

   public int displayUpdateTicks() {
      return Math.max(1, this.config.getInt("display.update-ticks", 4));
   }

   public int requestTimeoutSeconds() {
      return Math.max(5, this.config.getInt("request-timeout", 60));
   }

   public int cooldown(String key, int defaultSeconds) {
      return Math.max(0, this.config.getInt("cooldowns." + key, defaultSeconds));
   }

   public List<String> allowedCommandsDuringDuel() {
      return this.config.getStringList("allowed-commands-during-duel");
   }

   public boolean protectionEnabled(String key) {
      return this.config.getBoolean("protection." + key, true);
   }

   public double maxEscapeDistance() {
      return this.config.getDouble("protection.max-escape-distance", 0.0);
   }

   public boolean logoutForfeit() {
      return this.config.getBoolean("protection.logout-forfeit", true);
   }

   public boolean trackPlacedBlocks() {
      return this.config.getBoolean("blocks.track-placed", true);
   }

   public boolean trackBrokenBlocks() {
      return this.config.getBoolean("blocks.track-broken", true);
   }

   public boolean restoreBrokenBlocks() {
      return this.config.getBoolean("blocks.restore-broken", true);
   }

   public String defaultKitMenu() {
      return this.config.getString("default-kit-menu", "kits-menu").toLowerCase(Locale.ROOT);
   }

   public MonaDuels plugin() {
      return this.plugin;
   }

   public File dataFolder() {
      return this.plugin.getDataFolder();
   }

   public boolean debug() {
      return this.config.getBoolean("debug", false);
   }

   public boolean mvInventoriesEnabled() {
      return this.config.getBoolean("multiverse-inventories.enabled", true);
   }

   public boolean mvAutoCreateGroup() {
      return this.config.getBoolean("multiverse-inventories.auto-create-group", true);
   }

   public String mvGroupName() {
      return this.config.getString("multiverse-inventories.group-name", "monaduels");
   }

   public int arenaReteleportDelayTicks() {
      return Math.max(1, this.config.getInt("multiverse-inventories.arena-reteleport-delay-ticks", 10));
   }

   public String lobbyWorldName() {
      Location lobby = this.lobby();
      return lobby != null && lobby.getWorld() != null ? lobby.getWorld().getName() : this.config.getString("lobby.world", "lobby");
   }

   public int partyMaxSize() {
      return Math.max(2, this.config.getInt("party.max-size", 2));
   }

   public double partySpawnOffset() {
      return this.config.getDouble("party.spawn-teammate-offset", 1.0);
   }

   public FileConfiguration config() {
      return this.config;
   }

   public FileConfiguration arenas() {
      return this.arenas;
   }

   public FileConfiguration messages() {
      return this.messages;
   }

   public boolean bossBarEnabled() {
      return this.config.getBoolean("display.bossbar.enabled", true);
   }

   public String bossBarFormat() {
      return this.config.getString("display.bossbar.format", "&f⚔ &f{opponent} &8| &e{kit} &8| {status}");
   }

   public String bossBarColor() {
      return this.config.getString("display.bossbar.color", "RED");
   }

   public String bossBarStatusInGame() {
      return this.config.getString("display.bossbar.status.in-game", "&aВ игре");
   }

   public String bossBarStatusPreparing() {
      return this.config.getString("display.bossbar.status.preparing", "&eПодготовка");
   }

   public String bossBarStatusSpectating() {
      return this.config.getString("display.bossbar.status.spectating", "&bНаблюдение");
   }

   public boolean soundsEnabled() {
      return this.config.getBoolean("sounds.enabled", true);
   }

   public String soundCountdownTick() {
      return this.config.getString("sounds.countdown-tick", "BLOCK_NOTE_BLOCK_PLING");
   }

   public float soundCountdownTickVolume() {
      return (float)this.config.getDouble("sounds.countdown-tick-volume", 0.8);
   }

   public float soundCountdownTickPitch() {
      return (float)this.config.getDouble("sounds.countdown-tick-pitch", 1.2);
   }

   public String soundDuelStart() {
      return this.config.getString("sounds.duel-start", "ENTITY_PLAYER_LEVELUP");
   }

   public float soundDuelStartVolume() {
      return (float)this.config.getDouble("sounds.duel-start-volume", 1.0);
   }

   public float soundDuelStartPitch() {
      return (float)this.config.getDouble("sounds.duel-start-pitch", 1.0);
   }

   public String soundVictory() {
      return this.config.getString("sounds.victory", "UI_TOAST_CHALLENGE_COMPLETE");
   }

   public float soundVictoryVolume() {
      return (float)this.config.getDouble("sounds.victory-volume", 1.0);
   }

   public float soundVictoryPitch() {
      return (float)this.config.getDouble("sounds.victory-pitch", 1.0);
   }

   public String soundDefeat() {
      return this.config.getString("sounds.defeat", "ENTITY_VILLAGER_NO");
   }

   public float soundDefeatVolume() {
      return (float)this.config.getDouble("sounds.defeat-volume", 1.0);
   }

   public float soundDefeatPitch() {
      return (float)this.config.getDouble("sounds.defeat-pitch", 0.8);
   }

   public boolean endTitleEnabled() {
      return this.config.getBoolean("display.end-title.enabled", true);
   }

   public String endTitleWin() {
      return this.config.getString("display.end-title.win", "&6&lПОБЕДА!");
   }

   public String endTitleLose() {
      return this.config.getString("display.end-title.lose", "&c&lПОРАЖЕНИЕ");
   }

   public String endTitleWinSubtitle() {
      return this.config.getString("display.end-title.win-subtitle", "&7Вы победили &f{opponent}");
   }

   public String endTitleLoseSubtitle() {
      return this.config.getString("display.end-title.lose-subtitle", "&7Вы проиграли &f{opponent}");
   }

   public int endTitleFadeInTicks() {
      return Math.max(0, this.config.getInt("display.end-title.fade-in-ticks", 5));
   }

   public int endTitleStayTicks() {
      return Math.max(0, this.config.getInt("display.end-title.stay-ticks", 60));
   }

   public int endTitleFadeOutTicks() {
      return Math.max(0, this.config.getInt("display.end-title.fade-out-ticks", 20));
   }

   public boolean resultsChatEnabled() {
      return this.config.getBoolean("results.chat.enabled", true);
   }

   public boolean resultsGuiEnabled() {
      return this.config.getBoolean("results.gui.enabled", true);
   }

   public String resultsGuiTitle() {
      return this.config.getString("results.gui.title", "&8Результаты матча");
   }

   public boolean requestButtonsEnabled() {
      return this.config.getBoolean("request.buttons.enabled", true);
   }

   public String spectateMenuTitle() {
      return this.config.getString("spectate.menu-title", "&8Активные дуэли");
   }

   public double spectateHeightOffset() {
      return this.config.getDouble("spectate.height-offset", 8.0);
   }

   public int defaultElo() {
      return this.config.getInt("stats.default-elo", 1000);
   }

   public int eloKFactor() {
      return this.config.getInt("stats.elo-k-factor", 32);
   }

   public ConfigurationSection killEffectsSection() {
      return this.config.getConfigurationSection("kill-effects.effects");
   }

   public String defaultKillEffect() {
      return this.config.getString("kill-effects.default", "victory_burst");
   }

   public boolean queueEnabled() {
      return this.config.getBoolean("queue.enabled", true);
   }

   public boolean playAgainEnabled() {
      return this.config.getBoolean("play-again.enabled", true);
   }

   public int playAgainSlot() {
      return Math.max(0, Math.min(8, this.config.getInt("play-again.slot", 3)));
   }

   public String playAgainMaterial() {
      return this.config.getString("play-again.material", "PAPER");
   }

   public String playAgainName() {
      return this.config.getString("play-again.name", "&a&lИграть снова");
   }

   public List<String> playAgainLore() {
      List<String> lore = this.config.getStringList("play-again.lore");
      return lore.isEmpty() ? List.of("&7Кит: &f{kit}", "", "&eПКМ — снова в очередь") : lore;
   }

   // --- Lobby activator item (Task 1) ---

   public boolean lobbyHotbarEnabled() {
      return this.config.getBoolean("lobby-hotbar-enabled", true);
   }

   public String lobbyHotbarWorld() {
      String world = this.config.getString("lobby-world");
      return world != null && !world.isBlank() ? world : this.lobbyWorldName();
   }

   public int lobbyItemSlotIndex() {
      int slot = this.config.getInt("lobby-item-slot", 5);
      return Math.max(0, Math.min(8, slot - 1));
   }

   public String lobbyItemMaterial() {
      return this.config.getString("lobby-item-material", "FISHING_ROD");
   }

   public String lobbyItemName() {
      return this.config.getString("lobby-item-name", "&b&lДуэли");
   }

   public List<String> lobbyItemLore() {
      List<String> lore = this.config.getStringList("lobby-item-lore");
      return lore.isEmpty() ? List.of("&7ПКМ — открыть меню наборов") : lore;
   }

   // --- Generic GUI button name/lore helpers (Tasks 3 & 4) ---

   public String uiName(String path, String def) {
      return this.config.getString(path + ".name", def);
   }

   public List<String> uiLore(String path, List<String> def) {
      List<String> lore = this.config.getStringList(path + ".lore");
      return lore.isEmpty() ? def : lore;
   }

   // --- Armor trims (Tasks 3 & 4b) ---

   public boolean trimsEnabled() {
      return this.config.getBoolean("trims.enabled", true);
   }

   public String trimsPermission() {
      return this.config.getString("trims.permission", "monaprime.prime");
   }

   public String trimsGroup() {
      return this.config.getString("trims.group", "monaprime");
   }

   public String trimsMenuTitle() {
      return this.config.getString("trims.menu-title", "&8Шаблоны брони");
   }

   public List<String> trimsAllowedPatterns() {
      return this.config.getStringList("trims.patterns");
   }

   public List<String> trimsAllowedMaterials() {
      return this.config.getStringList("trims.materials");
   }

   // --- Celebrations gate (Rev. 2) ---

   public boolean celebrationsEnabled() {
      return this.config.getBoolean("celebrations.enabled", true);
   }

   public String celebrationsPermission() {
      return this.config.getString("celebrations.permission", "monaduels.celebrations");
   }

   public String celebrationsGroup() {
      return this.config.getString("celebrations.group", "monaprime");
   }

   // --- Queue action bar (Rev. 2) ---

   public boolean queueActionBarEnabled() {
      return this.config.getBoolean("queue.actionbar-enabled", true);
   }

   public String queueActionBarFormat() {
      return this.config.getString("queue.actionbar-format", "&e⚔ &fНабор: &e{kit} &8| &b👥 &fВ очереди: &b{count}");
   }

   public String settingsMenuTitle() {
      return this.config.getString("settings.title", "&8Настройки дуэлей");
   }
}
