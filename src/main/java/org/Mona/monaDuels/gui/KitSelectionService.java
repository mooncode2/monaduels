package org.Mona.monaDuels.gui;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.Mona.monaDuels.MonaDuels;
import org.Mona.monaDuels.config.ConfigManager;
import org.Mona.monaDuels.config.MessageService;
import org.Mona.monaDuels.kit.Kit;
import org.Mona.monaDuels.kit.KitManager;
import org.Mona.monaDuels.menu.MenuManager;
import org.Mona.monaDuels.party.Party;
import org.Mona.monaDuels.party.PartyManager;
import org.Mona.monaDuels.party.PartyRequestManager;
import org.Mona.monaDuels.player.PlayerDataManager;
import org.Mona.monaDuels.queue.QueueManager;
import org.Mona.monaDuels.request.RequestManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class KitSelectionService {
   private final MonaDuels plugin;
   private final ConfigManager config;
   private final MessageService messages;
   private final MenuManager menuManager;
   private final KitManager kitManager;
   private final RequestManager requestManager;
   private final PlayerDataManager playerDataManager;
   private final PartyManager partyManager;
   private final PartyRequestManager partyRequestManager;
   private KitPreviewService kitPreviewService;
   private QueueManager queueManager;
   private final Map<UUID, KitSelectionService.PendingChallenge> pendingTargets = new ConcurrentHashMap<>();

   public KitSelectionService(
      MonaDuels plugin,
      ConfigManager config,
      MessageService messages,
      MenuManager menuManager,
      KitManager kitManager,
      RequestManager requestManager,
      PlayerDataManager playerDataManager,
      PartyManager partyManager,
      PartyRequestManager partyRequestManager
   ) {
      this.plugin = plugin;
      this.config = config;
      this.messages = messages;
      this.menuManager = menuManager;
      this.kitManager = kitManager;
      this.requestManager = requestManager;
      this.playerDataManager = playerDataManager;
      this.partyManager = partyManager;
      this.partyRequestManager = partyRequestManager;
   }

   public void bindPreview(KitPreviewService kitPreviewService) {
      this.kitPreviewService = kitPreviewService;
   }

   public void bindQueue(QueueManager queueManager) {
      this.queueManager = queueManager;
   }

   /**
    * Opens the set-selection menu in matchmaking mode (Task 2): left-click a kit joins the queue,
    * right-click opens the kit preview. Replaces the old manual player-selection flow.
    */
   public void openMatchmakingMenu(Player player) {
      String menuId = this.config.defaultKitMenu();
      if (!this.menuManager.hasMenu(menuId)) {
         this.messages.send(player, "kit.menu-not-found");
         return;
      }

      this.menuManager.syncKitDisplayItems(menuId, this.kitManager.all());
      this.menuManager.clearHandlers(menuId);
      this.bindMatchmakingHandlers(menuId);
      this.menuManager.setKitActionHandler((p, kitName) -> this.onMatchmakingKit(p, kitName));
      this.menuManager.openMenu(player, menuId, Map.of("target", "—"));
   }

   private void bindMatchmakingHandlers(String menuId) {
      for (Kit kit : this.kitManager.all()) {
         String itemKey = kit.name();
         this.menuManager.registerKeyHandler(menuId, itemKey, (player, key) -> this.onMatchmakingKit(player, kit.name()));
         if (this.kitPreviewService != null) {
            this.menuManager.registerRightKeyHandler(menuId, itemKey, (player, key) -> this.kitPreviewService.open(player, kit.name()));
         }
      }

      this.menuManager.registerKeyHandler(menuId, "close", (player, key) -> player.closeInventory());
   }

   private void onMatchmakingKit(Player player, String kitName) {
      player.closeInventory();
      if (this.kitManager.find(kitName).isEmpty()) {
         this.messages.send(player, "kit.not-found", Map.of("kit", kitName));
      } else if (this.queueManager == null) {
         this.messages.send(player, "kit.menu-not-found");
      } else {
         String kitKey = kitName.toLowerCase(Locale.ROOT);
         this.playerDataManager.setLastKit(player.getUniqueId(), kitKey);
         this.playerDataManager.setLastGameMode(player.getUniqueId(), "normal");
         this.queueManager.enqueue(player, kitKey, false);
      }
   }

   public void openKitMenu(Player challenger, String targetName) {
      this.openKitMenu(challenger, targetName, false);
   }

   public void openPartyKitMenu(Player challengerLeader, UUID targetLeaderId) {
      Player targetLeader = Bukkit.getPlayer(targetLeaderId);
      String name = targetLeader != null ? targetLeader.getName() : "?";
      this.pendingTargets.put(challengerLeader.getUniqueId(), new KitSelectionService.PendingChallenge(name, false, targetLeaderId));
      this.openKitMenuInternal(challengerLeader, name);
   }

   public void openKitMenu(Player challenger, String targetName, boolean ranked) {
      this.pendingTargets.put(challenger.getUniqueId(), new KitSelectionService.PendingChallenge(targetName, ranked, null));
      this.playerDataManager.setLastGameMode(challenger.getUniqueId(), ranked ? "ranked" : "normal");
      this.openKitMenuInternal(challenger, targetName);
   }

   private void openKitMenuInternal(Player challenger, String targetName) {
      String menuId = this.config.defaultKitMenu();
      if (!this.menuManager.hasMenu(menuId)) {
         this.messages.send(challenger, "kit.menu-not-found");
         this.pendingTargets.remove(challenger.getUniqueId());
      } else {
         this.menuManager.syncKitDisplayItems(menuId, this.kitManager.all());
         this.menuManager.clearHandlers(menuId);
         this.bindKitHandlers(menuId, challenger);
         this.menuManager.setKitActionHandler((player, kitName) -> this.onKitSelected(player, kitName));
         this.menuManager.openMenu(challenger, menuId, Map.of("target", targetName));
      }
   }

   private void bindKitHandlers(String menuId, Player challenger) {
      for (Kit kit : this.kitManager.all()) {
         String itemKey = kit.name();
         this.menuManager.registerKeyHandler(menuId, itemKey, (player, key) -> this.onKitSelected(player, kit.name()));
         if (this.kitPreviewService != null) {
            this.menuManager.registerRightKeyHandler(menuId, itemKey, (player, key) -> this.kitPreviewService.open(player, kit.name()));
         }
      }

      this.menuManager.registerKeyHandler(menuId, "close", (player, key) -> {
         this.pendingTargets.remove(player.getUniqueId());
         player.closeInventory();
      });
   }

   private void onKitSelected(Player challenger, String kitName) {
      KitSelectionService.PendingChallenge pending = this.pendingTargets.remove(challenger.getUniqueId());
      challenger.closeInventory();
      if (pending != null) {
         if (this.kitManager.find(kitName).isEmpty()) {
            this.messages.send(challenger, "kit.not-found", Map.of("kit", kitName));
         } else {
            String kitKey = kitName.toLowerCase(Locale.ROOT);
            if (pending.partyTargetLeaderId() != null) {
               this.onPartyKitSelected(challenger, pending, kitKey);
            } else {
               Player target = this.plugin.getServer().getPlayerExact(pending.targetName());
               if (target != null && target.isOnline()) {
                  if (!this.requestManager.hasOutgoing(challenger.getUniqueId()) && !this.requestManager.hasIncoming(challenger.getUniqueId())) {
                     this.playerDataManager.setLastKit(challenger.getUniqueId(), kitKey);
                     this.playerDataManager.setLastGameMode(challenger.getUniqueId(), pending.ranked() ? "ranked" : "normal");
                     this.requestManager.create(challenger, target, kitKey, pending.ranked());
                  } else {
                     this.messages.send(challenger, "request.already-pending");
                  }
               } else {
                  this.messages.send(challenger, "general.player-offline", Map.of("player", pending.targetName()));
               }
            }
         }
      }
   }

   private void onPartyKitSelected(Player challengerLeader, KitSelectionService.PendingChallenge pending, String kitKey) {
      Player targetLeader = Bukkit.getPlayer(pending.partyTargetLeaderId());
      if (targetLeader != null && targetLeader.isOnline()) {
         Optional<Party> myParty = this.partyManager.getParty(challengerLeader.getUniqueId());
         Optional<Party> theirParty = this.partyManager.getParty(targetLeader.getUniqueId());
         if (myParty.isEmpty()
            || theirParty.isEmpty()
            || !myParty.get().isLeader(challengerLeader.getUniqueId())
            || !theirParty.get().isLeader(targetLeader.getUniqueId())
            || !this.partyManager.isPartyReadyForDuel(myParty.get())
            || !this.partyManager.isPartyReadyForDuel(theirParty.get())) {
            this.messages.send(challengerLeader, "party.not-ready");
         } else if (this.partyRequestManager.hasIncoming(targetLeader.getUniqueId())) {
            this.messages.send(challengerLeader, "party.request.target-busy", Map.of("target", targetLeader.getName()));
         } else {
            this.partyRequestManager.create(challengerLeader, targetLeader, kitKey, pending.ranked());
         }
      } else {
         this.messages.send(challengerLeader, "general.player-offline", Map.of("player", pending.targetName()));
      }
   }

   public void cancelPending(Player player) {
      this.pendingTargets.remove(player.getUniqueId());
   }

   private static record PendingChallenge(String targetName, boolean ranked, UUID partyTargetLeaderId) {
   }
}
