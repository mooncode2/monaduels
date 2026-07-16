package org.Mona.monaDuels.listener;

import org.Mona.monaDuels.config.MessageService;
import org.Mona.monaDuels.duel.DuelManager;
import org.Mona.monaDuels.queue.PlayAgainService;
import org.Mona.monaDuels.queue.QueueManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class PlayAgainListener implements Listener {
   private final PlayAgainService playAgainService;
   private final QueueManager queueManager;
   private final DuelManager duelManager;
   private final MessageService messages;

   public PlayAgainListener(PlayAgainService playAgainService, QueueManager queueManager, DuelManager duelManager, MessageService messages) {
      this.playAgainService = playAgainService;
      this.queueManager = queueManager;
      this.duelManager = duelManager;
      this.messages = messages;
   }

   @EventHandler(
      priority = EventPriority.HIGH
   )
   public void onInteract(PlayerInteractEvent event) {
      if (event.getHand() != EquipmentSlot.HAND) {
         return;
      }

      Action action = event.getAction();
      if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
         return;
      }

      ItemStack item = event.getItem();
      if (item == null || !this.playAgainService.isPlayAgain(item)) {
         return;
      }

      event.setCancelled(true);
      Player player = event.getPlayer();
      if (!this.duelManager.isInDuel(player.getUniqueId())) {
         this.queueManager.enqueue(player, this.playAgainService.kitOf(item));
      } else if (this.duelManager.isPostFight(player.getUniqueId())) {
         // Clicked on the arena during the celebration stage — queue as soon as we're back.
         this.playAgainService.markPending(player.getUniqueId());
         this.messages.send(player, "queue.play-again-pending");
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      this.queueManager.clearFor(event.getPlayer().getUniqueId());
      this.playAgainService.clearFor(event.getPlayer().getUniqueId());
   }
}
