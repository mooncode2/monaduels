package org.Mona.monaDuels.duel;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.Mona.monaDuels.MonaDuels;
import org.Mona.monaDuels.arena.Arena;
import org.Mona.monaDuels.arena.ArenaManager;
import org.Mona.monaDuels.arena.MapPoolManager;
import org.Mona.monaDuels.celebration.CelebrationService;
import org.Mona.monaDuels.config.ConfigManager;
import org.Mona.monaDuels.config.MessageService;
import org.Mona.monaDuels.cooldown.CooldownManager;
import org.Mona.monaDuels.display.DuelDisplayManager;
import org.Mona.monaDuels.kit.Kit;
import org.Mona.monaDuels.kit.KitManager;
import org.Mona.monaDuels.player.PlayerDataManager;
import org.Mona.monaDuels.player.PlayerSnapshot;
import org.Mona.monaDuels.queue.PlayAgainService;
import org.Mona.monaDuels.queue.QueueManager;
import org.Mona.monaDuels.request.DuelRequest;
import org.Mona.monaDuels.request.RequestManager;
import org.Mona.monaDuels.service.DuelResultService;
import org.Mona.monaDuels.service.DuelSoundService;
import org.Mona.monaDuels.spectator.SpectatorManager;
import org.Mona.monaDuels.stats.StatsManager;
import org.Mona.monaDuels.team.TeamDuelManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;

public final class DuelManager {
   private final MonaDuels plugin;
   private final ConfigManager config;
   private final MessageService messages;
   private final ArenaManager arenaManager;
   private final MapPoolManager mapPoolManager;
   private final KitManager kitManager;
   private final RequestManager requestManager;
   private final CooldownManager cooldownManager;
   private final DuelDisplayManager displayManager;
   private final DuelResultService resultService;
   private final DuelSoundService soundService;
   private final StatsManager statsManager;
   private final PlayerDataManager playerDataManager;
   private TeamDuelManager teamDuelManager;
   private CelebrationService celebrationService;
   private SpectatorManager spectatorManager;
   private QueueManager queueManager;
   private PlayAgainService playAgainService;
   private final Map<UUID, DuelSession> sessionsByPlayer = new ConcurrentHashMap<>();
   private final Map<UUID, Location> postFightRespawnLocations = new ConcurrentHashMap<>();
   private final Map<UUID, BukkitTask> postFightFinishTasks = new ConcurrentHashMap<>();

   public DuelManager(
      MonaDuels plugin,
      ConfigManager config,
      MessageService messages,
      ArenaManager arenaManager,
      MapPoolManager mapPoolManager,
      KitManager kitManager,
      RequestManager requestManager,
      CooldownManager cooldownManager,
      DuelDisplayManager displayManager,
      DuelResultService resultService,
      DuelSoundService soundService,
      StatsManager statsManager,
      PlayerDataManager playerDataManager
   ) {
      this.plugin = plugin;
      this.config = config;
      this.messages = messages;
      this.arenaManager = arenaManager;
      this.mapPoolManager = mapPoolManager;
      this.kitManager = kitManager;
      this.requestManager = requestManager;
      this.cooldownManager = cooldownManager;
      this.displayManager = displayManager;
      this.resultService = resultService;
      this.soundService = soundService;
      this.statsManager = statsManager;
      this.playerDataManager = playerDataManager;
   }

   public void bindSpectatorManager(SpectatorManager spectatorManager) {
      this.spectatorManager = spectatorManager;
   }

   public void bindQueueManager(QueueManager queueManager) {
      this.queueManager = queueManager;
   }

   public void bindPlayAgainService(PlayAgainService playAgainService) {
      this.playAgainService = playAgainService;
   }

   public void bindTeamDuelManager(TeamDuelManager teamDuelManager) {
      this.teamDuelManager = teamDuelManager;
   }

   public void bindCelebrationService(CelebrationService celebrationService) {
      this.celebrationService = celebrationService;
   }

