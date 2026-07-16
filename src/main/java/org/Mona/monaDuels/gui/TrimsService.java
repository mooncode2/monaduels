package org.Mona.monaDuels.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.Mona.monaDuels.MonaDuels;
import org.Mona.monaDuels.config.ConfigManager;
import org.Mona.monaDuels.config.MessageService;
import org.Mona.monaDuels.kit.Kit;
import org.Mona.monaDuels.kit.KitManager;
import org.Mona.monaDuels.player.PlayerDataManager;
import org.Mona.monaDuels.service.TrimService;
import org.Mona.monaDuels.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;

/**
 * Armor-trim picker GUI (Task 3/4b). Only players passing the {@link TrimService} gate can open it.
 * Selecting a pattern + material and saving persists the trim per player/kit; it is then applied to
 * the kit's armor at match start and reflected in the preview.
 */
public final class TrimsService {
   private static final int INFO_SLOT = 45;
   private static final int APPLY_ALL_SLOT = 46;
   private static final int SAVE_SLOT = 48;
   private static final int REMOVE_SLOT = 50;
   private static final int BACK_SLOT = 52;
   private static final int MAX_PATTERN_SLOT = 17;
   private static final int MATERIAL_START = 27;
   private static final int MATERIAL_END = 44;
   private final MonaDuels plugin;
   private final ConfigManager config;
   private final MessageService messages;
   private final KitManager kitManager;
   private final PlayerDataManager playerData;
   private final TrimService trimService;
   private KitPreviewService previewService;
   private final Map<UUID, PendingTrim> pending = new ConcurrentHashMap<>();

   public TrimsService(MonaDuels plugin, ConfigManager config, MessageService messages, KitManager kitManager, PlayerDataManager playerData, TrimService trimService) {
      this.plugin = plugin;
      this.config = config;
      this.messages = messages;
      this.kitManager = kitManager;
      this.playerData = playerData;
      this.trimService = trimService;
   }

   public void bindPreview(KitPreviewService previewService) {
      this.previewService = previewService;
   }

   public boolean isEnabled() {
      return this.trimService.isEnabled();
   }

   public boolean canOpen(Player player) {
      return this.trimService.canUse(player);
   }

   /** Applies the viewer's stored trim to a preview armor array (already cloned by the caller). */
   public void applyPreviewTrim(Player viewer, String kitName, ItemStack[] armor) {
      if (this.trimService.canUse(viewer)) {
         TrimService.applyTrim(armor, this.trimService.resolveTrim(viewer.getUniqueId(), kitName));
      }
   }

   public void open(Player player, String kitName) {
      if (!this.trimService.isEnabled() || !this.trimService.canUse(player)) {
         this.messages.send(player, "trims.locked");
         return;
      }

      Kit kit = this.kitManager.find(kitName).orElse(null);
      if (kit == null) {
         this.messages.send(player, "kit.not-found", Map.of("kit", kitName));
         return;
      }

      UUID id = player.getUniqueId();
      PendingTrim state = new PendingTrim(kit.name());
      if (this.playerData.hasKitTrim(id, kit.name())) {
         state.patternKey = this.playerData.getKitTrimPattern(id, kit.name());
         state.materialKey = this.playerData.getKitTrimMaterial(id, kit.name());
      }

      Inventory inv = Bukkit.createInventory(new TrimsService.TrimsHolder(kit.name()), 54, ColorUtil.color(this.config.trimsMenuTitle() + " &8· " + kit.displayName()));
      this.pending.put(id, state);
      this.render(player, inv, state);
      player.openInventory(inv);
   }

