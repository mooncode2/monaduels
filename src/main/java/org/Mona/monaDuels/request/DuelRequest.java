package org.Mona.monaDuels.request;

import java.util.UUID;

public final class DuelRequest {
   private final UUID challengerId;
   private final UUID targetId;
   private final String kitName;
   private final String arenaName;
   private final boolean ranked;
   private final long createdAt;
   private final long expiresAt;

   public DuelRequest(UUID challengerId, UUID targetId, String kitName, String arenaName, boolean ranked, long createdAt, long expiresAt) {
      this.challengerId = challengerId;
      this.targetId = targetId;
      this.kitName = kitName;
      this.arenaName = arenaName;
      this.ranked = ranked;
      this.createdAt = createdAt;
      this.expiresAt = expiresAt;
   }

   public UUID challengerId() {
      return this.challengerId;
   }

   public UUID targetId() {
      return this.targetId;
   }

   public String kitName() {
      return this.kitName;
   }

   public String arenaName() {
      return this.arenaName;
   }

   public boolean ranked() {
      return this.ranked;
   }

   public long createdAt() {
      return this.createdAt;
   }

   public long expiresAt() {
      return this.expiresAt;
   }

   public boolean isExpired() {
      return System.currentTimeMillis() >= this.expiresAt;
   }
}
