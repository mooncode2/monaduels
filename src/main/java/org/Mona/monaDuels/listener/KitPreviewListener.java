package org.Mona.monaDuels.listener;

import org.Mona.monaDuels.gui.KitPreviewService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class KitPreviewListener implements Listener {
   private final KitPreviewService previewService;

   public KitPreviewListener(KitPreviewService previewService) {
      this.previewService = previewService;
   }

   @EventHandler(
      priority = EventPriority.HIGH
   )
   public void onClick(InventoryClickEvent event) {
      if (event.getInventory().getHolder() instanceof KitPreviewService.KitPreviewHolder holder) {
         event.setCancelled(true);
         if (event.getWhoClicked() instanceof Player player) {
            switch (event.getRawSlot()) {
               case KitPreviewService.BOOK_SLOT:
                  this.previewService.onBookClicked(player, holder.kitName());
                  break;
               case KitPreviewService.SHERD_SLOT:
                  this.previewService.onSherdClicked(player, holder.kitName());
                  break;
               case KitPreviewService.CELEBRATION_SLOT:
                  this.previewService.onCelebrationClicked(player, holder.kitName());
                  break;
               case KitPreviewService.BACK_SLOT:
                  this.previewService.onBackClicked(player);
                  break;
               case KitPreviewService.CLOSE_SLOT:
                  player.closeInventory();
                  break;
               default:
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH
   )
   public void onDrag(InventoryDragEvent event) {
      if (event.getInventory().getHolder() instanceof KitPreviewService.KitPreviewHolder) {
         event.setCancelled(true);
      }
   }
}
