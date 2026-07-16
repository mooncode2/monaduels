package org.Mona.monaDuels.listener;

import java.lang.reflect.Method;
import net.kyori.adventure.text.Component;
import org.Mona.monaDuels.MonaDuels;
import org.Mona.monaDuels.config.ConfigManager;
import org.Mona.monaDuels.duel.DuelManager;
import org.Mona.monaDuels.duel.DuelState;
import org.Mona.monaDuels.team.TeamDuelManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public final class DuelListener implements Listener {
   private final MonaDuels plugin;
   private final ConfigManager config;
   private final DuelManager duelManager;
   private final TeamDuelManager teamDuelManager;

   public DuelListener(MonaDuels plugin, ConfigManager config, DuelManager duelManager, TeamDuelManager teamDuelManager) {
      this.plugin = plugin;
      this.config = config;
      this.duelManager = duelManager;
      this.teamDuelManager = teamDuelManager;
   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onDeath(PlayerDeathEvent event) {
      Player player = event.getEntity();
      if (this.duelManager.isInDuel(player.getUniqueId())) {
         event.deathMessage(null);
         event.setKeepInventory(true);
         event.getDrops().clear();
         event.setKeepLevel(true);
         event.setDroppedExp(0);
         hideDeathScreen(event);
         Player killer = player.getKiller();
         if (this.teamDuelManager.isInTeamDuel(player.getUniqueId())) {
            this.teamDuelManager.handleDeath(player, killer);
         } else {
            this.duelManager.handleDeath(player);
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onRespawn(PlayerRespawnEvent event) {
      Player player = event.getPlayer();
      Location postFightLoc = this.duelManager.getPostFightRespawnLocation(player.getUniqueId());
      if (postFightLoc != null && postFightLoc.getWorld() != null) {
         event.setRespawnLocation(postFightLoc);
         this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
            if (player.isOnline()) {
               this.duelManager.applyPostFightLoser(player, postFightLoc);
            }
         }, 2L);
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onWorldChange(PlayerChangedWorldEvent event) {
      Player player = event.getPlayer();
      this.duelManager.getSession(player.getUniqueId()).ifPresent(session -> {
         if (session.state() == DuelState.COUNTDOWN) {
            this.plugin.getServer().getScheduler().runTask(this.plugin, () -> this.duelManager.forceArenaSpawn(session, player));
         }
      });
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onTeleportDuringCountdown(PlayerTeleportEvent event) {
      Player player = event.getPlayer();
      this.duelManager.getSession(player.getUniqueId()).ifPresent(session -> {
         if (session.state() == DuelState.COUNTDOWN) {
            if (!session.isPluginTeleport()) {
               this.plugin.getServer().getScheduler().runTask(this.plugin, () -> this.duelManager.forceArenaSpawn(session, player));
            }
         }
      });
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      Player player = event.getPlayer();
      if (this.duelManager.isInDuel(player.getUniqueId())) {
         this.duelManager.handleLogout(player);
      }
   }

   private static void hideDeathScreen(PlayerDeathEvent event) {
      try {
         Method setShow = event.getClass().getMethod("setShowDeathScreen", boolean.class);
         setShow.invoke(event, false);
      } catch (ReflectiveOperationException var3) {
      }

      try {
         Method screenMsg = event.getClass().getMethod("deathScreenMessage", Component.class);
         screenMsg.invoke(event, null);
      } catch (ReflectiveOperationException var2) {
      }
   }
}
