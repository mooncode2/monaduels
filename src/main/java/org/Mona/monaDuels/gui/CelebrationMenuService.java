package org.Mona.monaDuels.gui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.Mona.monaDuels.MonaDuels;
import org.Mona.monaDuels.celebration.CelebrationService;
import org.Mona.monaDuels.config.MessageService;
import org.Mona.monaDuels.kit.Kit;
import org.Mona.monaDuels.kit.KitManager;
import org.Mona.monaDuels.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Per-kit celebration picker (Rev. 2). Opened from the Kit Preview GUI, gated to monaprime
 * ({@link CelebrationService#canAccess}). Includes «Применить ко всем» to copy the choice to all kits.
 */
public final class CelebrationMenuService {
   private final MonaDuels plugin;
   private final CelebrationService celebrationService;
   private final KitManager kitManager;
   private final MessageService messages;
   private KitPreviewService previewService;
   private final Map<UUID, Map<Integer, String>> openMenus = new ConcurrentHashMap<>();

   public CelebrationMenuService(MonaDuels plugin, CelebrationService celebrationService, KitManager kitManager, MessageService messages) {
      this.plugin = plugin;
      this.celebrationService = celebrationService;
      this.kitManager = kitManager;
      this.messages = messages;
   }

   public void bindPreview(KitPreviewService previewService) {
      this.previewService = previewService;
   }

   public boolean canOpen(Player player) {
      return this.celebrationService.canAccess(player);
   }

   public void open(Player player, String kitName) {
      if (!this.celebrationService.canAccess(player)) {
         this.messages.send(player, "celebration.no-access");
         return;
      }

      Kit kit = this.kitManager.find(kitName).orElse(null);
      if (kit == null) {
         this.messages.send(player, "kit.not-found", Map.of("kit", kitName));
         return;
      }

      String current = this.celebrationService.currentCelebrationForKit(player, kit.name());
      Collection<CelebrationService.EffectDef> effects = this.celebrationService.allEffects();
      int size = effects.size() <= 7 ? 36 : 54;
      Inventory inv = Bukkit.createInventory(
         new CelebrationMenuService.CelebrationHolder(kit.name()), size, ColorUtil.color("&8Празднование &8· " + kit.displayName())
      );
      ItemStack filler = filler();

      for (int i = 0; i < size; i++) {
         inv.setItem(i, filler);
      }

      List<Integer> slots = innerSlots(size);
      Map<Integer, String> slotToId = new HashMap<>();
      int idx = 0;

      for (CelebrationService.EffectDef def : effects) {
         if (idx >= slots.size()) {
            break;
         }

         int slot = slots.get(idx++);
         boolean selected = def.id().equalsIgnoreCase(current);
         boolean unlocked = this.celebrationService.canUse(player, def.id());
         inv.setItem(slot, icon(def, selected, unlocked));
         slotToId.put(slot, def.id());
      }

      int bottomRow = size - 9;
      inv.setItem(bottomRow + 3, button(Material.NETHER_STAR, "&b&lПрименить ко всем",
         List.of("&7Задать текущий эффект этого набора", "&7для всех наборов сразу")));
      inv.setItem(bottomRow + 5, button(Material.ARROW, "&e&lНазад", List.of("&7К предпросмотру набора")));
      this.openMenus.put(player.getUniqueId(), slotToId);
      player.openInventory(inv);
   }

   public boolean handleClick(Player player, int slot, String kitName, int inventorySize) {
      int bottomRow = inventorySize - 9;
      if (slot == bottomRow + 3) {
         this.applyToAllKits(player, kitName);
         return true;
      }

      if (slot == bottomRow + 5) {
         this.backToPreview(player, kitName);
         return true;
      }

      Map<Integer, String> slotToId = this.openMenus.get(player.getUniqueId());
      if (slotToId == null) {
         return false;
      }

      String id = slotToId.get(slot);
      if (id == null) {
         return false;
      }

      if (this.celebrationService.setCelebrationForKit(player, kitName, id)) {
         this.open(player, kitName);
      }

      return true;
   }

   public void handleClose(Player player) {
      this.openMenus.remove(player.getUniqueId());
   }

   private void applyToAllKits(Player player, String kitName) {
      String current = this.celebrationService.currentCelebrationForKit(player, kitName);
      List<String> kitNames = this.kitManager.all().stream().map(Kit::name).toList();
      if (this.celebrationService.applyToKits(player, kitNames, current)) {
         this.messages.send(player, "celebration.applied-all", Map.of("celebration", this.celebrationService.displayName(current)));
         this.open(player, kitName);
      } else {
         this.messages.send(player, "celebration.locked", Map.of("celebration", this.celebrationService.displayName(current)));
      }
   }

   private void backToPreview(Player player, String kitName) {
      this.openMenus.remove(player.getUniqueId());
      this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
         if (this.previewService != null && player.isOnline()) {
            this.previewService.open(player, kitName);
         } else if (player.isOnline()) {
            player.closeInventory();
         }
      });
   }

   private static ItemStack icon(CelebrationService.EffectDef def, boolean selected, boolean unlocked) {
      ItemStack stack = new ItemStack(def.icon());
      ItemMeta meta = stack.getItemMeta();
      if (meta != null) {
         String prefix = selected ? "&a✓ " : (unlocked ? "&f" : "&7");
         meta.setDisplayName(ColorUtil.color(prefix + def.displayName()));
         List<String> lore = new ArrayList<>();
         if (!unlocked) {
            lore.add(ColorUtil.color("&cТребуется группа: &f" + (def.group() != null && !def.group().isBlank() ? def.group() : "особый доступ")));
            lore.add(ColorUtil.color("&7Недоступно для вашей роли"));
         } else if (selected) {
            lore.add(ColorUtil.color("&aВыбрано для этого набора"));
         } else {
            lore.add(ColorUtil.color("&eНажмите, чтобы выбрать"));
         }

         meta.setLore(lore);
         if (selected) {
            meta.setEnchantmentGlintOverride(true);
         }

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

   private static List<Integer> innerSlots(int size) {
      List<Integer> slots = new ArrayList<>();
      int rows = size / 9;

      for (int row = 1; row < rows - 1; row++) {
         for (int col = 1; col <= 7; col++) {
            slots.add(row * 9 + col);
         }
      }

      return slots;
   }

   public static record CelebrationHolder(String kitName) implements InventoryHolder {
      public Inventory getInventory() {
         return null;
      }
   }
}