   public Collection<DuelSession> getActiveSessions() {
      return new HashSet<>(this.sessionsByPlayer.values());
   }

   public Optional<DuelSession> findSession(UUID sessionId) {
      return this.getActiveSessions().stream().filter(s -> s.sessionId().equals(sessionId)).findFirst();
   }

   public Optional<DuelSession> findSessionByShortId(String shortId) {
      if (shortId != null && !shortId.isBlank()) {
         String key = shortId.toLowerCase(Locale.ROOT);
         return this.getActiveSessions().stream().filter(s -> s.shortId().equalsIgnoreCase(key)).findFirst();
      } else {
         return Optional.empty();
      }
   }

   public SpectatorManager spectatorManager() {
      return this.spectatorManager;
   }

   public DuelResultService resultService() {
      return this.resultService;
   }

   public boolean isPostFight(UUID playerId) {
      return this.getSession(playerId).map(session -> session.state() == DuelState.POST_FIGHT).orElse(false);
   }

   public boolean isInDuel(UUID playerId) {
      return this.sessionsByPlayer.containsKey(playerId) || this.teamDuelManager != null && this.teamDuelManager.isInTeamDuel(playerId);
   }

   public Optional<DuelSession> getSession(UUID playerId) {
      return Optional.ofNullable(this.sessionsByPlayer.get(playerId));
   }

   public void startFromRequest(Player target) {
      Optional<DuelRequest> requestOpt = this.requestManager.getIncoming(target.getUniqueId());
      if (requestOpt.isEmpty()) {
         this.messages.send(target, "request.no-pending");
      } else {
         DuelRequest request = requestOpt.get();
         if (request.isExpired()) {
            this.requestManager.removeIncoming(target.getUniqueId());
            this.messages.send(target, "request.no-pending");
         } else {
            Player challenger = Bukkit.getPlayer(request.challengerId());
            if (challenger == null || !challenger.isOnline()) {
               this.requestManager.removeIncoming(target.getUniqueId());
               this.messages.send(target, "player-offline", Map.of("player", "?"));
            } else if (!this.isInDuel(challenger.getUniqueId()) && !this.isInDuel(target.getUniqueId())) {
               Optional<Kit> kitOpt = this.kitManager.find(request.kitName());
               if (kitOpt.isEmpty()) {
                  this.requestManager.removeIncoming(target.getUniqueId());
                  this.messages.send(target, "kit.not-found", Map.of("kit", request.kitName()));
               } else {
                  Kit kit = kitOpt.get();
                  String requestedArena = request.arenaName();
                  Optional<Arena> arenaOpt;
                  if (requestedArena != null && !requestedArena.isBlank()) {
                     arenaOpt = this.arenaManager.find(requestedArena.toLowerCase(Locale.ROOT));
                     if (arenaOpt.isEmpty() || !arenaOpt.get().isFree() || !arenaOpt.get().enabled()) {
                        this.messages.send(challenger, "arena.not-found", Map.of("arena", requestedArena));
                        this.messages.send(target, "arena.not-found", Map.of("arena", requestedArena));
                        this.requestManager.removeIncoming(target.getUniqueId());
                        return;
                     }
                  } else {
                     arenaOpt = this.mapPoolManager.findFreeArenaForKit(kit.name(), this.arenaManager);
                  }

                  if (arenaOpt.isEmpty()) {
                     String duelWorldName = this.config.duelWorld();
                     World duelWorld = Bukkit.getWorld(duelWorldName);
                     Map<String, String> poolMsg = Map.of("pool", kit.name(), "kit", kit.displayName());
                     if (duelWorld == null) {
                        this.messages.send(target, "duel-world.not-loaded", Map.of("world", duelWorldName));
                        this.messages.send(challenger, "duel-world.not-loaded", Map.of("world", duelWorldName));
                     } else {
                        this.messages.send(target, "arena.none-free-pool", poolMsg);
                        this.messages.send(challenger, "arena.none-free-pool", poolMsg);
                     }
                  } else {
                     this.requestManager.removeIncoming(target.getUniqueId());
                     this.messages.send(challenger, "request.accepted-challenger", Map.of("target", target.getName()));
                     this.messages.send(target, "request.accepted-target", Map.of("challenger", challenger.getName()));
                     this.playerDataManager.setLastKit(challenger.getUniqueId(), kit.name());
                     this.playerDataManager.setLastKit(target.getUniqueId(), kit.name());
                     boolean ranked = request.ranked();
                     this.playerDataManager.setLastGameMode(challenger.getUniqueId(), ranked ? "ranked" : "normal");
                     this.playerDataManager.setLastGameMode(target.getUniqueId(), ranked ? "ranked" : "normal");
                     this.beginDuel(challenger, target, kit, arenaOpt.get(), ranked);
                  }
               }
            } else {
               this.messages.send(target, "duel.already-in-duel");
            }
         }
      }
   }

