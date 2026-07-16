package org.Mona.monaDuels.lobby;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map.Entry;
import org.Mona.monaDuels.MonaDuels;
import org.Mona.monaDuels.config.ConfigManager;
import org.Mona.monaDuels.duel.DuelManager;
import org.Mona.monaDuels.gui.KitLayoutEditorService;
import org.Mona.monaDuels.kit.Kit;
import org.Mona.monaDuels.kit.KitManager;
import org.Mona.monaDuels.player.PlayerDataManager;
import org.Mona.monaDuels.util.ColorUtil;
import org.Mona.monaDuels.util.ConfigurableItemParser;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class LobbyLayoutService {
   private final MonaDuels plugin;
   private final ConfigManager configManager;
   private final KitManager kitManager;
   private final PlayerDataManager playerDataManager;
   private final DuelManager duelManager;
   private KitLayoutEditorService editorService;
   private final Set<String> lobbyWorlds = new HashSet<>();
   private final Map<String, LobbyLayoutService.LobbyItemDefinition> itemsById = new HashMap<>();
   private final Map<Integer, String> slotToItemId = new HashMap<>();
   private final NamespacedKey duelItemKey;
   private final Set<UUID> expandedHotbar = ConcurrentHashMap.newKeySet();
   private boolean enabled = true;
   
   public NamespacedKey getDuelItemKey() {
      return this.duelItemKey;
   }

   public LobbyLayoutService(MonaDuels plugin, ConfigManager configManager, KitManager kitManager, PlayerDataManager playerDataManager, DuelManager duelManager) {
      this.plugin = plugin;
      this.configManager = configManager;
      this.kitManager = kitManager;
      this.playerDataManager = playerDataManager;
      this.duelManager = duelManager;
      this.duelItemKey = new NamespacedKey(plugin, "lobby_duel_item");
   }

   public void bindEditor(KitLayoutEditorService editorService) {
      this.editorService = editorService;
   }

   public void load() {
      this.lobbyWorlds.clear();
      this.itemsById.clear();
      this.slotToItemId.clear();
      File file = new File(this.plugin.getDataFolder(), "lobby-layout.yml");
      if (!file.exists()) {
         this.plugin.saveResource("lobby-layout.yml", false);
      }

      YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
      this.enabled = cfg.getBoolean("enabled", true);

      for (String world : cfg.getStringList("worlds")) {
         if (world != null && !world.isBlank()) {
            this.lobbyWorlds.add(world.toLowerCase(Locale.ROOT));
         }
      }

      if (this.lobbyWorlds.isEmpty()) {
         this.lobbyWorlds.add(this.configManager.lobbyWorldName().toLowerCase(Locale.ROOT));
      }

      this.lobbyWorlds.add(this.configManager.lobbyHotbarWorld().toLowerCase(Locale.ROOT));

      ConfigurationSection items = cfg.getConfigurationSection("items");
      if (items != null) {
         for (String id : items.getKeys(false)) {
            ConfigurationSection section = items.getConfigurationSection(id);
            if (section != null && !section.getBoolean("skip", false) && !section.getBoolean("navigator", false)) {
               int slot = section.getInt("slot", -1);
               if (slot >= 0 && slot <= 8) {
                  String action = section.getString("action", "");
                  boolean dynamicLastKit = section.getBoolean("dynamic-last-kit", false) || "last-kit".equalsIgnoreCase(id);
                  boolean dynamicHead = section.getBoolean("player-head", false) || section.getBoolean("own-head", false) || "stats".equalsIgnoreCase(id);
                  LobbyLayoutService.LobbyItemDefinition def = new LobbyLayoutService.LobbyItemDefinition(
                     id.toLowerCase(Locale.ROOT), slot, section, action, dynamicLastKit, dynamicHead
                  );
                  this.itemsById.put(def.id(), def);
                  this.slotToItemId.put(slot, def.id());
               } else {
                  this.plugin.getLogger().warning("lobby-layout item '" + id + "' has invalid slot: " + slot);
               }
            }
         }
      }
   }

   public boolean isEnabled() {
      return this.enabled;
   }

   public boolean isLobbyWorld(String worldName) {
      return worldName == null ? false : this.lobbyWorlds.contains(worldName.toLowerCase(Locale.ROOT));
   }

   public Set<Integer> managedSlots() {
      Set<Integer> slots = new HashSet<>(this.slotToItemId.keySet());
      if (this.configManager.lobbyHotbarEnabled()) {
         slots.add(this.configManager.lobbyItemSlotIndex());
      }

      return slots;
   }

   public boolean isExpanded(UUID playerId) {
      return this.expandedHotbar.contains(playerId);
   }

   public void expand(Player player) {
      this.expandedHotbar.add(player.getUniqueId());
      this.applyLayout(player);
   }

   public void collapse(Player player) {
      this.expandedHotbar.remove(player.getUniqueId());
      this.applyLayout(player);
   }

   public void forgetPlayer(UUID playerId) {
      this.expandedHotbar.remove(playerId);
   }

   public void clearLayout(Player player) {
      if (player != null && player.isOnline()) {
         this.expandedHotbar.remove(player.getUniqueId());
         PlayerInventory inv = player.getInventory();

         for (int slot : this.managedSlots()) {
            inv.setItem(slot, null);
         }
      }
   }

   public void applyLayout(Player player) {
      if (this.enabled && player != null && player.isOnline()) {
         if (this.isLobbyWorld(player.getWorld().getName())) {
            if (this.editorService != null && this.editorService.isEditing(player.getUniqueId())) {
               return;
            }

            if (!this.duelManager.isInDuel(player.getUniqueId())) {
               PlayerInventory inv = player.getInventory();

               for (int slot : this.managedSlots()) {
                  inv.setItem(slot, null);
               }

               if (this.isExpanded(player.getUniqueId())) {
                  // Duel hotbar: items from lobby-layout.yml, no rod. Slot 8 stays free.
                  Map<String, String> placeholders = this.buildPlaceholders(player);

                  for (LobbyLayoutService.LobbyItemDefinition def : this.itemsById.values()) {
                     ItemStack stack = this.buildLobbyItem(player, def, placeholders);
                     if (stack != null) {
                        inv.setItem(def.slot(), stack);
                     }
                  }
               } else if (this.configManager.lobbyHotbarEnabled()) {
                  // Default state: ONLY the rod activator.
                  inv.setItem(this.configManager.lobbyItemSlotIndex(), this.buildLobbyDuelItem());
               }
            }
         }
      }
   }

   public String itemIdAtSlot(int hotbarSlot) {
      return this.slotToItemId.get(hotbarSlot);
   }

   public LobbyLayoutService.LobbyItemDefinition item(String id) {
      return this.itemsById.get(id.toLowerCase(Locale.ROOT));
   }

   public boolean isLobbyDuelItem(ItemStack item) {
      if (item == null) {
         return false;
      }

      ItemMeta meta = item.getItemMeta();
      return meta != null && meta.getPersistentDataContainer().has(this.duelItemKey, PersistentDataType.BYTE);
   }

   public ItemStack getDefaultHotbarItem(int slot) {
      String itemId = this.itemIdAtSlot(slot);
      if (itemId != null) {
         LobbyLayoutService.LobbyItemDefinition def = this.item(itemId);
         if (def != null) {
            return new ItemStack(ConfigurableItemParser.parseMaterial(def.section().getString("material", "stone")));
         }
      }
      return new ItemStack(Material.AIR);
   }

   private ItemStack buildLobbyDuelItem() {
      ItemStack stack = new ItemStack(ConfigurableItemParser.parseMaterial(this.configManager.lobbyItemMaterial()));
      ItemMeta meta = stack.getItemMeta();
      if (meta != null) {
         meta.displayName(ColorUtil.component(this.configManager.lobbyItemName()));
         List<String> lore = this.configManager.lobbyItemLore();
         if (!lore.isEmpty()) {
            meta.lore(lore.stream().map(ColorUtil::component).toList());
         }

         meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
         meta.getPersistentDataContainer().set(this.duelItemKey, PersistentDataType.BYTE, (byte)1);
         stack.setItemMeta(meta);
      }

      return stack;
   }

   private ItemStack buildLobbyItem(Player player, LobbyLayoutService.LobbyItemDefinition def, Map<String, String> placeholders) {
      if (def.dynamicLastKit()) {
         String lastKit = this.playerDataManager.getLastKit(player.getUniqueId());
         return lastKit.isBlank()
            ? this.buildFallbackLastKit(def, placeholders, player)
            : this.kitManager
               .find(lastKit)
               .map(kit -> this.buildLastKitItem(player, kit, def, placeholders))
               .orElseGet(() -> this.buildFallbackLastKit(def, placeholders, player));
      } else {
         ConfigurationSection section = def.section();
         if (def.dynamicHead()) {
            ConfigurationSection copy = this.cloneSectionForHead(section, player);
            return ConfigurableItemParser.fromSection(copy, player, placeholders);
         } else {
            return ConfigurableItemParser.fromSection(section, player, placeholders);
         }
      }
   }

   private ItemStack buildLastKitItem(Player player, Kit kit, LobbyLayoutService.LobbyItemDefinition def, Map<String, String> placeholders) {
      ItemStack stack = new ItemStack(kit.iconMaterial());
      ItemMeta meta = stack.getItemMeta();
      if (meta != null) {
         String name = def.section().getString("display-name", "&7{last_kit_display}");
         name = name.replace("{last_kit}", kit.name()).replace("{last_kit_display}", ColorUtil.strip(kit.displayName()));
         meta.displayName(ColorUtil.component(messagesApply(name, placeholders)));
         List<String> lore = def.section().getStringList("lore");
         if (!lore.isEmpty()) {
            meta.lore(
               lore.stream()
                  .map(
                     line -> ColorUtil.component(
                           messagesApply(line, placeholders).replace("{last_kit}", kit.name()).replace("{last_kit_display}", ColorUtil.strip(kit.displayName()))
                        )
                  )
                  .toList()
            );
         }

         stack.setItemMeta(meta);
      }

      return stack;
   }

   private ItemStack buildFallbackLastKit(LobbyLayoutService.LobbyItemDefinition def, Map<String, String> placeholders, Player player) {
      ConfigurationSection section = def.section();
      Material material = ConfigurableItemParser.parseMaterial(section.getString("material", "BARRIER"));
      ItemStack stack = new ItemStack(material);
      ItemMeta meta = stack.getItemMeta();
      if (meta != null) {
         meta.displayName(ColorUtil.component(messagesApply(section.getString("empty-display-name", "&8нет истории"), placeholders)));
         meta.lore(section.getStringList("empty-lore").stream().map(line -> ColorUtil.component(messagesApply(line, placeholders))).toList());
         stack.setItemMeta(meta);
      }

      return stack;
   }

   private ConfigurationSection cloneSectionForHead(ConfigurationSection source, Player player) {
      YamlConfiguration temp = new YamlConfiguration();

      for (String key : source.getKeys(true)) {
         temp.set(key, source.get(key));
      }

      temp.set("player-head", true);
      temp.set("material", "PLAYER_HEAD");
      return temp;
   }

   private Map<String, String> buildPlaceholders(Player player) {
      Map<String, String> ph = new HashMap<>();
      String lastKit = this.playerDataManager.getLastKit(player.getUniqueId());
      ph.put("last_kit", lastKit);
      this.kitManager.find(lastKit).ifPresent(kit -> ph.put("last_kit_display", ColorUtil.strip(kit.displayName())));
      if (!ph.containsKey("last_kit_display")) {
         ph.put("last_kit_display", "—");
      }

      return ph;
   }

   private static String messagesApply(String raw, Map<String, String> placeholders) {
      String result = raw;

      for (Entry<String, String> entry : placeholders.entrySet()) {
         result = result.replace("{" + entry.getKey() + "}", entry.getValue());
         result = result.replace("%" + entry.getKey() + "%", entry.getValue());
      }

      return result;
   }

   public static record LobbyItemDefinition(String id, int slot, ConfigurationSection section, String action, boolean dynamicLastKit, boolean dynamicHead) {
   }
}
