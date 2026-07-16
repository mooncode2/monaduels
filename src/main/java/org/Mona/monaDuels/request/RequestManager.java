package org.Mona.monaDuels.request;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.Mona.monaDuels.config.ConfigManager;
import org.Mona.monaDuels.config.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class RequestManager {
   private final ConfigManager configManager;
   private final MessageService messages;
   private final DuelRequestMessenger requestMessenger;
   private final Map<UUID, DuelRequest> incomingByTarget = new ConcurrentHashMap<>();
   private final Map<UUID, DuelRequest> outgoingByChallenger = new ConcurrentHashMap<>();

   public RequestManager(ConfigManager configManager, MessageService messages, DuelRequestMessenger requestMessenger) {
      this.configManager = configManager;
      this.messages = messages;
      this.requestMessenger = requestMessenger;
   }

   public boolean hasIncoming(UUID targetId) {
      this.cleanupExpired(targetId);
      return this.incomingByTarget.containsKey(targetId);
   }

   public boolean hasOutgoing(UUID challengerId) {
      this.cleanupExpired(challengerId);
      return this.outgoingByChallenger.containsKey(challengerId);
   }

   public void create(Player challenger, Player target, String kitName) {
      this.create(challenger, target, kitName, false);
   }

   public void create(Player challenger, Player target, String kitName, boolean ranked) {
      this.create(challenger, target, kitName, null, ranked);
   }

   public void create(Player challenger, Player target, String kitName, String arenaName, boolean ranked) {
      long now = System.currentTimeMillis();
      long expires = now + (long)this.configManager.requestTimeoutSeconds() * 1000L;
      DuelRequest request = new DuelRequest(challenger.getUniqueId(), target.getUniqueId(), kitName, arenaName, ranked, now, expires);
      this.incomingByTarget.put(target.getUniqueId(), request);
      this.outgoingByChallenger.put(challenger.getUniqueId(), request);
      this.requestMessenger.sendRequest(challenger, target, kitName, ranked);
   }

   public Optional<DuelRequest> getIncoming(UUID targetId) {
      this.cleanupExpired(targetId);
      return Optional.ofNullable(this.incomingByTarget.get(targetId));
   }

   public Optional<DuelRequest> getOutgoing(UUID challengerId) {
      this.cleanupExpired(challengerId);
      return Optional.ofNullable(this.outgoingByChallenger.get(challengerId));
   }

   public Optional<DuelRequest> removeIncoming(UUID targetId) {
      DuelRequest request = this.incomingByTarget.remove(targetId);
      if (request != null) {
         this.outgoingByChallenger.remove(request.challengerId());
      }

      return Optional.ofNullable(request);
   }

   public void clearFor(UUID playerId) {
      this.incomingByTarget.remove(playerId);
      this.outgoingByChallenger.remove(playerId);
      this.incomingByTarget.values().removeIf(r -> r.challengerId().equals(playerId) || r.targetId().equals(playerId));
      this.outgoingByChallenger.values().removeIf(r -> r.challengerId().equals(playerId) || r.targetId().equals(playerId));
   }

   private void cleanupExpired(UUID playerId) {
      DuelRequest incoming = this.incomingByTarget.get(playerId);
      if (incoming != null && incoming.isExpired()) {
         this.expire(incoming);
      }

      DuelRequest outgoing = this.outgoingByChallenger.get(playerId);
      if (outgoing != null && outgoing.isExpired()) {
         this.expire(outgoing);
      }
   }

   private void expire(DuelRequest request) {
      this.incomingByTarget.remove(request.targetId());
      this.outgoingByChallenger.remove(request.challengerId());
      Player challenger = Bukkit.getPlayer(request.challengerId());
      Player target = Bukkit.getPlayer(request.targetId());
      if (challenger != null) {
         this.messages.send(challenger, "request.expired-challenger", Map.of("target", target != null ? target.getName() : "?"));
      }

      if (target != null) {
         this.messages.send(target, "request.expired-target", Map.of("challenger", challenger != null ? challenger.getName() : "?"));
      }
   }
}
