package org.Mona.monaDuels.listener;

import org.Mona.monaDuels.gui.CelebrationMenuService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class CelebrationMenuListener implements Listener {
   private final CelebrationMenuService celebrationMenu;

   public CelebrationMenuListener(CelebrationMenuService celebrationMenu) {
      this.celebrationMenu = celebrationMenu;
   }

   @EventHandler(
      priority = EventPriority.HIGH
   )
   public void onClick(InventoryClickEvent event) {
      if (event.getWhoClicked() instanceof Player player
         && event.getInventory().getHolder() instanceof CelebrationMenuService.CelebrationHolder holder) {
         event.setCancelled(true);
         int size = event.getView().getTopInventory().getSize();
         if (event.getRawSlot() >= 0 && event.getRawSlot() < size) {
            this.celebrationMenu.handleClick(player, event.getRawSlot(), holder.kitName(), size);
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH
   )
   public void onDrag(InventoryDragEvent event) {
      if (event.getInventory().getHolder() instanceof CelebrationMenuService.CelebrationHolder) {
         event.setCancelled(true);
      }
   }

   @EventHandler
   public void onClose(InventoryCloseEvent event) {
      if (event.getPlayer() instanceof Player player && event.getInventory().getHolder() instanceof CelebrationMenuService.CelebrationHolder) {
         this.celebrationMenu.handleClose(player);
      }
   }
}
