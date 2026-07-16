package org.Mona.monaDuels.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.Mona.monaDuels.config.MessageService;
import org.Mona.monaDuels.duel.DuelManager;
import org.Mona.monaDuels.party.Party;
import org.Mona.monaDuels.party.PartyManager;
import org.Mona.monaDuels.party.PartyRequestManager;
import org.Mona.monaDuels.team.TeamDuelManager;
import org.Mona.monaDuels.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

public final class PartyMenuService {
   private final MessageService messages;
   private final PartyManager partyManager;
   private final PartyRequestManager partyRequestManager;
   private final KitSelectionService kitSelectionService;
   private final TeamDuelManager teamDuelManager;
   private final DuelManager duelManager;

   public PartyMenuService(
      MessageService messages,
      PartyManager partyManager,
      PartyRequestManager partyRequestManager,
      KitSelectionService kitSelectionService,
      TeamDuelManager teamDuelManager,
      DuelManager duelManager
   ) {
      this.messages = messages;
      this.partyManager = partyManager;
      this.partyRequestManager = partyRequestManager;
      this.kitSelectionService = kitSelectionService;
      this.teamDuelManager = teamDuelManager;
      this.duelManager = duelManager;
   }

   public void openMain(Player player) {
      Inventory inv = Bukkit.createInventory(new PartyMenuService.PartyMenuHolder(PartyMenuService.MenuType.MAIN), 27, ColorUtil.color("&8Пати / Комнаты"));
      Optional<Party> partyOpt = this.partyManager.getParty(player.getUniqueId());
      if (partyOpt.isEmpty()) {
         inv.setItem(11, button(Material.LIME_WOOL, "&aСоздать пати", List.of("&7Нажмите, чтобы создать")));
      } else {
         Party party = partyOpt.get();
         boolean leader = party.isLeader(player.getUniqueId());
         inv.setItem(
            4,
            button(
               Material.NETHER_STAR,
               "&eВаша пати",
               List.of(
                  "&7Участников: &f" + party.size() + "/" + this.partyManager.maxPartySize(), leader ? "&aВы лидер" : "&7Лидер: &f" + nameOf(party.leaderId())
               )
            )
         );
         if (leader) {
            inv.setItem(11, button(Material.PLAYER_HEAD, "&bПригласить", List.of("&7Добавить игрока в пати")));
            if (this.partyManager.isPartyReadyForDuel(party)) {
               inv.setItem(13, button(Material.DIAMOND_SWORD, "&cВызвать пати", List.of("&7Дуэль 2 на 2", "&7Против другой полной пати")));
            } else {
               inv.setItem(13, button(Material.GRAY_DYE, "&8Вызвать пати", List.of("&7Нужно &f" + this.partyManager.maxPartySize() + " &7игроков")));
            }

            inv.setItem(15, button(Material.BARRIER, "&cРаспустить", List.of("&7Удалить пати")));
         } else {
            inv.setItem(11, button(Material.OAK_DOOR, "&eПокинуть пати", List.of("&7Выйти из группы")));
         }

         if (leader && this.partyRequestManager.hasIncoming(player.getUniqueId())) {
            inv.setItem(22, button(Material.EMERALD, "&aВходящий вызов", List.of("&7Принять или отклонить")));
         }
      }

      inv.setItem(26, button(Material.ARROW, "&7Назад", List.of()));
      player.openInventory(inv);
   }

   public void openInviteList(Player player) {
      List<Player> targets = new ArrayList<>();

      for (Player online : Bukkit.getOnlinePlayers()) {
         if (!online.getUniqueId().equals(player.getUniqueId()) && !this.partyManager.hasParty(online.getUniqueId())) {
            targets.add(online);
         }
      }

      int size = Math.min(54, Math.max(9, (targets.size() + 8) / 9 * 9));
      Inventory inv = Bukkit.createInventory(
         new PartyMenuService.PartyMenuHolder(PartyMenuService.MenuType.INVITE), size, ColorUtil.color("&8Пригласить в пати")
      );
      int slot = 0;

      for (Player target : targets) {
         if (slot >= size) {
            break;
         }

         inv.setItem(slot++, playerHead(target, "&f" + target.getName(), List.of("&eНажмите — пригласить")));
      }

      if (targets.isEmpty()) {
         inv.setItem(4, button(Material.BARRIER, "&cНет игроков", List.of("&7Некого пригласить")));
      }

      player.openInventory(inv);
   }

   public void openChallengeList(Player player) {
      Optional<Party> myParty = this.partyManager.getParty(player.getUniqueId());
      if (myParty.isEmpty() || !myParty.get().isLeader(player.getUniqueId())) {
         this.messages.send(player, "party.not-leader");
      } else if (!this.partyManager.isPartyReadyForDuel(myParty.get())) {
         this.messages.send(player, "party.not-ready");
      } else {
         List<Player> leaders = new ArrayList<>();

         for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.getUniqueId().equals(player.getUniqueId())) {
               this.partyManager
                  .getParty(online.getUniqueId())
                  .ifPresent(
                     party -> {
                        if (party.isLeader(online.getUniqueId())
                           && this.partyManager.isPartyReadyForDuel(party)
                           && !party.partyId().equals(myParty.get().partyId())) {
                           leaders.add(online);
                        }
                     }
                  );
            }
         }

         int size = Math.min(54, Math.max(9, (leaders.size() + 8) / 9 * 9));
         Inventory inv = Bukkit.createInventory(
            new PartyMenuService.PartyMenuHolder(PartyMenuService.MenuType.CHALLENGE), size, ColorUtil.color("&8Вызов пати")
         );
         int slot = 0;

