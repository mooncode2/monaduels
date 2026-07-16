package org.Mona.monaDuels.listener;

import org.Mona.monaDuels.gui.TrimsService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class TrimsListener implements Listener {
   private final TrimsService trimsService;

   public TrimsListener(TrimsService trimsService) {
      this.trimsService = trimsService;
   }

   @EventHandler(
      priority = EventPriority.HIGH
   )
   public void onClick(InventoryClickEvent event) {
      if (event.getInventory().getHolder() instanceof TrimsService.TrimsHolder) {
         event.setCancelled(true);
         if (event.getWhoClicked() instanceof Player player) {
            this.trimsService.handleClick(player, event.getRawSlot(), event.getView().getTopInventory());
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH
   )
   public void onDrag(InventoryDragEvent event) {
      if (event.getInventory().getHolder() instanceof TrimsService.TrimsHolder) {
         event.setCancelled(true);
      }
   }

   @EventHandler
   public void onClose(InventoryCloseEvent event) {
      if (event.getPlayer() instanceof Player player && event.getInventory().getHolder() instanceof TrimsService.TrimsHolder) {
         this.trimsService.handleClose(player);
      }
   }
}
