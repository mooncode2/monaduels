package org.Mona.monaDuels.party;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.Mona.monaDuels.config.ConfigManager;
import org.Mona.monaDuels.config.MessageService;
import org.bukkit.entity.Player;

public final class PartyRequestManager {
   private final ConfigManager config;
   private final MessageService messages;
   private PartyDuelRequest incomingByTargetLeader;

   public PartyRequestManager(ConfigManager config, MessageService messages) {
      this.config = config;
      this.messages = messages;
   }

   public void create(Player challengerLeader, Player targetLeader, String kitName, boolean ranked) {
      this.clear();
      long now = System.currentTimeMillis();
      long expires = now + (long)this.config.requestTimeoutSeconds() * 1000L;
      this.incomingByTargetLeader = new PartyDuelRequest(
         challengerLeader.getUniqueId(), targetLeader.getUniqueId(), kitName.toLowerCase(Locale.ROOT), ranked, now, expires
      );
      this.messages.send(challengerLeader, "party.request.sent", Map.of("target", targetLeader.getName(), "kit", kitName));
      this.messages.send(targetLeader, "party.request.received", Map.of("challenger", challengerLeader.getName(), "kit", kitName));
      this.messages.send(targetLeader, "party.request.hint");
   }

   public Optional<PartyDuelRequest> getIncoming(UUID targetLeaderId) {
      if (this.incomingByTargetLeader == null) {
         return Optional.empty();
      } else if (!this.incomingByTargetLeader.targetLeaderId().equals(targetLeaderId)) {
         return Optional.empty();
      } else if (this.incomingByTargetLeader.isExpired()) {
         this.clear();
         return Optional.empty();
      } else {
         return Optional.of(this.incomingByTargetLeader);
      }
   }

   public Optional<PartyDuelRequest> removeIncoming(UUID targetLeaderId) {
      Optional<PartyDuelRequest> req = this.getIncoming(targetLeaderId);
      if (req.isPresent()) {
         this.clear();
      }

      return req;
   }

   public boolean hasIncoming(UUID targetLeaderId) {
      return this.getIncoming(targetLeaderId).isPresent();
   }

   public boolean isRanked(UUID targetLeaderId) {
      return this.getIncoming(targetLeaderId).map(PartyDuelRequest::ranked).orElse(false);
   }

   public void clear() {
      this.incomingByTargetLeader = null;
   }

   public void clearForLeader(UUID leaderId) {
      if (this.incomingByTargetLeader != null
         && (this.incomingByTargetLeader.challengerLeaderId().equals(leaderId) || this.incomingByTargetLeader.targetLeaderId().equals(leaderId))) {
         this.clear();
      }
   }
}
