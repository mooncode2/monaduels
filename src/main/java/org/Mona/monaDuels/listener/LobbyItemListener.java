package org.Mona.monaDuels.listener;

import org.Mona.monaDuels.MonaDuels;
import org.Mona.monaDuels.config.MessageService;
import org.Mona.monaDuels.duel.DuelManager;
import org.Mona.monaDuels.gui.KitLayoutEditorService;
import org.Mona.monaDuels.gui.KitSelectionService;
import org.Mona.monaDuels.gui.PartyMenuService;
import org.Mona.monaDuels.gui.SettingsMenuService;
import org.Mona.monaDuels.lobby.LobbyLayoutService;
import org.Mona.monaDuels.player.PlayerDataManager;
import org.Mona.monaDuels.queue.QueueManager;
import org.Mona.monaDuels.util.ColorUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class LobbyItemListener implements Listener {
   private final MonaDuels plugin;
   private final LobbyLayoutService lobbyLayout;
   private final KitSelectionService kitSelection;
   private final PartyMenuService partyMenu;
   private final SettingsMenuService settingsMenu;
   private final DuelManager duelManager;
   private final MessageService messages;
   private final KitLayoutEditorService editorService;
   private final PlayerDataManager playerDataManager;
   private final QueueManager queueManager;
   
   private boolean isPluginLobbyItem(ItemStack item) {
      if (item == null) return false;
      
      // Проверка на красный краситель "Покинуть подбор"
      if (item.getType() == Material.RED_DYE) {
         ItemMeta meta = item.getItemMeta();
         return meta != null && meta.getDisplayName() != null && 
                meta.getDisplayName().contains(ColorUtil.strip(ColorUtil.color("&cПокинуть подбор")));
      }
      
      // Проверка на предметы лобби по ключу
      ItemMeta meta = item.getItemMeta();
      return meta != null && meta.getPersistentDataContainer().has(this.lobbyLayout.getDuelItemKey(), PersistentDataType.BYTE);
   }
   
   private ItemStack protectItem(ItemStack item) {
      ItemMeta meta = item.getItemMeta();
      if (meta != null) {
         meta.setUnbreakable(true);
         meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
         meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
      }
      item.setItemMeta(meta);
      return item;
   }

   public LobbyItemListener(
      MonaDuels plugin,
      LobbyLayoutService lobbyLayout,
      KitSelectionService kitSelection,
      PartyMenuService partyMenu,
      SettingsMenuService settingsMenu,
      DuelManager duelManager,
      MessageService messages,
      KitLayoutEditorService editorService,
      PlayerDataManager playerDataManager,
      QueueManager queueManager
   ) {
      this.plugin = plugin;
      this.lobbyLayout = lobbyLayout;
      this.kitSelection = kitSelection;
      this.partyMenu = partyMenu;
      this.settingsMenu = settingsMenu;
      this.duelManager = duelManager;
      this.messages = messages;
      this.editorService = editorService;
      this.playerDataManager = playerDataManager;
      this.queueManager = queueManager;
   }

   @EventHandler
   public void onJoin(PlayerJoinEvent event) {
      this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> this.lobbyLayout.applyLayout(event.getPlayer()), 5L);
   }

   @EventHandler
   public void onRespawn(PlayerRespawnEvent event) {
      this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> this.lobbyLayout.applyLayout(event.getPlayer()), 2L);
   }

   @EventHandler
   public void onWorldChange(PlayerChangedWorldEvent event) {
      String from = event.getFrom().getName();
      String to = event.getPlayer().getWorld().getName();
      this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
         Player player = event.getPlayer();
         if (this.lobbyLayout.isLobbyWorld(from) && !this.lobbyLayout.isLobbyWorld(to)) {
            this.lobbyLayout.clearLayout(player);
         }

         if (this.lobbyLayout.isLobbyWorld(to)) {
            this.lobbyLayout.applyLayout(player);
         }
      }, 2L);
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      this.lobbyLayout.forgetPlayer(event.getPlayer().getUniqueId());
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onInventoryClick(InventoryClickEvent event) {
      if (event.getWhoClicked() instanceof Player player && this.lobbyLayout.isLobbyWorld(player.getWorld().getName())) {
         ItemStack currentItem = event.getCurrentItem();
         ItemStack cursorItem = event.getCursor();
         
         if (currentItem != null && this.isPluginLobbyItem(currentItem)) {
            event.setCancelled(true);
            return;
         }
         
         if (cursorItem != null && this.isPluginLobbyItem(cursorItem)) {
            event.setCancelled(true);
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onInventoryDrag(InventoryDragEvent event) {
      if (event.getWhoClicked() instanceof Player player && this.lobbyLayout.isLobbyWorld(player.getWorld().getName())) {
         for (ItemStack item : event.getNewItems().values()) {
            if (item != null && this.isPluginLobbyItem(item)) {
               event.setCancelled(true);
               return;
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH
   )
   public void onInteract(PlayerInteractEvent event) {
      if (event.getHand() == EquipmentSlot.HAND) {
         Action action = event.getAction();
         if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            Player player = event.getPlayer();
            if (!this.editorService.isEditing(player.getUniqueId())
               && !this.duelManager.isInDuel(player.getUniqueId())
               && this.lobbyLayout.isLobbyWorld(player.getWorld().getName())) {
               ItemStack item = event.getItem();
               if (item != null) {
                  // Проверка на красный краситель "Покинуть подбор" в любом слоте
                  if (item.getType() == Material.RED_DYE) {
                     ItemMeta meta = item.getItemMeta();
                     if (meta != null && meta.getDisplayName() != null && meta.getDisplayName().contains(ColorUtil.strip(ColorUtil.color("&cПокинуть подбор")))) {
                        event.setCancelled(true);
                        this.queueManager.leaveQueue(player);
                        return;
                     }
                  }

                  // The rod toggles the duel hotbar on.
                  if (this.lobbyLayout.isLobbyDuelItem(item)) {
                     event.setCancelled(true);
                     this.lobbyLayout.expand(player);
                     return;
                  }

                  // Layout items react only while the duel hotbar is shown.
                  if (this.lobbyLayout.isExpanded(player.getUniqueId())) {
                     int slot = player.getInventory().getHeldItemSlot();
                     String itemId = this.lobbyLayout.itemIdAtSlot(slot);
                     if (itemId != null) {
                        LobbyLayoutService.LobbyItemDefinition def = this.lobbyLayout.item(itemId);
                        if (def != null) {
                           event.setCancelled(true);
                           ItemStack lobbyItem = player.getInventory().getItem(slot);
                           
                           // Защита предмета от перемещения/броска (по предмету, а не по слоту)
                           if (lobbyItem != null && this.isPluginLobbyItem(lobbyItem)) {
                              this.protectItem(lobbyItem);
                           }
                           
                           // Проверка на красный краситель "Покинуть подбор"
                           if (lobbyItem != null && lobbyItem.getType() == Material.RED_DYE) {
                              ItemMeta meta = lobbyItem.getItemMeta();
                              if (meta != null && meta.getDisplayName() != null && meta.getDisplayName().contains(ColorUtil.strip(ColorUtil.color("&cПокинуть подбор")))) {
                                 this.queueManager.leaveQueue(player);
                                 return;
                              }
                           }
                           
                           this.runAction(player, def.action());
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void runAction(Player player, String action) {
      if (action != null && !action.isBlank()) {
         switch (action.toLowerCase()) {
            case "matchmaking":
            case "duel":
            case "casual":
            case "rated":
               this.kitSelection.openMatchmakingMenu(player);
               break;
            case "last-kit":
               String lastKit = this.playerDataManager.getLastKit(player.getUniqueId());
               String lastMode = this.playerDataManager.getLastGameMode(player.getUniqueId());
               boolean ranked = "ranked".equalsIgnoreCase(lastMode);
               if (!lastKit.isBlank()) {
                  this.queueManager.enqueue(player, lastKit, ranked);
               } else {
                  this.kitSelection.openMatchmakingMenu(player);
               }
               break;
            case "stats":
               player.performCommand("mduel stats");
               break;
            case "party":
               this.partyMenu.openMain(player);
               break;
            case "settings":
               this.settingsMenu.open(player);
               break;
            case "back":
            case "close":
               this.lobbyLayout.collapse(player);
               break;
            default:
               this.messages.send(player, "lobby.unknown-action");
         }
      }
   }
}