   public void deny(Player target) {
      Optional<DuelRequest> requestOpt = this.requestManager.removeIncoming(target.getUniqueId());
      if (requestOpt.isEmpty()) {
         this.messages.send(target, "request.no-pending");
      } else {
         DuelRequest request = requestOpt.get();
         Player challenger = Bukkit.getPlayer(request.challengerId());
         this.messages.send(target, "request.denied-target", Map.of("challenger", challenger != null ? challenger.getName() : "?"));
         if (challenger != null) {
            this.messages.send(challenger, "request.denied-challenger", Map.of("target", target.getName()));
         }
      }
   }

   private void beginDuel(Player playerOne, Player playerTwo, Kit kit, Arena arena, boolean ranked) {
      if (this.queueManager != null) {
         this.queueManager.dequeue(playerOne.getUniqueId());
         this.queueManager.dequeue(playerTwo.getUniqueId());
         this.queueManager.recordOpponents(playerOne.getUniqueId(), playerTwo.getUniqueId());
      }

      PlayerSnapshot snap1 = PlayerSnapshot.capture(playerOne);
      PlayerSnapshot snap2 = PlayerSnapshot.capture(playerTwo);
      DuelSession session = new DuelSession(playerOne, playerTwo, kit.name(), arena, snap1, snap2, ranked);
      this.sessionsByPlayer.put(playerOne.getUniqueId(), session);
      this.sessionsByPlayer.put(playerTwo.getUniqueId(), session);
      this.displayManager.beginSession(session, playerOne, playerTwo);
      this.displayManager.showMatchFound(playerOne, playerTwo, kit.displayName());
      this.requestManager.clearFor(playerOne.getUniqueId());
      this.requestManager.clearFor(playerTwo.getUniqueId());
      Location spawn1 = arena.spawn1();
      Location spawn2 = arena.spawn2();
      session.setArenaSpawn(playerOne.getUniqueId(), spawn1);
      session.setArenaSpawn(playerTwo.getUniqueId(), spawn2);
      int reteleportDelay = this.config.arenaReteleportDelayTicks();
      Runnable startCountdown = () -> {
         if (this.sessionsByPlayer.containsKey(playerOne.getUniqueId())) {
            this.kitManager.applyForPlayer(playerOne, kit, this.playerDataManager);
            this.kitManager.applyForPlayer(playerTwo, kit, this.playerDataManager);
            this.enforceSurvival(playerOne);
            this.enforceSurvival(playerTwo);
            playerOne.setHealth(playerOne.getMaxHealth());
            playerTwo.setHealth(playerTwo.getMaxHealth());
            restoreHunger(playerOne);
            restoreHunger(playerTwo);
            session.setCountdownAnchor(playerOne.getUniqueId(), playerOne.getLocation());
            session.setCountdownAnchor(playerTwo.getUniqueId(), playerTwo.getLocation());
            this.runCountdown(session, playerOne, playerTwo, arena);
         }
      };
      Runnable secondTeleport = () -> {
         if (this.sessionsByPlayer.containsKey(playerOne.getUniqueId())) {
            this.forceArenaSpawn(session, playerOne);
            this.forceArenaSpawn(session, playerTwo);
            startCountdown.run();
         }
      };
      this.teleportToArena(session, playerOne, spawn1);
      this.teleportToArena(session, playerTwo, spawn2);
      Bukkit.getScheduler().runTaskLater(this.plugin, secondTeleport, (long)reteleportDelay);
   }

