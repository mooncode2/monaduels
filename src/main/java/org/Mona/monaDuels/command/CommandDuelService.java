package org.Mona.monaDuels.command;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.Mona.monaDuels.arena.Arena;
import org.Mona.monaDuels.arena.ArenaManager;
import org.Mona.monaDuels.arena.MapPoolManager;
import org.Mona.monaDuels.config.ConfigManager;
import org.Mona.monaDuels.config.MessageService;
import org.Mona.monaDuels.cooldown.CooldownManager;
import org.Mona.monaDuels.duel.DuelManager;
import org.Mona.monaDuels.kit.Kit;
import org.Mona.monaDuels.kit.KitManager;
import org.Mona.monaDuels.player.PlayerDataManager;
import org.Mona.monaDuels.request.RequestManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class CommandDuelService {
   private final ConfigManager config;
   private final MessageService messages;
   private final CooldownManager cooldowns;
   private final DuelManager duelManager;
   private final KitManager kitManager;
   private final ArenaManager arenaManager;
   private final MapPoolManager mapPoolManager;
   private final PlayerDataManager playerDataManager;
   private final RequestManager requestManager;

   public CommandDuelService(
      ConfigManager config,
      MessageService messages,
      CooldownManager cooldowns,
      DuelManager duelManager,
      KitManager kitManager,
      ArenaManager arenaManager,
      MapPoolManager mapPoolManager,
      PlayerDataManager playerDataManager,
      RequestManager requestManager
   ) {
      this.config = config;
      this.messages = messages;
      this.cooldowns = cooldowns;
      this.duelManager = duelManager;
      this.kitManager = kitManager;
      this.arenaManager = arenaManager;
      this.mapPoolManager = mapPoolManager;
      this.playerDataManager = playerDataManager;
      this.requestManager = requestManager;
   }

   public boolean startCommandDuel(Player challenger, String targetName, String kitName, String arenaName) {
      if (targetName.equalsIgnoreCase(challenger.getName())) {
         this.messages.send(challenger, "general.cannot-duel-self");
         return true;
      } else if (!challenger.hasPermission("monaduels.use")) {
         this.messages.send(challenger, "general.no-permission");
         return true;
      } else if (!challenger.hasPermission("monaduels.bypass.cooldown") && this.cooldowns.isOnCooldown(challenger, "challenge")) {
         this.messages.send(challenger, "cooldown.active", Map.of("seconds", String.valueOf(this.cooldowns.remainingSeconds(challenger, "challenge"))));
         return true;
      } else {
         Player target = Bukkit.getPlayerExact(targetName);
         if (target == null) {
            this.messages.send(challenger, "general.player-offline", Map.of("player", targetName));
            return true;
         } else if (!target.isOnline()) {
            this.messages.send(challenger, "general.player-offline", Map.of("player", targetName));
            return true;
         } else if (this.duelManager.isInDuel(challenger.getUniqueId())
            || this.requestManager.hasOutgoing(challenger.getUniqueId())
            || this.requestManager.hasIncoming(challenger.getUniqueId())) {
            this.messages.send(challenger, "request.challenger-busy");
            return true;
         } else if (!this.duelManager.isInDuel(target.getUniqueId())
            && !this.requestManager.hasIncoming(target.getUniqueId())
            && !this.requestManager.hasOutgoing(target.getUniqueId())) {
            String lastKit = this.playerDataManager.getLastKit(challenger.getUniqueId());
            String finalKitName = kitName != null ? kitName : (lastKit.isBlank() ? null : lastKit);
            if (finalKitName == null) {
               this.messages.send(challenger, "kit.not-found", Map.of("kit", "?"));
               return true;
            } else if (this.kitManager.find(finalKitName).isEmpty()) {
               this.messages.send(challenger, "kit.not-found", Map.of("kit", finalKitName));
               return true;
            } else {
               Kit kit = this.kitManager.find(finalKitName).get();
               if (!challenger.hasPermission("monaduels.bypass.cooldown")) {
                  this.cooldowns.set(challenger, "challenge", (long)this.config.cooldown("challenge", 10) * 1000L);
               }

               this.playerDataManager.setLastKit(challenger.getUniqueId(), finalKitName);
               this.startDuelNow(challenger, target, kit, arenaName);
               return true;
            }
         } else {
            this.messages.send(challenger, "request.target-busy", Map.of("target", target.getName()));
            return true;
         }
      }
   }

   private void startDuelNow(Player challenger, Player target, Kit kit, String arenaName) {
      Arena arena;
      if (arenaName != null && !arenaName.isBlank()) {
         arenaName = arenaName.toLowerCase(Locale.ROOT);
         Optional<Arena> arenaOpt = this.arenaManager.find(arenaName);
         if (arenaOpt.isEmpty() || !arenaOpt.get().enabled() || !arenaOpt.get().isFree()) {
            this.messages.send(challenger, "arena.not-found", Map.of("arena", arenaName));
            return;
         }

         arena = arenaOpt.get();
      } else {
         Optional<Arena> arenaOpt = this.mapPoolManager.findFreeArenaForKit(kit.name(), this.arenaManager);
         if (arenaOpt.isEmpty()) {
            this.messages.send(challenger, "arena.none-free-pool", Map.of("pool", kit.name(), "kit", kit.displayName()));
            this.messages.send(target, "arena.none-free-pool", Map.of("pool", kit.name(), "kit", kit.displayName()));
            return;
         }

         arena = arenaOpt.get();
      }

      this.messages.send(challenger, "request.accepted-challenger", Map.of("target", target.getName()));
      this.messages.send(target, "request.accepted-target", Map.of("challenger", challenger.getName()));
      this.playerDataManager.setLastKit(target.getUniqueId(), kit.name());
      this.playerDataManager.setLastGameMode(target.getUniqueId(), "normal");
      this.duelManager.startDirectDuel(challenger, target, kit, arena, false);
   }
}
