package org.Mona.monaDuels.gui;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.Mona.monaDuels.MonaDuels;
import org.Mona.monaDuels.config.ConfigManager;
import org.Mona.monaDuels.config.MessageService;
import org.Mona.monaDuels.duel.DuelManager;
import org.Mona.monaDuels.kit.Kit;
import org.Mona.monaDuels.kit.KitManager;
import org.Mona.monaDuels.player.PlayerDataManager;
import org.Mona.monaDuels.player.PlayerSnapshot;
import org.Mona.monaDuels.queue.QueueManager;
import org.Mona.monaDuels.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Layout editor (Rev. 2, per ТЗ screenshots): a 3-row chest holds ONLY the control buttons
 * (save / reset / cancel); the kit items are loaded into the player's REAL inventory shown in the
 * bottom half of the view. Safety: the player's original inventory is snapshotted on entry and is
 * ALWAYS restored on close/quit — the kit items are disposable copies, so even a creative
 * inventory-clear cannot lose anything.
 */
public final class KitLayoutEditorService {
   public static final int SIZE = 27;
   public static final int SAVE_SLOT = 11;
   public static final int RESET_SLOT = 13;
   public static final int CANCEL_SLOT = 16;
   private final MonaDuels plugin;
   private final ConfigManager config;
   private final KitManager kitManager;
   private final PlayerDataManager playerData;
   private final MessageService messages;
   private final DuelManager duelManager;
   private QueueManager queueManager;
   private KitPreviewService previewService;
   private final Map<UUID, PlayerSnapshot> snapshots = new ConcurrentHashMap<>();
   private final Map<UUID, String> editingKit = new ConcurrentHashMap<>();

   public KitLayoutEditorService(
      MonaDuels plugin,
      ConfigManager config,
      KitManager kitManager,
      PlayerDataManager playerData,
      MessageService messages,
      DuelManager duelManager
   ) {
      this.plugin = plugin;
      this.config = config;
      this.kitManager = kitManager;
      this.playerData = playerData;
      this.messages = messages;
      this.duelManager = duelManager;
   }

   public void bindQueue(QueueManager queueManager) {
      this.queueManager = queueManager;
   }

   public void bindPreview(KitPreviewService previewService) {
      this.previewService = previewService;
   }

   public boolean isEditing(UUID id) {
      return this.editingKit.containsKey(id);
   }

   public static boolean isControlSlot(int slot) {
      return slot == SAVE_SLOT || slot == RESET_SLOT || slot == CANCEL_SLOT;
   }

   public void enterEditMode(Player player, String kitName) {
      UUID id = player.getUniqueId();
      Kit kit = this.kitManager.find(kitName).orElse(null);
      if (kit == null) {
         this.messages.send(player, "kit.not-found", Map.of("kit", kitName));
         return;
      }

      if (this.duelManager.isInDuel(id) || this.queueManager != null && this.queueManager.isQueued(id)) {
         this.messages.send(player, "kit-editor.busy");
         return;
      }

      if (this.editingKit.containsKey(id)) {
         return;
      }

      // Cache the original inventory FIRST — it is restored on every exit path.
      this.snapshots.put(id, PlayerSnapshot.capture(player));
      this.editingKit.put(id, kit.name());
      this.loadKitIntoInventory(player, kit);
      player.openInventory(this.buildControlChest(kit));
      this.messages.send(player, "kit-editor.opened", Map.of("kit", kit.displayName()));
   }

   /** Save button: persist the arrangement from the REAL inventory, then restore the original one. */
   public void save(Player player) {
      UUID id = player.getUniqueId();
      String kitKey = this.editingKit.remove(id);
      PlayerSnapshot snapshot = this.snapshots.remove(id);
      if (kitKey == null) {
         return;
      }

      PlayerInventory inv = player.getInventory();
      this.playerData.setKitLayout(id, kitKey, inv.getStorageContents(), inv.getArmorContents(), cleanOffhand(inv.getItemInOffHand()));
      if (snapshot != null) {
         snapshot.restoreInventoryOnly(player);
      }

      this.messages.send(player, "kit-editor.saved", Map.of("kit", kitKey));
      this.reopenPreview(player, kitKey);
   }

   /** Reset button: reload the kit defaults into the real inventory (persisted only via Save). */
   public void reset(Player player) {
      String kitKey = this.editingKit.get(player.getUniqueId());
      if (kitKey != null) {
         Kit kit = this.kitManager.find(kitKey).orElse(null);
         if (kit != null) {
            this.applyContents(player, kit.inventory(), kit.armor(), kit.offhand());
            this.messages.send(player, "kit-editor.reset", Map.of("kit", kitKey));
         }
      }
   }

