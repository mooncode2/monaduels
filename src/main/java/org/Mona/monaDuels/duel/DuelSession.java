package org.Mona.monaDuels.duel;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import org.Mona.monaDuels.arena.Arena;
import org.Mona.monaDuels.block.ArenaBlockTracker;
import org.Mona.monaDuels.player.PlayerSnapshot;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class DuelSession {
   private final UUID sessionId;
   private final UUID playerOneId;
   private final UUID playerTwoId;
   private final String kitName;
   private final Arena arena;
   private final ArenaBlockTracker blockTracker;
   private final PlayerSnapshot snapshotOne;
   private final PlayerSnapshot snapshotTwo;
   private final long createdAt;
   private final Set<UUID> spectators = new CopyOnWriteArraySet<>();
   private DuelState state;
   private UUID postFightWinnerId;
   private UUID postFightLoserId;
   private boolean pluginTeleport;
   private long activeAt;
   private final Map<UUID, Location> countdownAnchors = new ConcurrentHashMap<>();
   private final Map<UUID, Location> arenaSpawns = new ConcurrentHashMap<>();
   private final boolean ranked;
   private MatchResult matchResult;

   public DuelSession(Player playerOne, Player playerTwo, String kitName, Arena arena, PlayerSnapshot snapshotOne, PlayerSnapshot snapshotTwo, boolean ranked) {
      this.ranked = ranked;
      this.sessionId = UUID.randomUUID();
      this.playerOneId = playerOne.getUniqueId();
      this.playerTwoId = playerTwo.getUniqueId();
      this.kitName = kitName;
      this.arena = arena;
      this.blockTracker = new ArenaBlockTracker();
      this.snapshotOne = snapshotOne;
      this.snapshotTwo = snapshotTwo;
      this.createdAt = System.currentTimeMillis();
      this.state = DuelState.COUNTDOWN;
      this.pluginTeleport = false;
   }

   public UUID sessionId() {
      return this.sessionId;
   }

   public boolean ranked() {
      return this.ranked;
   }

   public String shortId() {
      return this.sessionId.toString().substring(0, 8);
   }

   public UUID playerOneId() {
      return this.playerOneId;
   }

   public UUID playerTwoId() {
      return this.playerTwoId;
   }

   public boolean involves(UUID playerId) {
      return this.playerOneId.equals(playerId) || this.playerTwoId.equals(playerId);
   }

   public UUID opponent(UUID playerId) {
      if (this.playerOneId.equals(playerId)) {
         return this.playerTwoId;
      } else {
         return this.playerTwoId.equals(playerId) ? this.playerOneId : null;
      }
   }

   public String kitName() {
      return this.kitName;
   }

   public Arena arena() {
      return this.arena;
   }

   public ArenaBlockTracker blockTracker() {
      return this.blockTracker;
   }

   public PlayerSnapshot snapshot(UUID playerId) {
      if (this.playerOneId.equals(playerId)) {
         return this.snapshotOne;
      } else {
         return this.playerTwoId.equals(playerId) ? this.snapshotTwo : null;
      }
   }

   public DuelState state() {
      return this.state;
   }

   public void setState(DuelState state) {
      this.state = state;
   }

   public void beginPostFight(UUID winnerId, UUID loserId) {
      this.postFightWinnerId = winnerId;
      this.postFightLoserId = loserId;
      this.state = DuelState.POST_FIGHT;
   }

   public UUID postFightWinnerId() {
      return this.postFightWinnerId;
   }

   public UUID postFightLoserId() {
      return this.postFightLoserId;
   }

   public boolean isPostFightLoser(UUID playerId) {
      return this.postFightLoserId != null && this.postFightLoserId.equals(playerId);
   }

   public void markActive() {
      if (this.activeAt <= 0L) {
         this.activeAt = System.currentTimeMillis();
      }
   }

   public long fightDurationMs() {
      long end = System.currentTimeMillis();
      long start = this.activeAt > 0L ? this.activeAt : this.createdAt;
      return Math.max(0L, end - start);
   }

   public Set<UUID> spectators() {
      return this.spectators;
   }

   public void addSpectator(UUID spectatorId) {
      this.spectators.add(spectatorId);
   }

   public void removeSpectator(UUID spectatorId) {
      this.spectators.remove(spectatorId);
   }

   public boolean isPluginTeleport() {
      return this.pluginTeleport;
   }

   public void setPluginTeleport(boolean pluginTeleport) {
      this.pluginTeleport = pluginTeleport;
   }

   public void setCountdownAnchor(UUID playerId, Location location) {
      if (location != null) {
         this.countdownAnchors.put(playerId, location.clone());
      }
   }

   public Location countdownAnchor(UUID playerId) {
      Location loc = this.countdownAnchors.get(playerId);
      return loc == null ? null : loc.clone();
   }

   public void clearCountdownAnchors() {
      this.countdownAnchors.clear();
   }

   public void setArenaSpawn(UUID playerId, Location location) {
      if (location != null) {
         this.arenaSpawns.put(playerId, location.clone());
      }
   }

   public Location arenaSpawn(UUID playerId) {
      Location loc = this.arenaSpawns.get(playerId);
      return loc == null ? null : loc.clone();
   }

   public void setMatchResult(MatchResult matchResult) {
      this.matchResult = matchResult;
   }

   public MatchResult matchResult() {
      return this.matchResult;
   }
}
