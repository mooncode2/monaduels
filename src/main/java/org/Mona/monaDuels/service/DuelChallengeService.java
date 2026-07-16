package org.Mona.monaDuels.service;

import java.util.Map;
import org.Mona.monaDuels.config.ConfigManager;
import org.Mona.monaDuels.config.MessageService;
import org.Mona.monaDuels.cooldown.CooldownManager;
import org.Mona.monaDuels.duel.DuelManager;
import org.Mona.monaDuels.gui.KitSelectionService;
import org.Mona.monaDuels.request.RequestManager;
import org.bukkit.entity.Player;

public final class DuelChallengeService {
   private final ConfigManager config;
   private final MessageService messages;
   private final CooldownManager cooldowns;
   private final DuelManager duelManager;
   private final RequestManager requestManager;
   private final KitSelectionService kitSelectionService;

   public DuelChallengeService(
      ConfigManager config,
      MessageService messages,
      CooldownManager cooldowns,
      DuelManager duelManager,
      RequestManager requestManager,
      KitSelectionService kitSelectionService
   ) {
      this.config = config;
      this.messages = messages;
      this.cooldowns = cooldowns;
      this.duelManager = duelManager;
      this.requestManager = requestManager;
      this.kitSelectionService = kitSelectionService;
   }

   public boolean challenge(Player challenger, String targetName) {
      if (targetName.equalsIgnoreCase(challenger.getName())) {
         this.messages.send(challenger, "general.cannot-duel-self");
         return true;
      } else if (!challenger.hasPermission("monaduels.bypass.cooldown") && this.cooldowns.isOnCooldown(challenger, "challenge")) {
         this.messages.send(challenger, "cooldown.active", Map.of("seconds", String.valueOf(this.cooldowns.remainingSeconds(challenger, "challenge"))));
         return true;
      } else {
         Player target = challenger.getServer().getPlayerExact(targetName);
         if (target == null || !target.isOnline()) {
            this.messages.send(challenger, "general.player-offline", Map.of("player", targetName));
            return true;
         } else if (!this.prepareRequest(challenger, target)) {
            return true;
         } else {
            if (!challenger.hasPermission("monaduels.bypass.cooldown")) {
               this.cooldowns.set(challenger, "challenge", (long)this.config.cooldown("challenge", 10) * 1000L);
            }

            this.kitSelectionService.openKitMenu(challenger, target.getName(), false);
            return true;
         }
      }
   }

   public boolean prepareRequest(Player challenger, Player target) {
      if (this.duelManager.isInDuel(challenger.getUniqueId())
         || this.requestManager.hasOutgoing(challenger.getUniqueId())
         || this.requestManager.hasIncoming(challenger.getUniqueId())) {
         this.messages.send(challenger, "request.challenger-busy");
         return false;
      } else if (!this.duelManager.isInDuel(target.getUniqueId())
         && !this.requestManager.hasIncoming(target.getUniqueId())
         && !this.requestManager.hasOutgoing(target.getUniqueId())) {
         return true;
      } else {
         this.messages.send(challenger, "request.target-busy", Map.of("target", target.getName()));
         return false;
      }
   }
}
