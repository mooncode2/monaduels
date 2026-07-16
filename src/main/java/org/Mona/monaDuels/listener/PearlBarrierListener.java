package org.Mona.monaDuels.listener;

import org.Mona.monaDuels.duel.DuelManager;
import org.Mona.monaDuels.duel.DuelState;
import org.Mona.monaDuels.team.TeamDuelManager;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.util.Vector;

public final class PearlBarrierListener implements Listener {
   private final DuelManager duelManager;
   private final TeamDuelManager teamDuelManager;

   public PearlBarrierListener(DuelManager duelManager, TeamDuelManager teamDuelManager) {
      this.duelManager = duelManager;
      this.teamDuelManager = teamDuelManager;
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onPearlHit(ProjectileHitEvent event) {
      if (event.getEntity() instanceof EnderPearl pearl) {
         if (pearl.getShooter() instanceof Player player) {
            Block hitBlock = event.getHitBlock();
            if (hitBlock != null && hitBlock.getType() == Material.BARRIER) {
               if (this.teamDuelManager.getSession(player.getUniqueId()).filter(session -> session.state() == DuelState.ACTIVE).isPresent()) {
                  bouncePearl(event, pearl);
               } else {
                  this.duelManager.getSession(player.getUniqueId()).ifPresent(session -> {
                     if (session.state() == DuelState.ACTIVE) {
                        bouncePearl(event, pearl);
                     }
                  });
               }
            }
         }
      }
   }

   private static void bouncePearl(ProjectileHitEvent event, EnderPearl pearl) {
      Vector velocity = pearl.getVelocity();
      if (!(velocity.lengthSquared() < 1.0E-6)) {
         BlockFace face = event.getHitBlockFace();
         if (face != null) {
            Vector offset = face.getOppositeFace().getDirection().multiply(0.35);
            pearl.teleport(pearl.getLocation().add(offset));
         }

         pearl.setVelocity(velocity.multiply(-1.0));
      }
   }
}