   public void forceArenaSpawn(DuelSession session, Player player) {
      Location spawn = session.arenaSpawn(player.getUniqueId());
      if (spawn != null && spawn.getWorld() != null) {
         this.teleportToArena(session, player, spawn);
      }
   }

   private void teleportToArena(DuelSession session, Player player, Location location) {
      if (location != null) {
         session.setPluginTeleport(true);
         player.teleport(location);
         Bukkit.getScheduler().runTaskLater(this.plugin, () -> session.setPluginTeleport(false), 2L);
      }
   }

   private void runCountdown(DuelSession session, Player p1, Player p2, Arena arena) {
      int seconds = this.config.countdownSeconds();
      if (seconds <= 0) {
         this.activate(session, p1, p2, arena);
      } else {
         int[] remaining = new int[]{seconds};
         BukkitTask[] taskRef = new BukkitTask[1];
         taskRef[0] = Bukkit.getScheduler().runTaskTimer(this.plugin, () -> {
            if (!this.sessionsByPlayer.containsKey(p1.getUniqueId())) {
               taskRef[0].cancel();
            } else if (remaining[0] > 0) {
               this.enforceSurvival(p1);
               this.enforceSurvival(p2);
               this.displayManager.showCountdownTick(p1, p2, remaining[0]);
               remaining[0]--;
            } else {
               taskRef[0].cancel();
               this.activate(session, p1, p2, arena);
            }
         }, 20L, 20L);
      }
   }

   private void activate(DuelSession session, Player p1, Player p2, Arena arena) {
      session.clearCountdownAnchors();
      session.markActive();
      session.setState(DuelState.ACTIVE);
      this.enforceSurvival(p1);
      this.enforceSurvival(p2);
      this.applyStartBuffs(session, p1, p2);
      this.displayManager.showCountdownStart(p1, p2);
      this.messages.send(p1, "duel.started", Map.of("arena", arena.displayName()));
      this.messages.send(p2, "duel.started", Map.of("arena", arena.displayName()));
   }

   private void applyStartBuffs(DuelSession session, Player p1, Player p2) {
      this.kitManager.find(session.kitName()).ifPresent(kit -> {
         if (!kit.startBuffs().isEmpty()) {
            applyStartBuffsToPlayer(p1, kit.startBuffs());
            applyStartBuffsToPlayer(p2, kit.startBuffs());
         }
      });
   }

   private static void applyStartBuffsToPlayer(Player player, List<PotionEffect> effects) {
      if (player != null && effects != null && !effects.isEmpty()) {
         for (PotionEffect effect : effects) {
            if (effect != null && effect.getType() != null) {
               player.removePotionEffect(effect.getType());
               player.addPotionEffect(effect);
            }
         }
      }
   }

   public void endWithWinner(DuelSession session, Player winner, Player loser, String winKey, String loseKey) {
      if (session.state() != DuelState.ENDING && session.state() != DuelState.POST_FIGHT) {
         this.concludeDuel(session, winner, loser, true);
         Map<String, String> placeholders = new HashMap<>();
         placeholders.put("winner", winner.getName());
         placeholders.put("loser", loser.getName());
         placeholders.put("player", loser.getName());
         this.messages.send(winner, winKey, placeholders);
         this.messages.send(loser, loseKey, placeholders);
         this.finishPlayerAfterDuel(winner, session);
         this.finishPlayerAfterDuel(loser, session);
         this.publishResultsIfPresent(session);
         this.scheduleSpectatorRemoval(session);
         this.cleanupArena(session);
         this.unregister(session);
      }
   }

