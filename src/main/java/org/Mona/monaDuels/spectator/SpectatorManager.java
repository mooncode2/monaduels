package org.Mona.monaDuels.spectator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.Mona.monaDuels.MonaDuels;
import org.Mona.monaDuels.config.ConfigManager;
import org.Mona.monaDuels.config.MessageService;
import org.Mona.monaDuels.display.DuelBossBarService;
import org.Mona.monaDuels.display.DuelDisplayManager;
import org.Mona.monaDuels.duel.DuelManager;
import org.Mona.monaDuels.duel.DuelSession;
import org.Mona.monaDuels.duel.DuelState;
import org.Mona.monaDuels.kit.KitManager;
import org.Mona.monaDuels.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

public final class SpectatorManager {
   private final MonaDuels plugin;
   private final ConfigManager config;
   private final MessageService messages;
   private final DuelManager duelManager;
   private final KitManager kitManager;
   private final DuelBossBarService bossBarService;
   private final DuelDisplayManager displayManager;
   private final Map<UUID, SpectatorManager.SpectatorData> spectators = new ConcurrentHashMap<>();

   public SpectatorManager(
      MonaDuels plugin,
      ConfigManager config,
      MessageService messages,
      DuelManager duelManager,
      KitManager kitManager,
      DuelBossBarService bossBarService,
      DuelDisplayManager displayManager
   ) {
      this.plugin = plugin;
      this.config = config;
      this.messages = messages;
      this.duelManager = duelManager;
      this.kitManager = kitManager;
      this.bossBarService = bossBarService;
      this.displayManager = displayManager;
   }

   public boolean isSpectating(UUID playerId) {
      return this.spectators.containsKey(playerId);
   }

   public void openSpectateMenu(Player player) {
      List<DuelSession> sessions = new ArrayList<>(this.duelManager.getActiveSessions());
      String title = ColorUtil.color(this.config.spectateMenuTitle());
      int size = Math.min(54, Math.max(9, (sessions.size() + 8) / 9 * 9));
      if (size == 0) {
         size = 9;
      }

      Inventory inv = Bukkit.createInventory(null, size, title);
      if (sessions.isEmpty()) {
         inv.setItem(4, item(Material.BARRIER, "&cРќРµС‚ Р°РєС‚РёРІРЅС‹С… РґСѓСЌР»РµР№", List.of("&7РЎРµР№С‡Р°СЃ РЅРёРєС‚Рѕ РЅРµ СЃСЂР°Р¶Р°РµС‚СЃСЏ")));
      } else {
         int slot = 0;

         for (DuelSession session : sessions) {
            if (slot >= size) {
               break;
            }

            Player p1 = Bukkit.getPlayer(session.playerOneId());
            Player p2 = Bukkit.getPlayer(session.playerTwoId());
            if (p1 != null && p2 != null) {
               String status = session.state() == DuelState.ACTIVE ? "&aР’ РёРіСЂРµ" : "&eРџРѕРґРіРѕС‚РѕРІРєР°";
               inv.setItem(slot++, this.duelItem(p1, p2, session, status));
            }
         }
      }

      player.openInventory(inv);
   }

   public void joinSpectateByShortId(Player spectator, String shortId) {
      this.duelManager
         .findSessionByShortId(shortId)
         .ifPresentOrElse(session -> this.joinSpectate(spectator, session.sessionId()), () -> this.messages.send(spectator, "spectate.not-found"));
   }

   public void joinSpectate(Player spectator, UUID sessionId) {
      Optional<DuelSession> sessionOpt = this.duelManager.findSession(sessionId);
      if (sessionOpt.isEmpty()) {
         this.messages.send(spectator, "spectate.not-found");
      } else {
         DuelSession session = sessionOpt.get();
         if (this.duelManager.isInDuel(spectator.getUniqueId())) {
            this.messages.send(spectator, "duel.already-in-duel");
         } else {
            if (this.isSpectating(spectator.getUniqueId())) {
               this.leaveSpectate(spectator, false);
            }

            Player p1 = Bukkit.getPlayer(session.playerOneId());
            Player p2 = Bukkit.getPlayer(session.playerTwoId());
            if (p1 != null && p2 != null) {
               Location watch = this.arenaSpectatorLocation(session);
               if (watch != null && watch.getWorld() != null) {
                  SpectatorManager.SpectatorData data = new SpectatorManager.SpectatorData(
                     session.sessionId(), spectator.getLocation().clone(), spectator.getGameMode(), spectator.getAllowFlight(), spectator.isFlying()
                  );
                  this.spectators.put(spectator.getUniqueId(), data);
                  session.addSpectator(spectator.getUniqueId());
                  spectator.setGameMode(GameMode.SPECTATOR);
                  spectator.setAllowFlight(true);
                  spectator.setFlying(false);
                  this.teleportSpectatorToArena(spectator, watch.clone(), () -> {
                     if (this.isSpectating(spectator.getUniqueId())) {
                        this.messages.send(spectator, "spectate.joined", Map.of("player1", p1.getName(), "player2", p2.getName()));
                        this.bossBarService.update(spectator, p1, session, true);
                     }
                  });
               } else {
                  this.messages.send(spectator, "spectate.no-spawn", Map.of("arena", session.arena().displayName()));
               }
            } else {
               this.messages.send(spectator, "spectate.not-found");
            }
         }
      }
   }

   public void leaveSpectate(Player spectator) {
      this.leaveSpectate(spectator, false);
   }

