package org.Mona.monaDuels.party;

import java.util.UUID;

public final class PartyDuelRequest {
   private final UUID challengerLeaderId;
   private final UUID targetLeaderId;
   private final String kitName;
   private final boolean ranked;
   private final long createdAt;
   private final long expiresAt;

   public PartyDuelRequest(UUID challengerLeaderId, UUID targetLeaderId, String kitName, boolean ranked, long createdAt, long expiresAt) {
      this.challengerLeaderId = challengerLeaderId;
      this.targetLeaderId = targetLeaderId;
      this.kitName = kitName;
      this.ranked = ranked;
      this.createdAt = createdAt;
      this.expiresAt = expiresAt;
   }

   public UUID challengerLeaderId() {
      return this.challengerLeaderId;
   }

   public UUID targetLeaderId() {
      return this.targetLeaderId;
   }

   public String kitName() {
      return this.kitName;
   }

   public boolean ranked() {
      return this.ranked;
   }

   public boolean isExpired() {
      return System.currentTimeMillis() > this.expiresAt;
   }
}