   public void handleClick(Player player, int rawSlot, Inventory top) {
      PendingTrim state = this.pending.get(player.getUniqueId());
      if (state == null || rawSlot < 0 || rawSlot >= top.getSize()) {
         return;
      }

      if (rawSlot == SAVE_SLOT) {
         this.save(player, state);
      } else if (rawSlot == APPLY_ALL_SLOT) {
         this.applyToAllKits(player, state);
      } else if (rawSlot == REMOVE_SLOT) {
         this.remove(player, top, state);
      } else if (rawSlot == BACK_SLOT) {
         this.backToPreview(player, state.kitName);
      } else {
         TrimPattern pattern = state.patternSlots.get(rawSlot);
         if (pattern != null) {
            state.patternKey = pattern.getKey().toString();
            this.render(player, top, state);
            return;
         }

         TrimMaterial material = state.materialSlots.get(rawSlot);
         if (material != null) {
            state.materialKey = material.getKey().toString();
            this.render(player, top, state);
         }
      }
   }

   public void handleClose(Player player) {
      this.pending.remove(player.getUniqueId());
   }

   private void save(Player player, PendingTrim state) {
      if (state.patternKey == null || state.materialKey == null) {
         this.messages.send(player, "trims.incomplete");
         return;
      }

      this.playerData.setKitTrim(player.getUniqueId(), state.kitName, state.patternKey, state.materialKey);
      this.messages.send(player, "trims.saved", Map.of("kit", state.kitName));
      this.backToPreview(player, state.kitName);
   }

   private void applyToAllKits(Player player, PendingTrim state) {
      if (state.patternKey == null || state.materialKey == null) {
         this.messages.send(player, "trims.incomplete");
         return;
      }

      for (Kit kit : this.kitManager.all()) {
         this.playerData.setKitTrim(player.getUniqueId(), kit.name(), state.patternKey, state.materialKey);
      }

      this.messages.send(player, "trims.applied-all");
      this.backToPreview(player, state.kitName);
   }

   private void remove(Player player, Inventory top, PendingTrim state) {
      this.playerData.clearKitTrim(player.getUniqueId(), state.kitName);
      state.patternKey = null;
      state.materialKey = null;
      this.messages.send(player, "trims.removed", Map.of("kit", state.kitName));
      this.render(player, top, state);
   }

