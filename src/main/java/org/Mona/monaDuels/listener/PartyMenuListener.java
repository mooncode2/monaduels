package org.Mona.monaDuels.listener;

import org.Mona.monaDuels.gui.PartyMenuService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public final class PartyMenuListener implements Listener {
   private final PartyMenuService partyMenu;

   public PartyMenuListener(PartyMenuService partyMenu) {
      this.partyMenu = partyMenu;
   }

   @EventHandler
   public void onClick(InventoryClickEvent event) {
      if (event.getWhoClicked() instanceof Player player) {
         if (event.getInventory().getHolder() instanceof PartyMenuService.PartyMenuHolder holder) {
            event.setCancelled(true);
            if (holder.type() == PartyMenuService.MenuType.INCOMING) {
               this.partyMenu.handleIncomingAction(player, event.getRawSlot());
            } else {
               ItemStack item = event.getCurrentItem();
               this.partyMenu.handleClick(player, holder.type(), event.getRawSlot(), item);
            }
         }
      }
   }
}
