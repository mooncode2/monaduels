package org.Mona.monaDuels.listener;

import org.Mona.monaDuels.config.ConfigManager;
import org.Mona.monaDuels.duel.DuelManager;
import org.Mona.monaDuels.duel.DuelState;
import org.Mona.monaDuels.team.TeamDuelManager;
import org.Mona.monaDuels.team.TeamDuelSession;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public final class DuelBlockListener implements Listener {
   private final ConfigManager config;
   private final DuelManager duelManager;
   private final TeamDuelManager teamDuelManager;

   public DuelBlockListener(ConfigManager config, DuelManager duelManager, TeamDuelManager teamDuelManager) {
      this.config = config;
      this.duelManager = duelManager;
      this.teamDuelManager = teamDuelManager;
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onPlace(BlockPlaceEvent event) {
      if (this.config.trackPlacedBlocks()) {
         this.track(event.getPlayer(), event.getBlock(), true);
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onBreak(BlockBreakEvent event) {
      if (this.config.trackBrokenBlocks()) {
         this.track(event.getPlayer(), event.getBlock(), false);
      }
   }

   private void track(Player player, Block block, boolean placed) {
      this.teamDuelManager.getSession(player.getUniqueId()).ifPresent(session -> this.trackTeam(session, block, placed));
      if (!this.teamDuelManager.isInTeamDuel(player.getUniqueId())) {
         this.duelManager.getSession(player.getUniqueId()).ifPresent(session -> {
            if (session.state() == DuelState.ACTIVE) {
               String arenaWorld = session.arena().world();
               if (arenaWorld != null && block.getWorld().getName().equalsIgnoreCase(arenaWorld)) {
                  if (placed) {
                     session.blockTracker().trackPlaced(block);
                  } else {
                     session.blockTracker().trackBroken(block);
                  }
               }
            }
         });
      }
   }

   private void trackTeam(TeamDuelSession session, Block block, boolean placed) {
      if (session.state() == DuelState.ACTIVE) {
         String arenaWorld = session.arena().world();
         if (arenaWorld != null && block.getWorld().getName().equalsIgnoreCase(arenaWorld)) {
            if (placed) {
               session.blockTracker().trackPlaced(block);
            } else {
               session.blockTracker().trackBroken(block);
            }
         }
      }
   }
}
