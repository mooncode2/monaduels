package org.Mona.monaDuels.team;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.Mona.monaDuels.arena.Arena;
import org.Mona.monaDuels.block.ArenaBlockTracker;
import org.Mona.monaDuels.duel.DuelState;
import org.Mona.monaDuels.player.PlayerSnapshot;
import org.bukkit.Location;

public final class TeamDuelSession {
   private final UUID sessionId;
   private final List<UUID> teamA;
   private final List<UUID> teamB;
   private final String kitName;
   private final Arena arena;
   private final ArenaBlockTracker blockTracker;
   private final Map<UUID, PlayerSnapshot> snapshots = new ConcurrentHashMap<>();
   private final Set<UUID> eliminated = ConcurrentHashMap.newKeySet();
   private final Map<UUID, Location> arenaSpawns = new ConcurrentHashMap<>();
   private final Map<UUID, Location> countdownAnchors = new ConcurrentHashMap<>();
   private DuelState state = DuelState.COUNTDOWN;
   private boolean pluginTeleport;
   private long activeAt;

   public TeamDuelSession(List<UUID> teamA, List<UUID> teamB, String kitName, Arena arena) {
      this.sessionId = UUID.randomUUID();
      this.teamA = List.copyOf(teamA);
      this.teamB = List.copyOf(teamB);
      this.kitName = kitName;
      this.arena = arena;
      this.blockTracker = new ArenaBlockTracker();
   }

   public UUID sessionId() {
      return this.sessionId;
   }

   public List<UUID> teamA() {
      return this.teamA;
   }

   public List<UUID> teamB() {
      return this.teamB;
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

   public boolean involves(UUID playerId) {
      return this.teamA.contains(playerId) || this.teamB.contains(playerId);
   }

   public List<UUID> teamOf(UUID playerId) {
      if (this.teamA.contains(playerId)) {
         return this.teamA;
      } else {
         return this.teamB.contains(playerId) ? this.teamB : List.of();
      }
   }

   public List<UUID> oppositeTeam(UUID playerId) {
      if (this.teamA.contains(playerId)) {
         return this.teamB;
      } else {
         return this.teamB.contains(playerId) ? this.teamA : List.of();
      }
   }

   public List<UUID> allPlayers() {
      List<UUID> all = new ArrayList<>(this.teamA);
      all.addAll(this.teamB);
      return all;
   }

   public void putSnapshot(UUID playerId, PlayerSnapshot snapshot) {
      this.snapshots.put(playerId, snapshot);
   }

   public PlayerSnapshot snapshot(UUID playerId) {
      return this.snapshots.get(playerId);
   }

   public void markEliminated(UUID playerId) {
      this.eliminated.add(playerId);
   }

   public boolean isEliminated(UUID playerId) {
      return this.eliminated.contains(playerId);
   }

   public boolean isTeamFullyEliminated(List<UUID> team) {
      for (UUID id : team) {
         if (!this.eliminated.contains(id)) {
            return false;
         }
      }

      return true;
   }

   public DuelState state() {
      return this.state;
   }

   public void setState(DuelState state) {
      this.state = state;
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

   public void setCountdownAnchor(UUID playerId, Location location) {
      if (location != null) {
         this.countdownAnchors.put(playerId, location.clone());
      }
   }

   public void clearCountdownAnchors() {
      this.countdownAnchors.clear();
   }

   public boolean isPluginTeleport() {
      return this.pluginTeleport;
   }

   public void setPluginTeleport(boolean pluginTeleport) {
      this.pluginTeleport = pluginTeleport;
   }

   public void markActive() {
      if (this.activeAt <= 0L) {
         this.activeAt = System.currentTimeMillis();
      }
   }

   public Set<UUID> eliminated() {
      return Set.copyOf(this.eliminated);
   }
}
