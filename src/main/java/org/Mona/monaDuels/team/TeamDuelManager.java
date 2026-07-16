package org.Mona.monaDuels.team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.Mona.monaDuels.MonaDuels;
import org.Mona.monaDuels.arena.Arena;
import org.Mona.monaDuels.arena.ArenaManager;
import org.Mona.monaDuels.arena.MapPoolManager;
import org.Mona.monaDuels.celebration.CelebrationService;
import org.Mona.monaDuels.config.ConfigManager;
import org.Mona.monaDuels.config.MessageService;
import org.Mona.monaDuels.duel.DuelState;
import org.Mona.monaDuels.kit.Kit;
import org.Mona.monaDuels.kit.KitManager;
import org.Mona.monaDuels.party.Party;
import org.Mona.monaDuels.party.PartyDuelRequest;
import org.Mona.monaDuels.party.PartyManager;
import org.Mona.monaDuels.party.PartyRequestManager;
import org.Mona.monaDuels.player.PlayerSnapshot;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public final class TeamDuelManager {
   private final MonaDuels plugin;
   private final ConfigManager config;
   private final MessageService messages;
   private final ArenaManager arenaManager;
   private final MapPoolManager mapPoolManager;
   private final KitManager kitManager;
   private final PartyManager partyManager;
   private final PartyRequestManager partyRequestManager;
   private CelebrationService celebrationService;
   private final Map<UUID, TeamDuelSession> sessionsByPlayer = new HashMap<>();
   private final Map<UUID, BukkitTask> finishTasks = new HashMap<>();

   public TeamDuelManager(
      MonaDuels plugin,
      ConfigManager config,
      MessageService messages,
      ArenaManager arenaManager,
      MapPoolManager mapPoolManager,
      KitManager kitManager,
      PartyManager partyManager,
      PartyRequestManager partyRequestManager
   ) {
      this.plugin = plugin;
      this.config = config;
      this.messages = messages;
      this.arenaManager = arenaManager;
      this.mapPoolManager = mapPoolManager;
      this.kitManager = kitManager;
      this.partyManager = partyManager;
      this.partyRequestManager = partyRequestManager;
   }

   public void bindCelebrationService(CelebrationService celebrationService) {
      this.celebrationService = celebrationService;
   }

   public boolean isInTeamDuel(UUID playerId) {
      return this.sessionsByPlayer.containsKey(playerId);
   }

   public Optional<TeamDuelSession> getSession(UUID playerId) {
      return Optional.ofNullable(this.sessionsByPlayer.get(playerId));
   }

   public void startFromRequest(Player targetLeader) {
      Optional<PartyDuelRequest> requestOpt = this.partyRequestManager.removeIncoming(targetLeader.getUniqueId());
      if (requestOpt.isEmpty()) {
         this.messages.send(targetLeader, "party.request.none");
      } else {
         PartyDuelRequest request = requestOpt.get();
         Player challengerLeader = Bukkit.getPlayer(request.challengerLeaderId());
         if (challengerLeader != null && challengerLeader.isOnline()) {
            Optional<Party> partyAOpt = this.partyManager.getParty(challengerLeader.getUniqueId());
            Optional<Party> partyBOpt = this.partyManager.getParty(targetLeader.getUniqueId());
            if (!partyAOpt.isEmpty()
               && !partyBOpt.isEmpty()
               && this.partyManager.isPartyReadyForDuel(partyAOpt.get())
               && this.partyManager.isPartyReadyForDuel(partyBOpt.get())) {
               Optional<Kit> kitOpt = this.kitManager.find(request.kitName());
               if (kitOpt.isEmpty()) {
                  this.messages.send(targetLeader, "kit.not-found", Map.of("kit", request.kitName()));
               } else {
                  Kit kit = kitOpt.get();
                  Optional<Arena> arenaOpt = this.mapPoolManager.findFreeArenaForKit(kit.name(), this.arenaManager);
                  if (arenaOpt.isEmpty()) {
                     String duelWorldName = this.config.duelWorld();
                     World duelWorld = Bukkit.getWorld(duelWorldName);
                     Map<String, String> poolMsg = Map.of("pool", kit.name(), "kit", kit.displayName());
                     if (duelWorld == null) {
                        this.messages.send(targetLeader, "duel-world.not-loaded", Map.of("world", duelWorldName));
                        this.messages.send(challengerLeader, "duel-world.not-loaded", Map.of("world", duelWorldName));
                     } else {
                        this.messages.send(targetLeader, "arena.none-free-pool", poolMsg);
                        this.messages.send(challengerLeader, "arena.none-free-pool", poolMsg);
                     }
                  } else {
                     List<UUID> teamA = new ArrayList<>(partyAOpt.get().members());
                     List<UUID> teamB = new ArrayList<>(partyBOpt.get().members());
                     this.beginTeamDuel(teamA, teamB, kit, arenaOpt.get());
                     this.messages.send(challengerLeader, "party.request.accepted-challenger", Map.of("target", targetLeader.getName()));
                     this.messages.send(targetLeader, "party.request.accepted-target", Map.of("challenger", challengerLeader.getName()));
                  }
               }
            } else {
               this.messages.send(targetLeader, "party.not-ready");
            }
         } else {
            this.messages.send(targetLeader, "general.player-offline", Map.of("player", "?"));
         }
      }
   }

   public void denyRequest(Player targetLeader) {
      Optional<PartyDuelRequest> requestOpt = this.partyRequestManager.removeIncoming(targetLeader.getUniqueId());
      if (requestOpt.isEmpty()) {
         this.messages.send(targetLeader, "party.request.none");
      } else {
         PartyDuelRequest request = requestOpt.get();
         Player challenger = Bukkit.getPlayer(request.challengerLeaderId());
         this.messages.send(targetLeader, "party.request.denied-target");
         if (challenger != null) {
            this.messages.send(challenger, "party.request.denied-challenger", Map.of("target", targetLeader.getName()));
         }
      }
   }

   private void beginTeamDuel(List<UUID> teamA, List<UUID> teamB, Kit kit, Arena arena) {
      TeamDuelSession session = new TeamDuelSession(teamA, teamB, kit.name(), arena);

      for (UUID id : session.allPlayers()) {
         this.sessionsByPlayer.put(id, session);
      }

      Location spawnA = arena.spawn1();
      Location spawnB = arena.spawn2();
      double offset = this.config.partySpawnOffset();
      int indexA = 0;

      for (UUID id : teamA) {
         Player player = Bukkit.getPlayer(id);
         if (player == null) {
            this.forceEnd(session);
            return;
         }

         Location spawn = offsetSpawn(spawnA, indexA++, offset);
         session.setArenaSpawn(id, spawn);
         session.putSnapshot(id, PlayerSnapshot.capture(player));
      }

      int indexB = 0;

      for (UUID id : teamB) {
         Player player = Bukkit.getPlayer(id);
         if (player == null) {
            this.forceEnd(session);
            return;
         }

         Location spawn = offsetSpawn(spawnB, indexB++, offset);
         session.setArenaSpawn(id, spawn);
         session.putSnapshot(id, PlayerSnapshot.capture(player));
      }

      this.partyRequestManager.clear();
      Map<String, String> started = Map.of("arena", arena.displayName(), "kit", kit.displayName());

      for (UUID id : session.allPlayers()) {
         Player player = Bukkit.getPlayer(id);
         if (player != null) {
            this.messages.send(player, "party.duel.started", started);
         }
      }

      int delay = this.config.arenaReteleportDelayTicks();
      Runnable startCountdown = () -> this.runCountdown(session, kit);
      Runnable secondTeleport = () -> {
         if (this.sessionsByPlayer.containsKey(teamA.get(0))) {
            for (UUID idx : session.allPlayers()) {
               Player playerx = Bukkit.getPlayer(idx);
               if (playerx != null) {
                  this.forceArenaSpawn(session, playerx);
               }
            }

            startCountdown.run();
         }
      };

      for (UUID idx : session.allPlayers()) {
         Player player = Bukkit.getPlayer(idx);
         if (player != null) {
            this.teleport(session, player, session.arenaSpawn(idx));
         }
      }

      Bukkit.getScheduler().runTaskLater(this.plugin, secondTeleport, (long)delay);
   }

   private static Location offsetSpawn(Location base, int index, double offset) {
      if (base == null) {
         return null;
      } else {
         Location loc = base.clone();
         if (index > 0) {
            loc.add(offset * (double)index, 0.0, 0.0);
         }

         return loc;
      }
   }

   private void teleport(TeamDuelSession session, Player player, Location location) {
      if (location != null) {
         session.setPluginTeleport(true);
         player.teleport(location);
         Bukkit.getScheduler().runTaskLater(this.plugin, () -> session.setPluginTeleport(false), 2L);
      }
   }

   public void forceArenaSpawn(TeamDuelSession session, Player player) {
      Location spawn = session.arenaSpawn(player.getUniqueId());
      if (spawn != null) {
         this.teleport(session, player, spawn);
      }
   }

   private void runCountdown(TeamDuelSession session, Kit kit) {
      int seconds = this.config.countdownSeconds();
      if (seconds <= 0) {
         this.activate(session, kit);
      } else {
         int[] remaining = new int[]{seconds};
         BukkitTask[] taskRef = new BukkitTask[1];
         taskRef[0] = Bukkit.getScheduler().runTaskTimer(this.plugin, () -> {
            if (!this.sessionsByPlayer.containsKey(session.teamA().get(0))) {
               taskRef[0].cancel();
            } else if (remaining[0] > 0) {
               for (UUID id : session.allPlayers()) {
                  Player player = Bukkit.getPlayer(id);
                  if (player != null) {
                     this.enforceSurvival(player);
                     this.messages.send(player, "countdown.tick", Map.of("seconds", String.valueOf(remaining[0])));
                  }
               }

               remaining[0]--;
            } else {
               taskRef[0].cancel();
               this.activate(session, kit);
            }
         }, 20L, 20L);
      }
   }

   private void activate(TeamDuelSession session, Kit kit) {
      session.clearCountdownAnchors();
      session.markActive();
      session.setState(DuelState.ACTIVE);

      for (UUID id : session.allPlayers()) {
         Player player = Bukkit.getPlayer(id);
         if (player != null) {
            this.enforceSurvival(player);
            this.kitManager.apply(player, kit);
            player.setHealth(player.getMaxHealth());
            player.setFoodLevel(20);
         }
      }

      for (UUID idx : session.allPlayers()) {
         Player player = Bukkit.getPlayer(idx);
         if (player != null) {
            this.messages.send(player, "countdown.start");
         }
      }
   }

   public void handleDeath(Player dead, Player killer) {
      this.getSession(dead.getUniqueId()).ifPresent(session -> {
         if (session.state() == DuelState.ACTIVE || session.state() == DuelState.COUNTDOWN) {
            if (!session.isEliminated(dead.getUniqueId())) {
               Location deathLoc = dead.getLocation().clone();
               session.markEliminated(dead.getUniqueId());
               this.respawnAsArenaSpectator(dead, deathLoc);
               if (killer != null && this.celebrationService != null) {
                  this.celebrationService.playKillEffect(killer, deathLoc);
               }

               List<UUID> deadTeam = session.teamOf(dead.getUniqueId());
               if (session.isTeamFullyEliminated(deadTeam)) {
                  List<UUID> winners = session.oppositeTeam(dead.getUniqueId());
                  this.endWithWinningTeam(session, winners, deadTeam);
               }
            }
         }
      });
   }

   private void respawnAsArenaSpectator(Player player, Location location) {
      if (player.isDead()) {
         player.spigot().respawn();
      }

      player.setGameMode(GameMode.SPECTATOR);
      player.setAllowFlight(true);
      if (location.getWorld() != null) {
         player.teleport(location);
      }
   }

   private void endWithWinningTeam(TeamDuelSession session, List<UUID> winners, List<UUID> losers) {
      if (session.state() != DuelState.ENDING) {
         session.setState(DuelState.ENDING);
         String winnerNames = formatNames(winners);
         String loserNames = formatNames(losers);
         Map<String, String> ph = Map.of("winners", winnerNames, "losers", loserNames);

         for (UUID id : winners) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
               this.messages.send(player, "party.duel.win", ph);
            }
         }

         for (UUID idx : losers) {
            Player player = Bukkit.getPlayer(idx);
            if (player != null) {
               this.messages.send(player, "party.duel.lose", ph);
            }
         }

         int delaySeconds = this.config.postKillDelaySeconds();
         BukkitTask task = Bukkit.getScheduler().runTaskLater(this.plugin, () -> this.finishAll(session), (long)delaySeconds * 20L);
         this.finishTasks.put(session.sessionId(), task);
      }
   }

   private void finishAll(TeamDuelSession session) {
      this.finishTasks.remove(session.sessionId());

      for (UUID id : session.allPlayers()) {
         Player player = Bukkit.getPlayer(id);
         if (player != null) {
            this.finishPlayer(player, session);
         }
      }

      this.cleanupArena(session);
      this.unregister(session);
   }

   private void cleanupArena(TeamDuelSession session) {
      try {
         session.blockTracker().cleanupPlaced();
      } catch (Exception var4) {
      }

      try {
         session.blockTracker().restoreBroken();
      } catch (Exception var3) {
      }

      this.arenaManager.release(session.arena());
   }

   private void finishPlayer(Player player, TeamDuelSession session) {
      this.enforceSurvival(player);
      PlayerSnapshot snapshot = session.snapshot(player.getUniqueId());
      if (snapshot != null) {
         snapshot.restoreInventoryOnly(player);
      }

      this.enforceSurvival(player);
      Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
         if (snapshot != null && snapshot.location() != null && snapshot.location().getWorld() != null) {
            player.teleport(snapshot.location());
         } else {
            Location lobby = this.config.lobby();
            if (lobby != null && lobby.getWorld() != null) {
               player.teleport(lobby);
            }
         }

         this.enforceSurvival(player);
         MonaDuels patt0$temp = this.plugin;
         if (patt0$temp instanceof MonaDuels) {
            patt0$temp.lobbyLayoutService().applyLayout(player);
         }
      }, 5L);
   }

   public void handleLeave(Player leaver) {
      this.getSession(leaver.getUniqueId()).ifPresent(session -> {
         if (session.state() != DuelState.ENDING) {
            List<UUID> winners = session.oppositeTeam(leaver.getUniqueId());
            List<UUID> losers = session.teamOf(leaver.getUniqueId());
            this.messages.send(leaver, "duel.leave");
            this.endWithWinningTeam(session, winners, losers);
         }
      });
   }

   public void handleLogout(Player player) {
      this.handleLeave(player);
   }

   public void forceEnd(TeamDuelSession session) {
      BukkitTask task = this.finishTasks.remove(session.sessionId());
      if (task != null) {
         task.cancel();
      }

      for (UUID id : session.allPlayers()) {
         Player player = Bukkit.getPlayer(id);
         if (player != null) {
            this.finishPlayer(player, session);
         }
      }

      this.cleanupArena(session);
      this.unregister(session);
   }

   private void unregister(TeamDuelSession session) {
      for (UUID id : session.allPlayers()) {
         this.sessionsByPlayer.remove(id);
      }
   }

   public void enforceSurvival(Player player) {
      player.setGameMode(GameMode.SURVIVAL);
      player.setAllowFlight(false);
      player.setFlying(false);
   }

   private static String formatNames(List<UUID> ids) {
      StringBuilder sb = new StringBuilder();

      for (UUID id : ids) {
         Player player = Bukkit.getPlayer(id);
         if (player != null) {
            if (!sb.isEmpty()) {
               sb.append(", ");
            }

            sb.append(player.getName());
         }
      }

      return sb.isEmpty() ? "?" : sb.toString();
   }

   public void shutdown() {
      for (TeamDuelSession session : new HashSet<>(this.sessionsByPlayer.values())) {
         this.forceEnd(session);
      }
   }
}