   public void leaveSpectate(Player spectator, boolean duelEnded) {
      SpectatorManager.SpectatorData data = this.spectators.remove(spectator.getUniqueId());
      if (data != null) {
         this.duelManager.findSession(data.sessionId()).ifPresent(s -> s.removeSpectator(spectator.getUniqueId()));
         this.bossBarService.hide(spectator);
         this.displayManager.restoreSpectatorScoreboard(spectator);
         spectator.setGameMode(data.gameMode() != null ? data.gameMode() : GameMode.SURVIVAL);
         spectator.setAllowFlight(data.allowFlight());
         spectator.setFlying(data.flying());
         if (data.previousLocation() != null && data.previousLocation().getWorld() != null) {
            spectator.teleport(data.previousLocation());
         }

         if (duelEnded) {
            this.messages.send(spectator, "spectate.duel-ended");
         } else {
            this.messages.send(spectator, "spectate.left");
         }
      }
   }

   public void removeAllFromSession(DuelSession session) {
      this.removeAllFromSession(session, false);
   }

   public void removeAllFromSession(DuelSession session, boolean duelEnded) {
      for (UUID spectatorId : new ArrayList<>(session.spectators())) {
         Player spectator = Bukkit.getPlayer(spectatorId);
         if (spectator != null) {
            this.leaveSpectate(spectator, duelEnded);
         } else {
            this.spectators.remove(spectatorId);
         }
      }
   }

   public void scheduleRemovalAfterDuel(DuelSession session, int delaySeconds) {
      List<UUID> spectatorIds = new ArrayList<>(session.spectators());
      if (!spectatorIds.isEmpty()) {
         if (delaySeconds <= 0) {
            this.removeAllFromSession(session, true);
         } else {
            Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
               for (UUID spectatorId : spectatorIds) {
                  Player spectator = Bukkit.getPlayer(spectatorId);
                  if (spectator != null && this.isSpectating(spectatorId)) {
                     this.leaveSpectate(spectator, true);
                  }
               }
            }, (long)delaySeconds * 20L);
         }
      }
   }

   private void teleportSpectatorToArena(Player spectator, Location target, Runnable onComplete) {
      spectator.teleport(target);
      int delay = this.config.arenaReteleportDelayTicks();
      Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
         if (this.isSpectating(spectator.getUniqueId()) && spectator.isOnline()) {
            spectator.teleport(target);
            if (onComplete != null) {
               onComplete.run();
            }
         }
      }, (long)delay);
   }

   private Location arenaSpectatorLocation(DuelSession session) {
      Location configured = session.arena().spectatorSpawn();
      if (configured != null && configured.getWorld() != null) {
         return configured.clone();
      } else {
         Location s1 = session.arenaSpawn(session.playerOneId());
         Location s2 = session.arenaSpawn(session.playerTwoId());
         if (s1 != null && s2 != null && s1.getWorld() != null) {
            double x = (s1.getX() + s2.getX()) / 2.0;
            double y = Math.max(s1.getY(), s2.getY()) + this.config.spectateHeightOffset();
            double z = (s1.getZ() + s2.getZ()) / 2.0;
            return new Location(s1.getWorld(), x, y, z, s1.getYaw(), s1.getPitch());
         } else if (s1 != null && s1.getWorld() != null) {
            Location loc = s1.clone();
            loc.setY(loc.getY() + this.config.spectateHeightOffset());
            return loc;
         } else {
            Location spawn1 = session.arena().spawn1();
            if (spawn1 != null && spawn1.getWorld() != null) {
               Location loc = spawn1.clone();
               loc.setY(loc.getY() + this.config.spectateHeightOffset());
               return loc;
            } else {
               return null;
            }
         }
      }
   }

   private ItemStack duelItem(Player p1, Player p2, DuelSession session, String status) {
      ItemStack item = new ItemStack(Material.PLAYER_HEAD);
      SkullMeta meta = (SkullMeta)item.getItemMeta();
      if (meta != null) {
         meta.setOwningPlayer(p1);
         meta.setDisplayName(ColorUtil.color("&f" + p1.getName() + " &7vs &f" + p2.getName()));
         meta.setLore(
            List.of(
               ColorUtil.color("&7РљРёС‚: &f" + this.kitDisplayName(session.kitName())),
               ColorUtil.color("&7РђСЂРµРЅР°: &f" + session.arena().displayName()),
               ColorUtil.color("&7РЎС‚Р°С‚СѓСЃ: " + status),
               "",
               ColorUtil.color("&eРќР°Р¶РјРёС‚Рµ, С‡С‚РѕР±С‹ РЅР°Р±Р»СЋРґР°С‚СЊ"),
               ColorUtil.color("&8id:" + session.shortId())
            )
         );
         item.setItemMeta(meta);
      }

      return item;
   }

   private String kitDisplayName(String kitName) {
      return this.kitManager.find(kitName).map(kit -> kit.displayName()).orElse(kitName);
   }

   private static ItemStack item(Material material, String name, List<String> lore) {
      ItemStack stack = new ItemStack(material);
      ItemMeta meta = stack.getItemMeta();
      if (meta != null) {
         meta.setDisplayName(ColorUtil.color(name));
         meta.setLore(lore.stream().map(ColorUtil::color).toList());
         stack.setItemMeta(meta);
      }

      return stack;
   }

   private static record SpectatorData(UUID sessionId, Location previousLocation, GameMode gameMode, boolean allowFlight, boolean flying) {
   }
}