   public void handleDeath(Player dead) {
      this.getSession(dead.getUniqueId()).ifPresent(session -> {
         if (session.state() == DuelState.ACTIVE || session.state() == DuelState.COUNTDOWN) {
            UUID opponentId = session.opponent(dead.getUniqueId());
            if (opponentId != null) {
               Player winner = Bukkit.getPlayer(opponentId);
               if (winner == null) {
                  this.forceEnd(session);
               } else {
                  this.endDuelByKill(session, winner, dead);
               }
            }
         }
      });
   }

   private void endDuelByKill(DuelSession session, Player winner, Player dead) {
      if (session.state() != DuelState.ENDING && session.state() != DuelState.POST_FIGHT) {
         if (this.celebrationService != null) {
            this.celebrationService.playKillEffect(winner, dead.getLocation(), session.kitName());
         }

         this.concludeDuel(session, winner, dead, true);
         Map<String, String> placeholders = new HashMap<>();
         placeholders.put("winner", winner.getName());
         placeholders.put("loser", dead.getName());
         placeholders.put("player", dead.getName());
         this.messages.send(winner, "duel.win", placeholders);
         this.messages.send(dead, "duel.lose", placeholders);
         int delaySeconds = this.config.postKillDelaySeconds();
         if (delaySeconds <= 0) {
            this.respawnLoserOnArena(session, dead);
            this.completePostFightGrace(session, winner, dead);
         } else {
            session.beginPostFight(winner.getUniqueId(), dead.getUniqueId());
            this.storePostFightRespawnLocation(session, dead);
            Bukkit.getScheduler().runTask(this.plugin, () -> this.respawnLoserOnArena(session, dead));
            // Rev. 2: the «Играть снова» paper appears the moment the match is decided,
            // while the players are still on the arena (celebration stage).
            if (this.playAgainService != null) {
               this.playAgainService.givePlayAgainItem(winner, session.kitName());
               this.playAgainService.givePlayAgainItem(dead, session.kitName());
            }

            this.schedulePostFightFinish(session, winner, dead, delaySeconds);
         }
      }
   }

   private void storePostFightRespawnLocation(DuelSession session, Player loser) {
      Location stay = loser.getLocation();
      if (stay.getWorld() == null) {
         stay = session.arenaSpawn(loser.getUniqueId());
      }

      if (stay != null) {
         this.postFightRespawnLocations.put(loser.getUniqueId(), stay.clone());
      }
   }

   private void respawnLoserOnArena(DuelSession session, Player loser) {
      if (loser.isOnline()) {
         this.instantRespawn(loser);
         Location stay = this.postFightRespawnLocations.get(loser.getUniqueId());
         if (stay == null) {
            stay = session.arenaSpawn(loser.getUniqueId());
         }

         this.applyPostFightLoser(loser, stay);
      }
   }

   public void applyPostFightLoser(Player player, Location arenaLocation) {
      if (arenaLocation != null && arenaLocation.getWorld() != null) {
         this.sessionSetPluginTeleport(player, arenaLocation);
      }

      player.setGameMode(GameMode.SPECTATOR);
      player.setAllowFlight(true);
      player.setFlying(false);
   }

   private void sessionSetPluginTeleport(Player player, Location location) {
      this.getSession(player.getUniqueId()).ifPresent(session -> {
         session.setPluginTeleport(true);
         player.teleport(location);
         Bukkit.getScheduler().runTaskLater(this.plugin, () -> session.setPluginTeleport(false), 2L);
      });
   }

   public Location getPostFightRespawnLocation(UUID playerId) {
      Location loc = this.postFightRespawnLocations.get(playerId);
      return loc == null ? null : loc.clone();
   }

