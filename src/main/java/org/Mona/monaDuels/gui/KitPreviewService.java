package org.Mona.monaDuels.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.Mona.monaDuels.MonaDuels;
import org.Mona.monaDuels.config.ConfigManager;
import org.Mona.monaDuels.config.MessageService;
import org.Mona.monaDuels.kit.Kit;
import org.Mona.monaDuels.kit.KitManager;
import org.Mona.monaDuels.player.PlayerDataManager;
import org.Mona.monaDuels.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;

public final class KitPreviewService {
   public static final int EFFECTS_SLOT = 4;
   public static final int OFFHAND_SLOT = 5;
   public static final int BOOK_SLOT = 45;
   public static final int SHERD_SLOT = 47;
   public static final int CELEBRATION_SLOT = 49;
   public static final int BACK_SLOT = 51;
   public static final int CLOSE_SLOT = 53;
   private final MonaDuels plugin;
   private final ConfigManager config;
   private final KitManager kitManager;
   private final PlayerDataManager playerData;
   private final MessageService messages;
   private KitLayoutEditorService editorService;
   private TrimsService trimsService;
   private CelebrationMenuService celebrationMenu;
   private KitSelectionService kitSelection;

   public KitPreviewService(MonaDuels plugin, ConfigManager config, KitManager kitManager, PlayerDataManager playerData, MessageService messages) {
      this.plugin = plugin;
      this.config = config;
      this.kitManager = kitManager;
      this.playerData = playerData;
      this.messages = messages;
   }

   public void bindEditor(KitLayoutEditorService editorService) {
      this.editorService = editorService;
   }

   public void bindTrims(TrimsService trimsService) {
      this.trimsService = trimsService;
   }

   public void bindCelebrationMenu(CelebrationMenuService celebrationMenu) {
      this.celebrationMenu = celebrationMenu;
   }

   public void bindKitSelection(KitSelectionService kitSelection) {
      this.kitSelection = kitSelection;
   }

   public void open(Player viewer, String kitName) {
      Kit kit = this.kitManager.find(kitName).orElse(null);
      if (kit == null) {
         this.messages.send(viewer, "kit.not-found", Map.of("kit", kitName));
         return;
      }

      Inventory inv = Bukkit.createInventory(new KitPreviewService.KitPreviewHolder(kit.name()), 54, ColorUtil.color("&8Просмотр: " + kit.displayName()));
      ItemStack filler = filler();

      for (int i = 0; i < 54; i++) {
         inv.setItem(i, filler);
      }

      ItemStack[] storage = this.resolveInventory(viewer, kit);
      ItemStack[] armor = cloneArmor(this.resolveArmor(viewer, kit));
      ItemStack offhand = this.resolveOffhand(viewer, kit);
      if (this.trimsService != null) {
         this.trimsService.applyPreviewTrim(viewer, kit.name(), armor);
      }

      inv.setItem(0, armor.length > 3 ? armor[3] : null);
      inv.setItem(1, armor.length > 2 ? armor[2] : null);
      inv.setItem(2, armor.length > 1 ? armor[1] : null);
      inv.setItem(3, armor.length > 0 ? armor[0] : null);
      inv.setItem(EFFECTS_SLOT, effectsSummary(kit));
      inv.setItem(OFFHAND_SLOT, offhand);

      for (int i = 9; i < 36; i++) {
         inv.setItem(i, storage != null && i < storage.length ? storage[i] : null);
      }

      for (int i = 0; i < 9; i++) {
         inv.setItem(36 + i, storage != null && i < storage.length ? storage[i] : null);
      }

      inv.setItem(BOOK_SLOT, this.button(Material.WRITABLE_BOOK, "kit-preview.book", "&a&lРедактировать раскладку",
         List.of("&7Настройте расположение предметов", "&7этого набора под себя.", "", "&eНажмите, чтобы редактировать")));
      if (this.trimsService != null && this.trimsService.isEnabled()) {
         ItemStack sherd = this.button(Material.BLADE_POTTERY_SHERD, "kit-preview.sherd", "&d&lШаблоны брони",
            List.of("&7Косметические отделки (Trims)", "&7на броню набора.", "", "&eНажмите, чтобы выбрать"));
         inv.setItem(SHERD_SLOT, this.trimsService.canOpen(viewer) ? sherd : locked(sherd));
      }

      if (this.celebrationMenu != null) {
         ItemStack celebration = this.button(Material.FIREWORK_ROCKET, "kit-preview.celebration", "&6&lПразднование",
            List.of("&7Эффект на месте убийства", "&7для этого набора.", "", "&eНажмите, чтобы выбрать"));
         inv.setItem(CELEBRATION_SLOT, this.celebrationMenu.canOpen(viewer) ? celebration : locked(celebration));
      }

      inv.setItem(BACK_SLOT, this.button(Material.ARROW, "kit-preview.back", "&e&lНазад", List.of("&7К выбору наборов")));
      inv.setItem(CLOSE_SLOT, this.button(Material.BARRIER, "kit-preview.close", "&c&lЗакрыть", List.of("&7Закрыть меню")));
      viewer.openInventory(inv);
   }

