package org.Mona.monaDuels.party;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.Mona.monaDuels.config.ConfigManager;
import org.Mona.monaDuels.config.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class PartyManager {
   private final ConfigManager config;
   private final MessageService messages;
   private final Map<UUID, Party> partiesById = new HashMap<>();
   private final Map<UUID, UUID> partyIdByPlayer = new HashMap<>();
   private final Map<UUID, UUID> pendingInvitePartyId = new HashMap<>();

   public PartyManager(ConfigManager config, MessageService messages) {
      this.config = config;
      this.messages = messages;
   }

   public int maxPartySize() {
      return Math.max(2, this.config.partyMaxSize());
   }

   public Optional<Party> getParty(UUID playerId) {
      UUID partyId = this.partyIdByPlayer.get(playerId);
      return partyId == null ? Optional.empty() : Optional.ofNullable(this.partiesById.get(partyId));
   }

   public Optional<Party> getPartyById(UUID partyId) {
      return Optional.ofNullable(this.partiesById.get(partyId));
   }

   public boolean hasParty(UUID playerId) {
      return this.partyIdByPlayer.containsKey(playerId);
   }

   public Party createParty(Player leader) {
      if (this.hasParty(leader.getUniqueId())) {
         this.messages.send(leader, "party.already-in-party");
         return this.partiesById.get(this.partyIdByPlayer.get(leader.getUniqueId()));
      } else {
         UUID id = UUID.randomUUID();
         Party party = new Party(id, leader.getUniqueId());
         this.partiesById.put(id, party);
         this.partyIdByPlayer.put(leader.getUniqueId(), id);
         this.messages.send(leader, "party.created");
         return party;
      }
   }

   public void disband(Party party) {
      for (UUID memberId : party.members()) {
         this.partyIdByPlayer.remove(memberId);
         Player member = Bukkit.getPlayer(memberId);
         if (member != null) {
            this.messages.send(member, "party.disbanded");
         }
      }

      this.partiesById.remove(party.partyId());
   }

   public void leave(Player player) {
      Optional<Party> partyOpt = this.getParty(player.getUniqueId());
      if (partyOpt.isEmpty()) {
         this.messages.send(player, "party.not-in-party");
      } else {
         Party party = partyOpt.get();
         party.removeMember(player.getUniqueId());
         this.partyIdByPlayer.remove(player.getUniqueId());
         this.messages.send(player, "party.left");
         if (party.size() == 0) {
            this.partiesById.remove(party.partyId());
         } else {
            if (party.isLeader(player.getUniqueId())) {
               UUID newLeader = party.members().iterator().next();
               party.setLeaderId(newLeader);
               Player leaderPlayer = Bukkit.getPlayer(newLeader);
               if (leaderPlayer != null) {
                  this.messages.send(leaderPlayer, "party.leader-promoted");
               }
            }

            this.broadcastToParty(party, "party.member-left", Map.of("player", player.getName()));
         }
      }
   }

   public boolean invite(Player leader, Player target) {
      Optional<Party> partyOpt = this.getParty(leader.getUniqueId());
      if (partyOpt.isEmpty()) {
         this.messages.send(leader, "party.not-in-party");
         return false;
      } else {
         Party party = partyOpt.get();
         if (!party.isLeader(leader.getUniqueId())) {
            this.messages.send(leader, "party.not-leader");
            return false;
         } else if (party.size() >= this.maxPartySize()) {
            this.messages.send(leader, "party.full", Map.of("size", String.valueOf(this.maxPartySize())));
            return false;
         } else if (this.hasParty(target.getUniqueId())) {
            this.messages.send(leader, "party.target-in-party", Map.of("target", target.getName()));
            return false;
         } else if (this.pendingInvitePartyId.containsKey(target.getUniqueId())) {
            this.messages.send(leader, "party.target-has-invite", Map.of("target", target.getName()));
            return false;
         } else {
            this.pendingInvitePartyId.put(target.getUniqueId(), party.partyId());
            this.messages.send(leader, "party.invite-sent", Map.of("target", target.getName()));
            this.messages
               .send(
                  target,
                  "party.invite-received",
                  Map.of("leader", leader.getName(), "size", String.valueOf(party.size()), "max", String.valueOf(this.maxPartySize()))
               );
            return true;
         }
      }
   }

   public boolean acceptInvite(Player target) {
      UUID partyId = this.pendingInvitePartyId.remove(target.getUniqueId());
      if (partyId == null) {
         this.messages.send(target, "party.no-invite");
         return false;
      } else {
         Party party = this.partiesById.get(partyId);
         if (party == null) {
            this.messages.send(target, "party.disbanded");
            return false;
         } else if (party.size() >= this.maxPartySize()) {
            this.messages.send(target, "party.full-join");
            return false;
         } else if (this.hasParty(target.getUniqueId())) {
            this.messages.send(target, "party.already-in-party");
            return false;
         } else {
            party.addMember(target.getUniqueId());
            this.partyIdByPlayer.put(target.getUniqueId(), party.partyId());
            this.messages.send(target, "party.joined");
            this.broadcastToParty(party, "party.member-joined", Map.of("player", target.getName()));
            return true;
         }
      }
   }

   public void denyInvite(Player target) {
      UUID partyId = this.pendingInvitePartyId.remove(target.getUniqueId());
      if (partyId == null) {
         this.messages.send(target, "party.no-invite");
      } else {
         this.messages.send(target, "party.invite-denied");
         Party party = this.partiesById.get(partyId);
         if (party != null) {
            Player leader = Bukkit.getPlayer(party.leaderId());
            if (leader != null) {
               this.messages.send(leader, "party.invite-denied-by", Map.of("player", target.getName()));
            }
         }
      }
   }

   public boolean isPartyReadyForDuel(Party party) {
      return party != null && party.size() >= this.maxPartySize();
   }

   public void broadcastToParty(Party party, String key, Map<String, String> placeholders) {
      for (UUID memberId : party.members()) {
         Player member = Bukkit.getPlayer(memberId);
         if (member != null) {
            this.messages.send(member, key, placeholders);
         }
      }
   }

   public void clearPlayer(UUID playerId) {
      this.pendingInvitePartyId.remove(playerId);
      this.getParty(playerId).ifPresent(party -> {
         party.removeMember(playerId);
         this.partyIdByPlayer.remove(playerId);
         if (party.size() == 0) {
            this.partiesById.remove(party.partyId());
         } else if (party.isLeader(playerId)) {
            UUID newLeader = party.members().iterator().next();
            party.setLeaderId(newLeader);
         }
      });
   }
}