   /** Cancel button: discard changes, restore the original inventory, return to the preview. */
   public void cancel(Player player) {
      UUID id = player.getUniqueId();
      String kitKey = this.editingKit.remove(id);
      PlayerSnapshot snapshot = this.snapshots.remove(id);
      if (kitKey != null) {
         if (snapshot != null) {
            snapshot.restoreInventoryOnly(player);
         }

         this.messages.send(player, "kit-editor.cancelled");
         this.reopenPreview(player, kitKey);
      }
   }

   /** Plain close (Esc): discard changes and restore the original inventory. */
   public void handleClose(Player player) {
      UUID id = player.getUniqueId();
      if (this.editingKit.remove(id) != null) {
         PlayerSnapshot snapshot = this.snapshots.remove(id);
         if (snapshot != null) {
            snapshot.restoreInventoryOnly(player);
         }
      }
   }

   public void handleQuit(Player player) {
      this.handleClose(player);
   }

   public void shutdown() {
      for (UUID id : new HashSet<>(this.editingKit.keySet())) {
         Player player = this.plugin.getServer().getPlayer(id);
         if (player != null) {
            this.handleClose(player);
            player.closeInventory();
         } else {
            this.editingKit.remove(id);
            this.snapshots.remove(id);
         }
      }
   }

   private void reopenPreview(Player player, String kitKey) {
      this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
         if (this.previewService != null && player.isOnline()) {
            this.previewService.open(player, kitKey);
         } else if (player.isOnline()) {
            player.closeInventory();
         }
      });
   }

   private void loadKitIntoInventory(Player player, Kit kit) {
      UUID id = player.getUniqueId();
      ItemStack[] storage = null;
      ItemStack[] armor = null;
      ItemStack offhand = null;
      if (this.playerData.hasKitLayout(id, kit.name())) {
         storage = this.playerData.getKitLayoutInventory(id, kit.name());
         armor = this.playerData.getKitLayoutArmor(id, kit.name());
         offhand = this.playerData.getKitLayoutOffhand(id, kit.name());
      }

      this.applyContents(
         player,
         storage != null ? storage : kit.inventory(),
         armor != null ? armor : kit.armor(),
         offhand != null ? offhand : kit.offhand()
      );
   }

   private void applyContents(Player player, ItemStack[] storage, ItemStack[] armor, ItemStack offhand) {
      PlayerInventory inv = player.getInventory();
      inv.clear();
      inv.setStorageContents(cloneArray(storage, inv.getStorageContents().length));
      inv.setArmorContents(cloneArray(armor, 4));
      inv.setItemInOffHand(offhand == null ? new ItemStack(Material.AIR) : offhand.clone());
      player.updateInventory();
   }

   private Inventory buildControlChest(Kit kit) {
      Inventory inv = Bukkit.createInventory(new KitLayoutEditorService.KitEditorHolder(kit.name()), SIZE, ColorUtil.color("&8Раскладка: " + kit.displayName()));
      ItemStack filler = filler();

      for (int i = 0; i < SIZE; i++) {
         inv.setItem(i, filler);
      }

      inv.setItem(SAVE_SLOT, this.button(Material.TURTLE_HELMET, "kit-editor.save", "&a&lСохранить раскладку",
         List.of("&7Сохранить текущее расположение", "&7предметов набора.", "", "&eF &7— предмет во вторую руку")));
      inv.setItem(RESET_SLOT, this.button(Material.SPECTRAL_ARROW, "kit-editor.reset", "&e&lСбросить раскладку",
         List.of("&7Вернуть предметы к", "&7значениям набора по умолчанию.")));
      inv.setItem(CANCEL_SLOT, this.button(Material.BARRIER, "kit-editor.cancel", "&c&lОтмена",
         List.of("&7Отменить изменения и", "&7вернуться к предпросмотру.")));
      return inv;
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

   private static ItemStack filler() {
      ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
      ItemMeta meta = pane.getItemMeta();
      if (meta != null) {
         meta.setDisplayName(" ");
         pane.setItemMeta(meta);
      }

      return pane;
   }

   private static ItemStack cleanOffhand(ItemStack offhand) {
      return offhand != null && offhand.getType() != Material.AIR ? offhand : null;
   }

   private static ItemStack[] cloneArray(ItemStack[] source, int size) {
      ItemStack[] result = new ItemStack[size];
      if (source != null) {
         for (int i = 0; i < Math.min(source.length, size); i++) {
            result[i] = source[i] == null ? null : source[i].clone();
         }
      }

      return result;
   }

   public static record KitEditorHolder(String kitName) implements InventoryHolder {
      public Inventory getInventory() {
         return null;
      }
   }
}