         for (Player leader : leaders) {
            if (slot >= size) {
               break;
            }

            inv.setItem(slot++, playerHead(leader, "&c" + leader.getName(), List.of("&7Лидер пати", "&eНажмите — выбрать кит")));
         }

         if (leaders.isEmpty()) {
            inv.setItem(4, button(Material.BARRIER, "&cНет пати", List.of("&7Нет других полных пати онлайн")));
         }

         player.openInventory(inv);
      }
   }

   public void openIncomingRequest(Player leader) {
      Inventory inv = Bukkit.createInventory(new PartyMenuService.PartyMenuHolder(PartyMenuService.MenuType.INCOMING), 27, ColorUtil.color("&8Вызов пати"));
      inv.setItem(11, button(Material.LIME_WOOL, "&aПринять", List.of("&7Начать пати-дуэль")));
      inv.setItem(15, button(Material.RED_WOOL, "&cОтклонить", List.of()));
      leader.openInventory(inv);
   }

   public boolean handleClick(Player player, PartyMenuService.MenuType type, int slot, ItemStack item) {
      if (item != null && item.getType() != Material.AIR) {
         return switch (type) {
            case MAIN -> this.handleMainClick(player, slot, item);
            case INVITE -> this.handleInviteClick(player, item);
            case CHALLENGE -> this.handleChallengeClick(player, item);
            case INCOMING -> false;
         };
      } else {
         return false;
      }
   }

   private boolean handleMainClick(Player player, int slot, ItemStack item) {
      Optional<Party> partyOpt = this.partyManager.getParty(player.getUniqueId());
      if (partyOpt.isEmpty()) {
         if (slot == 11) {
            this.partyManager.createParty(player);
            player.closeInventory();
            this.openMain(player);
            return true;
         } else {
            return false;
         }
      } else {
         Party party = partyOpt.get();
         boolean leader = party.isLeader(player.getUniqueId());
         if (slot == 11 && leader) {
            this.openInviteList(player);
            return true;
         } else if (slot == 11 && !leader) {
            this.partyManager.leave(player);
            player.closeInventory();
            return true;
         } else if (slot == 13 && leader && this.partyManager.isPartyReadyForDuel(party)) {
            this.openChallengeList(player);
            return true;
         } else if (slot == 15 && leader) {
            this.partyManager.disband(party);
            player.closeInventory();
            return true;
         } else if (slot == 22 && leader && this.partyRequestManager.hasIncoming(player.getUniqueId())) {
            this.openIncomingRequest(player);
            return true;
         } else if (slot == 26) {
            player.closeInventory();
            return true;
         } else {
            return false;
         }
      }
   }

   private boolean handleInviteClick(Player player, ItemStack item) {
      if (item.getType() != Material.PLAYER_HEAD) {
         return false;
      } else {
         if (item.getItemMeta() instanceof SkullMeta skull && skull.getOwningPlayer() != null) {
            String name = skull.getOwningPlayer().getName();
            if (name == null) {
               return false;
            }

            Player target = Bukkit.getPlayerExact(name);
            if (target == null) {
               this.messages.send(player, "general.player-offline", Map.of("player", name));
               return true;
            }

            this.partyManager.invite(player, target);
            player.closeInventory();
            return true;
         }

         return false;
      }
   }

   private boolean handleChallengeClick(Player player, ItemStack item) {
      if (item.getType() != Material.PLAYER_HEAD) {
         return false;
      } else {
         if (item.getItemMeta() instanceof SkullMeta skull && skull.getOwningPlayer() != null) {
            String name = skull.getOwningPlayer().getName();
            if (name == null) {
               return false;
            }

            Player targetLeader = Bukkit.getPlayerExact(name);
            if (targetLeader == null) {
               this.messages.send(player, "general.player-offline", Map.of("player", name));
               return true;
            }

            if (!this.duelManager.isInDuel(player.getUniqueId()) && !this.teamDuelManager.isInTeamDuel(player.getUniqueId())) {
               player.closeInventory();
               this.kitSelectionService.openPartyKitMenu(player, targetLeader.getUniqueId());
               return true;
            }

            this.messages.send(player, "duel.already-in-duel");
            return true;
         }

         return false;
      }
   }

   public void handleIncomingAction(Player leader, int slot) {
      if (slot == 11) {
         leader.closeInventory();
         this.teamDuelManager.startFromRequest(leader);
      } else if (slot == 15) {
         leader.closeInventory();
         this.teamDuelManager.denyRequest(leader);
      }
   }

   private static String nameOf(UUID id) {
      Player player = Bukkit.getPlayer(id);
      return player != null ? player.getName() : "?";
   }

   private static ItemStack button(Material material, String name, List<String> lore) {
      ItemStack stack = new ItemStack(material);
      ItemMeta meta = stack.getItemMeta();
      if (meta != null) {
         meta.setDisplayName(ColorUtil.color(name));
         meta.setLore(lore.stream().map(ColorUtil::color).toList());
         stack.setItemMeta(meta);
      }

      return stack;
   }

   private static ItemStack playerHead(Player owner, String name, List<String> lore) {
      ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
      SkullMeta meta = (SkullMeta)stack.getItemMeta();
      if (meta != null) {
         meta.setOwningPlayer(owner);
         meta.setDisplayName(ColorUtil.color(name));
         meta.setLore(lore.stream().map(ColorUtil::color).toList());
         stack.setItemMeta(meta);
      }

      return stack;
   }

   public static enum MenuType {
      MAIN,
      INVITE,
      CHALLENGE,
      INCOMING;
   }

   public static record PartyMenuHolder(PartyMenuService.MenuType type) implements InventoryHolder {
      public Inventory getInventory() {
         return null;
      }
   }
}
