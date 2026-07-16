package org.Mona.monaDuels.listener;

import org.Mona.monaDuels.gui.KitLayoutEditorService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class KitLayoutEditorListener implements Listener {
   private final KitLayoutEditorService editor;

   public KitLayoutEditorListener(KitLayoutEditorService editor) {
      this.editor = editor;
   }

   @EventHandler(
      priority = EventPriority.HIGH
   )
   public void onClick(InventoryClickEvent event) {
      if (!(event.getWhoClicked() instanceof Player player) || !this.editor.isEditing(player.getUniqueId())) {
         return;
      }

      // While editing, any other GUI is off-limits.
      if (!(event.getView().getTopInventory().getHolder() instanceof KitLayoutEditorService.KitEditorHolder)) {
         event.setCancelled(true);
         return;
      }

      int topSize = event.getView().getTopInventory().getSize();
      int raw = event.getRawSlot();

      // Top chest: only the three control buttons react; nothing may be placed or taken there.
      if (raw >= 0 && raw < topSize) {
         event.setCancelled(true);
         if (KitLayoutEditorService.isControlSlot(raw)) {
            // Return whatever is on the cursor into the kit layout is impossible now — it is a
            // disposable copy; drop it so it cannot leak into the restored inventory.
            event.getView().setCursor(null);
            if (raw == KitLayoutEditorService.SAVE_SLOT) {
               this.editor.save(player);
            } else if (raw == KitLayoutEditorService.RESET_SLOT) {
               this.editor.reset(player);
            } else if (raw == KitLayoutEditorService.CANCEL_SLOT) {
               this.editor.cancel(player);
            }
         }

         return;
      }

      // Bottom (real) inventory: free rearranging, but nothing may leave it.
      InventoryAction action = event.getAction();
      if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY
         || action == InventoryAction.COLLECT_TO_CURSOR
         || action == InventoryAction.DROP_ALL_SLOT
         || action == InventoryAction.DROP_ONE_SLOT
         || action == InventoryAction.DROP_ALL_CURSOR
         || action == InventoryAction.DROP_ONE_CURSOR) {
         event.setCancelled(true);
      }
      // SWAP_OFFHAND (F) on bottom slots is allowed on purpose — that is how the off-hand is set.
   }

   @EventHandler(
      priority = EventPriority.HIGH
   )
   public void onDrag(InventoryDragEvent event) {
      if (event.getWhoClicked() instanceof Player player && this.editor.isEditing(player.getUniqueId())) {
         if (!(event.getView().getTopInventory().getHolder() instanceof KitLayoutEditorService.KitEditorHolder)) {
            event.setCancelled(true);
            return;
         }

         int topSize = event.getView().getTopInventory().getSize();

         for (int raw : event.getRawSlots()) {
            if (raw < topSize) {
               event.setCancelled(true);
               return;
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH
   )
   public void onDrop(PlayerDropItemEvent event) {
      if (this.editor.isEditing(event.getPlayer().getUniqueId())) {
         event.setCancelled(true);
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH
   )
   public void onPickup(EntityPickupItemEvent event) {
      if (event.getEntity() instanceof Player player && this.editor.isEditing(player.getUniqueId())) {
         event.setCancelled(true);
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH
   )
   public void onInteract(PlayerInteractEvent event) {
      if (this.editor.isEditing(event.getPlayer().getUniqueId())) {
         event.setCancelled(true);
      }
   }

   @EventHandler
   public void onClose(InventoryCloseEvent event) {
      if (event.getPlayer() instanceof Player player && event.getInventory().getHolder() instanceof KitLayoutEditorService.KitEditorHolder) {
         // A kit-item copy on the cursor must not be returned into the restored inventory.
         event.getView().setCursor(null);
         this.editor.handleClose(player);
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      this.editor.handleQuit(event.getPlayer());
   }
}