   private void backToPreview(Player player, String kitName) {
      this.pending.remove(player.getUniqueId());
      this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
         if (this.previewService != null) {
            this.previewService.open(player, kitName);
         } else {
            player.closeInventory();
         }
      });
   }

   private void render(Player player, Inventory inv, PendingTrim state) {
      ItemStack filler = filler();

      for (int i = 0; i < inv.getSize(); i++) {
         inv.setItem(i, filler);
      }

      state.patternSlots.clear();
      int slot = 0;

      for (TrimPattern pattern : this.trimService.availablePatterns()) {
         if (slot > MAX_PATTERN_SLOT) {
            this.plugin.getLogger().warning("Trims menu: more patterns than slots — some are hidden.");
            break;
         }

         boolean selected = matches(pattern.getKey(), state.patternKey);
         inv.setItem(slot, this.selectableItem(this.trimService.patternIcon(pattern), "&7Паттерн", TrimService.prettyName(pattern.getKey()), selected));
         state.patternSlots.put(slot, pattern);
         slot++;
      }

      state.materialSlots.clear();
      int mslot = MATERIAL_START;

      for (TrimMaterial material : this.trimService.availableMaterials()) {
         if (mslot > MATERIAL_END) {
            this.plugin.getLogger().warning("Trims menu: more materials than slots — some are hidden.");
            break;
         }

         boolean selected = matches(material.getKey(), state.materialKey);
         inv.setItem(mslot, this.selectableItem(this.trimService.materialIcon(material), "&7Материал", TrimService.prettyName(material.getKey()), selected));
         state.materialSlots.put(mslot, material);
         mslot++;
      }

      inv.setItem(INFO_SLOT, this.infoItem(state));
      inv.setItem(APPLY_ALL_SLOT, button(Material.NETHER_STAR, "&b&lПрименить ко всем",
         List.of("&7Задать выбранный шаблон", "&7для всех наборов сразу")));
      inv.setItem(SAVE_SLOT, button(Material.LIME_DYE, "&a&lСохранить", List.of("&7Применить выбранный шаблон", "&7к броне этого набора")));
      inv.setItem(REMOVE_SLOT, button(Material.RED_DYE, "&c&lУбрать шаблон", List.of("&7Снять трим с брони набора")));
      inv.setItem(BACK_SLOT, button(Material.ARROW, "&e&lНазад", List.of("&7К предпросмотру набора")));
      player.updateInventory();
   }

   private ItemStack selectableItem(Material material, String category, String name, boolean selected) {
      ItemStack stack = new ItemStack(material);
      ItemMeta meta = stack.getItemMeta();
      if (meta != null) {
         meta.setDisplayName(ColorUtil.color((selected ? "&a&l" : "&f") + name));
         List<String> lore = new ArrayList<>();
         lore.add(ColorUtil.color("&8" + category));
         lore.add("");
         lore.add(ColorUtil.color(selected ? "&a✔ Выбрано" : "&eНажмите, чтобы выбрать"));
         meta.setLore(lore);
         if (selected) {
            meta.setEnchantmentGlintOverride(true);
         }

         meta.addItemFlags(ItemFlag.values());
         stack.setItemMeta(meta);
      }

      return stack;
   }

   private ItemStack infoItem(PendingTrim state) {
      ItemStack stack = new ItemStack(Material.ITEM_FRAME);
      ItemMeta meta = stack.getItemMeta();
      if (meta != null) {
         meta.setDisplayName(ColorUtil.color("&b&lТекущий выбор"));
         String pattern = state.patternKey == null ? "&8—" : "&f" + prettyFromKey(state.patternKey);
         String material = state.materialKey == null ? "&8—" : "&f" + prettyFromKey(state.materialKey);
         meta.setLore(List.of(
            ColorUtil.color("&7Паттерн: " + pattern),
            ColorUtil.color("&7Материал: " + material),
            "",
            ColorUtil.color("&7Нужны оба, затем &aСохранить")
         ));
         stack.setItemMeta(meta);
      }

      return stack;
   }

   private static ItemStack button(Material material, String name, List<String> lore) {
      ItemStack stack = new ItemStack(material);
      ItemMeta meta = stack.getItemMeta();
      if (meta != null) {
         meta.setDisplayName(ColorUtil.color(name));
         meta.setLore(lore.stream().map(ColorUtil::color).toList());
         stack.setItemMeta(meta);
      }

      return stack;
   }

   private static ItemStack filler() {
      ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
      ItemMeta meta = pane.getItemMeta();
      if (meta != null) {
         meta.setDisplayName(" ");
         pane.setItemMeta(meta);
      }

      return pane;
   }

   private static String prettyFromKey(String rawKey) {
      String key = rawKey;
      int colon = key.indexOf(58);
      if (colon >= 0 && colon + 1 < key.length()) {
         key = key.substring(colon + 1);
      }

      NamespacedKey nk = NamespacedKey.minecraft(key.toLowerCase(Locale.ROOT));
      return TrimService.prettyName(nk);
   }

   private static boolean matches(NamespacedKey key, String stored) {
      if (stored == null) {
         return false;
      }

      String s = stored.trim();
      return s.equalsIgnoreCase(key.toString()) || s.equalsIgnoreCase(key.getKey());
   }

   private static final class PendingTrim {
      private final String kitName;
      private String patternKey;
      private String materialKey;
      private final Map<Integer, TrimPattern> patternSlots = new HashMap<>();
      private final Map<Integer, TrimMaterial> materialSlots = new HashMap<>();

      private PendingTrim(String kitName) {
         this.kitName = kitName;
      }
   }

   public static record TrimsHolder(String kitName) implements InventoryHolder {
      public Inventory getInventory() {
         return null;
      }
   }
}
