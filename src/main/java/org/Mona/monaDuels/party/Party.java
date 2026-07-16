package org.Mona.monaDuels.party;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class Party {
   private final UUID partyId;
   private UUID leaderId;
   private final Set<UUID> members = new LinkedHashSet<>();

   public Party(UUID partyId, UUID leaderId) {
      this.partyId = partyId;
      this.leaderId = leaderId;
      this.members.add(leaderId);
   }

   public UUID partyId() {
      return this.partyId;
   }

   public UUID leaderId() {
      return this.leaderId;
   }

   public void setLeaderId(UUID leaderId) {
      this.leaderId = leaderId;
   }

   public Set<UUID> members() {
      return Set.copyOf(this.members);
   }

   public int size() {
      return this.members.size();
   }

   public boolean isMember(UUID playerId) {
      return this.members.contains(playerId);
   }

   public boolean isLeader(UUID playerId) {
      return this.leaderId.equals(playerId);
   }

   public void addMember(UUID playerId) {
      this.members.add(playerId);
   }

   public void removeMember(UUID playerId) {
      this.members.remove(playerId);
   }
}