   private void schedulePostFightFinish(DuelSession session, Player winner, Player loser, int delaySeconds) {
      this.cancelPostFightTask(session.sessionId());
      BukkitTask task = Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
         this.postFightFinishTasks.remove(session.sessionId());
         if (this.sessionsByPlayer.containsKey(winner.getUniqueId())) {
            Player w = Bukkit.getPlayer(winner.getUniqueId());
            Player l = Bukkit.getPlayer(loser.getUniqueId());
            this.completePostFightGrace(session, w, l);
         }
      }, (long)delaySeconds * 20L);
      this.postFightFinishTasks.put(session.sessionId(), task);
   }

   private void cancelPostFightTask(UUID sessionId) {
      BukkitTask task = this.postFightFinishTasks.remove(sessionId);
      if (task != null) {
         task.cancel();
      }
   }

   private void completePostFightGrace(DuelSession session, Player winner, Player loser) {
      this.cancelPostFightTask(session.sessionId());
      if (loser != null) {
         this.postFightRespawnLocations.remove(loser.getUniqueId());
      }

      if (winner != null) {
         this.postFightRespawnLocations.remove(winner.getUniqueId());
      }

      if (loser != null && loser.isOnline()) {
         this.enforceSurvival(loser);
         this.finishPlayerAfterDuel(loser, session);
      }

      if (winner != null && winner.isOnline()) {
         this.finishPlayerAfterDuel(winner, session);
      }

      this.publishResultsIfPresent(session);
      if (this.spectatorManager != null) {
         this.spectatorManager.removeAllFromSession(session, true);
      }

      this.cleanupArena(session);
      this.unregister(session);
   }

   private void scheduleSpectatorRemoval(DuelSession session) {
      if (this.spectatorManager != null && !session.spectators().isEmpty()) {
         this.spectatorManager.scheduleRemovalAfterDuel(session, this.config.postKillDelaySeconds());
      }
   }

   private static void restoreHunger(Player player) {
      player.setFoodLevel(20);
      player.setSaturation(0.0F);
   }

   private void publishResultsIfPresent(DuelSession session) {
      MatchResult result = session.matchResult();
      if (result != null) {
         this.resultService.publishResultsChat(result);
      }
   }

   private void abortPostFight(DuelSession session) {
      this.cancelPostFightTask(session.sessionId());
      Player winner = session.postFightWinnerId() != null ? Bukkit.getPlayer(session.postFightWinnerId()) : null;
      Player loser = session.postFightLoserId() != null ? Bukkit.getPlayer(session.postFightLoserId()) : null;
      this.completePostFightGrace(session, winner, loser);
   }

   private void instantRespawn(Player player) {
      if (player.isDead()) {
         player.spigot().respawn();
         player.setHealth(player.getMaxHealth());
         player.setFoodLevel(20);
         player.setFireTicks(0);
         this.enforceSurvival(player);
      }
   }

   public void handleLeave(Player leaver) {
      if (this.teamDuelManager != null && this.teamDuelManager.isInTeamDuel(leaver.getUniqueId())) {
         this.teamDuelManager.handleLeave(leaver);
      } else {
         Optional<DuelSession> postFight = this.getSession(leaver.getUniqueId()).filter(session -> session.state() == DuelState.POST_FIGHT);
         if (postFight.isPresent()) {
            this.abortPostFight(postFight.get());
         } else {
            this.getSession(leaver.getUniqueId()).ifPresent(session -> {
               UUID opponentId = session.opponent(leaver.getUniqueId());
               Player opponent = opponentId != null ? Bukkit.getPlayer(opponentId) : null;
               this.messages.send(leaver, "duel.leave");
               if (opponent != null) {
                  this.endWithWinner(session, opponent, leaver, "duel.opponent-left", "duel.lose");
               } else {
                  this.forceEnd(session);
               }
            });
         }
      }
   }

   public void handleLogout(Player player) {
      if (this.teamDuelManager != null && this.teamDuelManager.isInTeamDuel(player.getUniqueId())) {
         this.teamDuelManager.handleLogout(player);
      } else {
         Optional<DuelSession> postFight = this.getSession(player.getUniqueId()).filter(session -> session.state() == DuelState.POST_FIGHT);
         if (postFight.isPresent()) {
            this.abortPostFight(postFight.get());
         } else if (!this.config.logoutForfeit()) {
            this.forceEndForPlayer(player);
         } else {
            this.getSession(player.getUniqueId()).ifPresent(session -> {
               UUID opponentId = session.opponent(player.getUniqueId());
               Player opponent = opponentId != null ? Bukkit.getPlayer(opponentId) : null;
               if (opponent != null) {
                  this.messages.send(opponent, "duel.opponent-logout", Map.of("player", player.getName()));
                  this.endWithWinner(session, opponent, player, "duel.opponent-left", "duel.forfeit-logout");
               } else {
                  this.forceEnd(session);
               }
            });
         }
      }
   }

   private void concludeDuel(DuelSession session, Player winner, Player loser, boolean playSounds) {
      session.setState(DuelState.ENDING);
      session.clearCountdownAnchors();
      long duration = session.fightDurationMs();
      StatsManager.EloResult elo = this.statsManager.recordDuel(winner, loser, session.kitName(), duration);
      String kitDisplay = this.kitManager.find(session.kitName()).map(kit -> kit.displayName()).orElse(session.kitName());
      MatchResult result = new MatchResult(
         session.sessionId(),
         winner.getName(),
         loser.getName(),
         session.kitName(),
         kitDisplay,
         session.arena().name(),
         session.arena().displayName(),
         duration,
         elo.winnerChange(),
         elo.loserChange(),
         elo.winnerElo(),
         elo.loserElo()
      );
      session.setMatchResult(result);
      this.resultService.handleEnd(session, winner, loser, result);
      this.displayManager.clearScoreboards(session);
      if (playSounds) {
         this.soundService.playVictory(winner);
         this.soundService.playDefeat(loser);
      }
   }

   public void forceEnd(DuelSession session) {
      if (session.state() == DuelState.POST_FIGHT) {
         this.abortPostFight(session);
      } else {
         session.setState(DuelState.ENDING);
         session.clearCountdownAnchors();
         this.cancelPostFightTask(session.sessionId());
         if (this.spectatorManager != null) {
            this.spectatorManager.removeAllFromSession(session, true);
         }

         Player p1 = Bukkit.getPlayer(session.playerOneId());
         Player p2 = Bukkit.getPlayer(session.playerTwoId());
         if (p1 != null) {
            this.finishPlayerAfterDuel(p1, session);
         }

         if (p2 != null) {
            this.finishPlayerAfterDuel(p2, session);
         }

         this.cleanupArena(session);
         this.unregister(session);
      }
   }

   public void forceEndForPlayer(Player player) {
      this.getSession(player.getUniqueId()).ifPresent(session -> {
         Player opponent = Bukkit.getPlayer(session.opponent(player.getUniqueId()));
         if (opponent != null) {
            this.endWithWinner(session, opponent, player, "duel.opponent-left", "duel.lose");
         } else {
            this.forceEnd(session);
         }
      });
   }

   private void finishPlayerAfterDuel(Player player, DuelSession session) {
      this.enforceSurvival(player);
      PlayerSnapshot snapshot = session.snapshot(player.getUniqueId());
      if (snapshot != null) {
         snapshot.restoreInventoryOnly(player);
      }

      this.enforceSurvival(player);
      Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
         if (snapshot != null && snapshot.world() != null) {
            Location back = snapshot.location();
            if (back.getWorld() != null) {
               player.teleport(back);
            }
         }

         this.enforceSurvival(player);
         MonaDuels patt0$temp = this.plugin;
         if (patt0$temp instanceof MonaDuels) {
            patt0$temp.lobbyLayoutService().applyLayout(player);
            if (this.playAgainService != null) {
               this.playAgainService.givePlayAgainItem(player, session.kitName());
               // Clicked «Играть снова» while still on the arena → queue now that we're back.
               if (this.queueManager != null && this.playAgainService.consumePending(player.getUniqueId())) {
                  this.queueManager.enqueue(player, session.kitName());
               }
            }
         }
      }, 3L);
   }

   public void enforceSurvival(Player player) {
      if (player.getGameMode() != GameMode.SURVIVAL) {
         player.setGameMode(GameMode.SURVIVAL);
      }
   }

   private void cleanupArena(DuelSession session) {
      if (this.config.trackPlacedBlocks()) {
         session.blockTracker().cleanupPlaced();
      }

      if (this.config.trackBrokenBlocks() && this.config.restoreBrokenBlocks()) {
         session.blockTracker().restoreBroken();
      }

      this.arenaManager.release(session.arena());
   }

   private void unregister(DuelSession session) {
      Player p1 = Bukkit.getPlayer(session.playerOneId());
      Player p2 = Bukkit.getPlayer(session.playerTwoId());
      this.displayManager.endSession(session, p1, p2);
      this.sessionsByPlayer.remove(session.playerOneId());
      this.sessionsByPlayer.remove(session.playerTwoId());
   }

   public void startDirectDuel(Player playerOne, Player playerTwo, Kit kit, Arena arena, boolean ranked) {
      if (this.queueManager != null) {
         this.queueManager.dequeue(playerOne.getUniqueId());
         this.queueManager.dequeue(playerTwo.getUniqueId());
         this.queueManager.recordOpponents(playerOne.getUniqueId(), playerTwo.getUniqueId());
      }

      PlayerSnapshot snap1 = PlayerSnapshot.capture(playerOne);
      PlayerSnapshot snap2 = PlayerSnapshot.capture(playerTwo);
      DuelSession session = new DuelSession(playerOne, playerTwo, kit.name(), arena, snap1, snap2, ranked);
      this.sessionsByPlayer.put(playerOne.getUniqueId(), session);
      this.sessionsByPlayer.put(playerTwo.getUniqueId(), session);
      this.displayManager.beginSession(session, playerOne, playerTwo);
      this.displayManager.showMatchFound(playerOne, playerTwo, kit.displayName());
      this.requestManager.clearFor(playerOne.getUniqueId());
      this.requestManager.clearFor(playerTwo.getUniqueId());
      Location spawn1 = arena.spawn1();
      Location spawn2 = arena.spawn2();
      session.setArenaSpawn(playerOne.getUniqueId(), spawn1);
      session.setArenaSpawn(playerTwo.getUniqueId(), spawn2);
      int reteleportDelay = this.config.arenaReteleportDelayTicks();
      Runnable startCountdown = () -> {
         if (this.sessionsByPlayer.containsKey(playerOne.getUniqueId())) {
            this.kitManager.applyForPlayer(playerOne, kit, this.playerDataManager);
            this.kitManager.applyForPlayer(playerTwo, kit, this.playerDataManager);
            this.enforceSurvival(playerOne);
            this.enforceSurvival(playerTwo);
            playerOne.setHealth(playerOne.getMaxHealth());
            playerTwo.setHealth(playerTwo.getMaxHealth());
            restoreHunger(playerOne);
            restoreHunger(playerTwo);
            session.setCountdownAnchor(playerOne.getUniqueId(), playerOne.getLocation());
            session.setCountdownAnchor(playerTwo.getUniqueId(), playerTwo.getLocation());
            this.runCountdown(session, playerOne, playerTwo, arena);
         }
      };
      Runnable secondTeleport = () -> {
         if (this.sessionsByPlayer.containsKey(playerOne.getUniqueId())) {
            this.forceArenaSpawn(session, playerOne);
            this.forceArenaSpawn(session, playerTwo);
            startCountdown.run();
         }
      };
      this.teleportToArena(session, playerOne, spawn1);
      this.teleportToArena(session, playerTwo, spawn2);
      Bukkit.getScheduler().runTaskLater(this.plugin, secondTeleport, (long)reteleportDelay);
   }

   public void shutdown() {
      for (BukkitTask task : this.postFightFinishTasks.values()) {
         task.cancel();
      }

      this.postFightFinishTasks.clear();

      for (DuelSession session : new HashSet<>(this.sessionsByPlayer.values())) {
         this.forceEnd(session);
      }

      this.sessionsByPlayer.clear();
      this.postFightRespawnLocations.clear();
   }
}