   public void onBookClicked(Player player, String kitName) {
      player.closeInventory();
      if (this.editorService != null) {
         this.plugin.getServer().getScheduler().runTask(this.plugin, () -> this.editorService.enterEditMode(player, kitName));
      }
   }

   public void onSherdClicked(Player player, String kitName) {
      if (this.trimsService == null || !this.trimsService.isEnabled() || !this.trimsService.canOpen(player)) {
         this.messages.send(player, "trims.locked");
         return;
      }

      player.closeInventory();
      this.plugin.getServer().getScheduler().runTask(this.plugin, () -> this.trimsService.open(player, kitName));
   }

   public void onCelebrationClicked(Player player, String kitName) {
      if (this.celebrationMenu == null || !this.celebrationMenu.canOpen(player)) {
         this.messages.send(player, "celebration.no-access");
         return;
      }

      player.closeInventory();
      this.plugin.getServer().getScheduler().runTask(this.plugin, () -> this.celebrationMenu.open(player, kitName));
   }

   public void onBackClicked(Player player) {
      player.closeInventory();
      if (this.kitSelection != null) {
         this.plugin.getServer().getScheduler().runTask(this.plugin, () -> this.kitSelection.openMatchmakingMenu(player));
      }
   }

   private ItemStack[] resolveInventory(Player viewer, Kit kit) {
      if (this.playerData.hasKitLayout(viewer.getUniqueId(), kit.name())) {
         ItemStack[] custom = this.playerData.getKitLayoutInventory(viewer.getUniqueId(), kit.name());
         if (custom != null) {
            return custom;
         }
      }

      return kit.inventory();
   }

   private ItemStack[] resolveArmor(Player viewer, Kit kit) {
      if (this.playerData.hasKitLayout(viewer.getUniqueId(), kit.name())) {
         ItemStack[] custom = this.playerData.getKitLayoutArmor(viewer.getUniqueId(), kit.name());
         if (custom != null) {
            return custom;
         }
      }

      return kit.armor();
   }

   private ItemStack resolveOffhand(Player viewer, Kit kit) {
      if (this.playerData.hasKitLayout(viewer.getUniqueId(), kit.name())) {
         return this.playerData.getKitLayoutOffhand(viewer.getUniqueId(), kit.name());
      }

      return kit.offhand();
   }

   private ItemStack button(Material material, String path, String defName, List<String> defLore) {
      ItemStack stack = new ItemStack(material);
      ItemMeta meta = stack.getItemMeta();
      if (meta != null) {
         meta.setDisplayName(ColorUtil.color(this.config.uiName(path, defName)));
         meta.setLore(this.config.uiLore(path, defLore).stream().map(ColorUtil::color).toList());
         stack.setItemMeta(meta);
      }

      return stack;
   }

   private static ItemStack locked(ItemStack source) {
      ItemMeta meta = source.getItemMeta();
      if (meta != null) {
         List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
         lore.add(ColorUtil.color("&c🔒 Только для группы monaprime"));
         meta.setLore(lore);
         source.setItemMeta(meta);
      }

      return source;
   }

   private static ItemStack[] cloneArmor(ItemStack[] source) {
      ItemStack[] result = new ItemStack[4];
      if (source != null) {
         for (int i = 0; i < Math.min(source.length, 4); i++) {
            result[i] = source[i] == null ? null : source[i].clone();
         }
      }

      return result;
   }

   private static ItemStack effectsSummary(Kit kit) {
      List<PotionEffect> effects = kit.effects();
      if (effects == null || effects.isEmpty()) {
         return filler();
      }

      ItemStack stack = new ItemStack(Material.POTION);
      ItemMeta meta = stack.getItemMeta();
      if (meta != null) {
         meta.setDisplayName(ColorUtil.color("&d&lЭффекты набора"));
         List<String> lore = new ArrayList<>();

         for (PotionEffect effect : effects) {
            lore.add(ColorUtil.color("&7• &f" + effect.getType().getKey().getKey() + " &7" + (effect.getAmplifier() + 1)));
         }

         meta.setLore(lore);
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

   public static record KitPreviewHolder(String kitName) implements InventoryHolder {
      public Inventory getInventory() {
         return null;
      }
   }
}
