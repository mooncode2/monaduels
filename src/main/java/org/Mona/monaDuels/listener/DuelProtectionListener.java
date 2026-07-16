package org.Mona.monaDuels.listener;

import java.util.Locale;
import org.Mona.monaDuels.config.ConfigManager;
import org.Mona.monaDuels.config.MessageService;
import org.Mona.monaDuels.duel.DuelManager;
import org.Mona.monaDuels.duel.DuelState;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.ItemStack;

public final class DuelProtectionListener implements Listener {
   private final ConfigManager config;
   private final MessageService messages;
   private final DuelManager duelManager;

   public DuelProtectionListener(ConfigManager config, MessageService messages, DuelManager duelManager) {
      this.config = config;
      this.messages = messages;
      this.duelManager = duelManager;
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onCommand(PlayerCommandPreprocessEvent event) {
      if (this.config.protectionEnabled("block-commands")) {
         Player player = event.getPlayer();
         if (this.duelManager.isInDuel(player.getUniqueId())) {
            if (!player.hasPermission("monaduels.bypass.commandblock")) {
               String label = normalizeCommand(event.getMessage());

               for (String allowedCmd : this.config.allowedCommandsDuringDuel()) {
                  if (label.equals(allowedCmd.toLowerCase(Locale.ROOT)) || label.startsWith(allowedCmd.toLowerCase(Locale.ROOT) + " ")) {
                     return;
                  }
               }

               event.setCancelled(true);
               this.messages.send(player, "protection.command-blocked");
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onDrop(PlayerDropItemEvent event) {
      if (this.config.protectionEnabled("block-item-drop")) {
         if (this.duelManager.isInDuel(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            this.messages.send(event.getPlayer(), "protection.drop-blocked");
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onTeleport(PlayerTeleportEvent event) {
      if (this.config.protectionEnabled("block-teleport")) {
         Player player = event.getPlayer();
         this.duelManager.getSession(player.getUniqueId()).ifPresent(session -> {
            if (!session.isPluginTeleport()) {
               if (session.state() == DuelState.ACTIVE || session.state() == DuelState.COUNTDOWN) {
                  event.setCancelled(true);
                  this.messages.send(player, "protection.teleport-blocked");
               }
            }
         });
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onMove(PlayerMoveEvent event) {
      if (event.getFrom().getX() != event.getTo().getX() || event.getFrom().getY() != event.getTo().getY() || event.getFrom().getZ() != event.getTo().getZ()) {
         Player player = event.getPlayer();
         this.duelManager.getSession(player.getUniqueId()).ifPresent(session -> {
            if (session.state() == DuelState.COUNTDOWN) {
               Location anchor = session.countdownAnchor(player.getUniqueId());
               if (anchor != null) {
                  event.setCancelled(true);
                  if (player.getLocation().distanceSquared(anchor) > 0.01) {
                     session.setPluginTeleport(true);
                     player.teleport(anchor);
                     session.setPluginTeleport(false);
                  }
               }
            } else if (session.state() == DuelState.ACTIVE) {
               double maxDistance = this.config.maxEscapeDistance();
               if (!(maxDistance <= 0.0)) {
                  Location center = session.arena().spawn1();
                  if (center != null) {
                     if (!player.getWorld().equals(center.getWorld())) {
                        session.setPluginTeleport(true);
                        player.teleport(center);
                        session.setPluginTeleport(false);
                        this.messages.send(player, "duel.escape-blocked");
                     } else {
                        if (player.getLocation().distanceSquared(center) > maxDistance * maxDistance) {
                           session.setPluginTeleport(true);
                           player.teleport(center);
                           session.setPluginTeleport(false);
                           this.messages.send(player, "duel.escape-blocked");
                        }
                     }
                  }
               }
            }
         });
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onGameModeChange(PlayerGameModeChangeEvent event) {
      Player player = event.getPlayer();
      this.duelManager.getSession(player.getUniqueId()).ifPresent(session -> {
         if (session.state() == DuelState.POST_FIGHT) {
            if (!session.isPostFightLoser(player.getUniqueId()) || event.getNewGameMode() != GameMode.SPECTATOR) {
               if (session.isPostFightLoser(player.getUniqueId())) {
                  event.setCancelled(true);
                  player.setGameMode(GameMode.SPECTATOR);
               } else {
                  if (event.getNewGameMode() != GameMode.SURVIVAL) {
                     event.setCancelled(true);
                     this.duelManager.enforceSurvival(player);
                  }
               }
            }
         } else {
            if (event.getNewGameMode() != GameMode.SURVIVAL) {
               event.setCancelled(true);
               this.duelManager.enforceSurvival(player);
            }
         }
      });
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onDamage(EntityDamageEvent event) {
      if (event.getEntity() instanceof Player player) {
         this.duelManager.getSession(player.getUniqueId()).ifPresent(session -> {
            if (session.state() == DuelState.POST_FIGHT) {
               event.setCancelled(true);
            }
         });
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onPearl(PlayerTeleportEvent event) {
      if (event.getCause() == TeleportCause.ENDER_PEARL || event.getCause() == TeleportCause.CHORUS_FRUIT) {
         Player player = event.getPlayer();
         if (this.duelManager.isInDuel(player.getUniqueId())) {
            if (event.getCause() != TeleportCause.ENDER_PEARL || this.config.protectionEnabled("block-ender-pearl")) {
               if (event.getCause() != TeleportCause.CHORUS_FRUIT || this.config.protectionEnabled("block-chorus-fruit")) {
                  this.duelManager.getSession(player.getUniqueId()).ifPresent(session -> {
                     if (session.state() == DuelState.ACTIVE || session.state() == DuelState.COUNTDOWN) {
                        event.setCancelled(true);
                        this.messages.send(player, "protection.teleport-blocked");
                     }
                  });
               }
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onInteractPearl(PlayerInteractEvent event) {
      if (this.config.protectionEnabled("block-ender-pearl")) {
         ItemStack item = event.getItem();
         if (item != null) {
            if (item.getType() == Material.ENDER_PEARL || item.getType() == Material.CHORUS_FRUIT) {
               Player player = event.getPlayer();
               if (this.duelManager.isInDuel(player.getUniqueId())) {
                  this.duelManager.getSession(player.getUniqueId()).ifPresent(session -> {
                     if (session.state() == DuelState.ACTIVE) {
                        event.setCancelled(true);
                     }
                  });
               }
            }
         }
      }
   }

   private static String normalizeCommand(String message) {
      String cmd = message.startsWith("/") ? message.substring(1) : message;
      return cmd.trim().toLowerCase(Locale.ROOT);
   }
}
