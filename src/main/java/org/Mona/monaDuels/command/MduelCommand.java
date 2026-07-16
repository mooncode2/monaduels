package org.Mona.monaDuels.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.Mona.monaDuels.MonaDuels;
import org.Mona.monaDuels.arena.Arena;
import org.Mona.monaDuels.arena.ArenaManager;
import org.Mona.monaDuels.config.ConfigManager;
import org.Mona.monaDuels.config.MessageService;
import org.Mona.monaDuels.cooldown.CooldownManager;
import org.Mona.monaDuels.duel.DuelManager;
import org.Mona.monaDuels.gui.KitSelectionService;
import org.Mona.monaDuels.kit.Kit;
import org.Mona.monaDuels.kit.KitManager;
import org.Mona.monaDuels.party.PartyManager;
import org.Mona.monaDuels.party.PartyRequestManager;
import org.Mona.monaDuels.service.DuelChallengeService;
import org.Mona.monaDuels.stats.StatsManager;
import org.Mona.monaDuels.team.TeamDuelManager;
import org.Mona.monaDuels.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class MduelCommand implements CommandExecutor, TabCompleter {
   private final MonaDuels plugin;
   private final ConfigManager config;
   private final MessageService messages;
   private final ArenaManager arenaManager;
   private final KitManager kitManager;
   private final DuelManager duelManager;
   private final TeamDuelManager teamDuelManager;
   private final CooldownManager cooldowns;
   private final DuelChallengeService challengeService;
   private final KitSelectionService kitSelectionService;
   private final PartyManager partyManager;
   private final PartyRequestManager partyRequestManager;
   private final CommandDuelService commandDuelService;

   public MduelCommand(
      MonaDuels plugin,
      ConfigManager config,
      MessageService messages,
      ArenaManager arenaManager,
      KitManager kitManager,
      DuelManager duelManager,
      TeamDuelManager teamDuelManager,
      CooldownManager cooldowns,
      DuelChallengeService challengeService,
      KitSelectionService kitSelectionService,
      PartyManager partyManager,
      PartyRequestManager partyRequestManager,
      CommandDuelService commandDuelService
   ) {
      this.plugin = plugin;
      this.config = config;
      this.messages = messages;
      this.arenaManager = arenaManager;
      this.kitManager = kitManager;
      this.duelManager = duelManager;
      this.teamDuelManager = teamDuelManager;
      this.cooldowns = cooldowns;
      this.challengeService = challengeService;
      this.kitSelectionService = kitSelectionService;
      this.partyManager = partyManager;
      this.partyRequestManager = partyRequestManager;
      this.commandDuelService = commandDuelService;
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (sender instanceof Player player) {
         if (args.length == 0) {
            this.sendUsage(player);
            return true;
         } else {
            String sub = args[0].toLowerCase(Locale.ROOT);
            switch (sub) {
               case "accept":
                  this.handleAccept(player);
                  break;
               case "deny":
                  this.handleDeny(player);
                  break;
               case "leave":
                  this.handleLeave(player);
                  break;
               case "spectate":
                  this.handleSpectate(player);
                  break;
               case "results":
                  this.handleResults(player, args);
                  break;
               case "stats":
                  this.handleStats(player);
                  break;
               case "party":
                  this.handleParty(player, args);
                  break;
               case "admin":
                  this.handleAdmin(player, args);
                  break;
               default:
                  if (!player.hasPermission("monaduels.use")) {
                     this.messages.send(player, "general.no-permission");
                     return true;
                  }

                  MduelCommand.CommandArgs parsed = this.parseCommandArgs(args);
                  if (parsed != null) {
                     this.commandDuelService.startCommandDuel(player, parsed.targetName(), parsed.kitName(), parsed.arenaName());
                  } else {
                     this.challengeService.challenge(player, args[0]);
                  }
            }

            return true;
         }
      } else {
         this.messages.send(sender, "general.player-only");
         return true;
      }
   }

   private MduelCommand.CommandArgs parseCommandArgs(String[] args) {
      if (args.length < 2) {
         return null;
      } else {
         String targetArg = args[0];
         String kitName = null;
         String arenaName = null;

         for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("kit=")) {
               kitName = arg.substring(4).toLowerCase(Locale.ROOT);
            } else if (arg.startsWith("map=")) {
               arenaName = arg.substring(4).toLowerCase(Locale.ROOT);
            }
         }

         return kitName == null && arenaName == null ? null : new MduelCommand.CommandArgs(targetArg, kitName, arenaName);
      }
   }

   private void handleAccept(Player player) {
      if (!player.hasPermission("monaduels.use")) {
         this.messages.send(player, "general.no-permission");
      } else if (!player.hasPermission("monaduels.bypass.cooldown") && this.cooldowns.isOnCooldown(player, "accept")) {
         this.messages.send(player, "cooldown.active", Map.of("seconds", String.valueOf(this.cooldowns.remainingSeconds(player, "accept"))));
      } else {
         if (!player.hasPermission("monaduels.bypass.cooldown")) {
            this.cooldowns.set(player, "accept", (long)this.config.cooldown("accept", 5) * 1000L);
         }

         if (this.partyRequestManager.hasIncoming(player.getUniqueId())
            && this.partyManager.getParty(player.getUniqueId()).map(p -> p.isLeader(player.getUniqueId())).orElse(false)) {
            this.teamDuelManager.startFromRequest(player);
         } else {
            this.duelManager.startFromRequest(player);
         }
      }
   }

   private void handleDeny(Player player) {
      if (!player.hasPermission("monaduels.use")) {
         this.messages.send(player, "general.no-permission");
      } else if (this.partyRequestManager.hasIncoming(player.getUniqueId())
         && this.partyManager.getParty(player.getUniqueId()).map(p -> p.isLeader(player.getUniqueId())).orElse(false)) {
         this.teamDuelManager.denyRequest(player);
      } else {
         this.duelManager.deny(player);
      }
   }

   private void handleParty(Player player, String[] args) {
      if (!player.hasPermission("monaduels.use")) {
         this.messages.send(player, "general.no-permission");
      } else if (args.length < 2) {
         this.plugin.partyMenuService().openMain(player);
      } else {
         String action = args[1].toLowerCase(Locale.ROOT);
         switch (action) {
            case "accept":
               this.partyManager.acceptInvite(player);
               break;
            case "deny":
               this.partyManager.denyInvite(player);
               break;
            case "leave":
               this.partyManager.leave(player);
               break;
            default:
               if (args.length >= 3 && "invite".equals(action)) {
                  Player target = Bukkit.getPlayerExact(args[2]);
                  if (target == null) {
                     this.messages.send(player, "general.player-offline", Map.of("player", args[2]));
                     return;
                  }

                  this.partyManager.invite(player, target);
               } else {
                  this.plugin.partyMenuService().openMain(player);
               }
         }
      }
   }

   private void handleSpectate(Player player) {
      if (!player.hasPermission("monaduels.spectate")) {
         this.messages.send(player, "general.no-permission");
      } else if (this.duelManager.spectatorManager() != null) {
         this.duelManager.spectatorManager().openSpectateMenu(player);
      }
   }

   private void handleStats(Player player) {
      StatsManager stats = this.plugin.statsManager();
      player.sendMessage(
         ColorUtil.component(
            this.messages
               .resolve(
                  "stats.self",
                  Map.of(
                     "wins",
                     String.valueOf(stats.getWins(player.getUniqueId())),
                     "losses",
                     String.valueOf(stats.getLosses(player.getUniqueId())),
                     "time",
                     StatsManager.formatTotalTime(stats.getTotalTimeMs(player.getUniqueId()))
                  )
               )
         )
      );

      for (Kit kit : this.kitManager.all()) {
         int elo = stats.getKitElo(player.getUniqueId(), kit.name());
         player.sendMessage(ColorUtil.component(this.messages.resolve("stats.kit-line", Map.of("kit", kit.name(), "elo", String.valueOf(elo)))));
      }
   }

   private void handleResults(Player player, String[] args) {
      if (args.length < 2) {
         this.messages.send(player, "general.invalid-usage", Map.of("usage", "/mduel results <id>"));
      } else {
         this.duelManager.resultService().findResult(args[1]).ifPresentOrElse(result -> {
            if (!this.config.resultsGuiEnabled()) {
               this.messages.send(player, "results.gui-disabled");
            } else {
               this.duelManager.resultService().openResultsGui(player, result);
            }
         }, () -> this.messages.send(player, "results.not-found"));
      }
   }

   private void handleLeave(Player player) {
      if (this.duelManager.spectatorManager() != null && this.duelManager.spectatorManager().isSpectating(player.getUniqueId())) {
         this.duelManager.spectatorManager().leaveSpectate(player);
      } else if (!player.hasPermission("monaduels.use")) {
         this.messages.send(player, "general.no-permission");
      } else if (!this.duelManager.isInDuel(player.getUniqueId())) {
         this.messages.send(player, "duel.not-in-duel");
      } else if (!player.hasPermission("monaduels.bypass.cooldown") && this.cooldowns.isOnCooldown(player, "leave")) {
         this.messages.send(player, "cooldown.active", Map.of("seconds", String.valueOf(this.cooldowns.remainingSeconds(player, "leave"))));
      } else {
         if (!player.hasPermission("monaduels.bypass.cooldown")) {
            this.cooldowns.set(player, "leave", (long)this.config.cooldown("leave", 3) * 1000L);
         }

         this.duelManager.handleLeave(player);
      }
   }

   private void handleAdmin(Player player, String[] args) {
      if (!player.hasPermission("monaduels.admin")) {
         this.messages.send(player, "general.no-permission");
      } else if (args.length < 2) {
         this.messages.send(player, "admin.usage");
      } else {
         String action = args[1].toLowerCase(Locale.ROOT);
         switch (action) {
            case "createkit":
               if (args.length < 3) {
                  this.messages.send(player, "general.invalid-usage", Map.of("usage", "/mduel admin createkit <name>"));
                  return;
               }

               if (this.kitManager.createFromPlayer(args[2], player)) {
                  this.messages.send(player, "kit.created", Map.of("kit", args[2].toLowerCase(Locale.ROOT)));
               }
               break;
            case "editkit":
               if (args.length < 3) {
                  this.messages.send(player, "general.invalid-usage", Map.of("usage", "/duel admin editkit <kit> [addbuff <potion> <level>]"));
                  return;
               }

               String kitName = args[2].toLowerCase(Locale.ROOT);
               Optional<Kit> kitOpt = this.kitManager.find(kitName);
               if (kitOpt.isEmpty()) {
                  this.messages.send(player, "kit.not-found", Map.of("kit", kitName));
                  return;
               }

               if (args.length == 3) {
                  this.kitManager.apply(player, kitOpt.get());
                  return;
               }

               if (args.length < 6 || !args[3].equalsIgnoreCase("addbuff")) {
                  this.messages.send(player, "general.invalid-usage", Map.of("usage", "/duel admin editkit <kit> addbuff <potion> <level>"));
                  return;
               }

               String potionName = args[4];

               int level;
               try {
                  level = Integer.parseInt(args[5]);
               } catch (NumberFormatException var12) {
                  this.messages.send(player, "general.invalid-usage", Map.of("usage", "/duel admin editkit <kit> addbuff <potion> <level>"));
                  return;
               }

               if (level <= 0) {
                  this.messages.send(player, "general.invalid-usage", Map.of("usage", "/duel admin editkit <kit> addbuff <potion> <level>"));
                  return;
               }

               PotionEffectType type = PotionEffectType.getByName(potionName.toUpperCase(Locale.ROOT));
               if (type == null) {
                  type = PotionEffectType.getByKey(NamespacedKey.minecraft(potionName.toLowerCase(Locale.ROOT).replace("minecraft:", "")));
               }

               if (type == null) {
                  this.messages.send(player, "general.invalid-usage", Map.of("usage", "Unknown potion: " + potionName));
                  return;
               }

               PotionEffect effect = new PotionEffect(type, 72000, Math.max(0, level - 1), true, true, true);
               if (this.kitManager.addStartBuff(kitName, effect)) {
                  this.messages.send(player, "kit.buff-added", Map.of("kit", kitName, "buff", type.getKey().toString(), "level", String.valueOf(level)));
               }
               break;
            case "removekit":
               if (args.length < 3) {
                  this.messages.send(player, "general.invalid-usage", Map.of("usage", "/mduel admin removekit <name>"));
                  return;
               }

               if (this.kitManager.remove(args[2])) {
                  this.messages.send(player, "kit.removed", Map.of("kit", args[2].toLowerCase(Locale.ROOT)));
               } else {
                  this.messages.send(player, "kit.not-found", Map.of("kit", args[2]));
               }
               break;
            case "setspawn":
               if (args.length < 4) {
                  this.messages.send(player, "general.invalid-usage", Map.of("usage", "/mduel admin setspawn <arena> <1|2>"));
                  return;
               }

               Optional<Arena> arenaOpt = this.arenaManager.find(args[2]);
               if (arenaOpt.isEmpty()) {
                  this.messages.send(player, "arena.not-found", Map.of("arena", args[2]));
                  return;
               }

               Arena arena = arenaOpt.get();
               String spawnArg = args[3];
               if ("1".equals(spawnArg)) {
                  arena.setSpawn1(player.getLocation());
               } else {
                  if (!"2".equals(spawnArg)) {
                     this.messages.send(player, "general.invalid-usage", Map.of("usage", "/mduel admin setspawn <arena> <1|2>"));
                     return;
                  }

                  arena.setSpawn2(player.getLocation());
               }

               this.arenaManager.save();
               this.messages.send(player, "arena.spawn-set", Map.of("arena", arena.name(), "spawn", spawnArg));
               break;
            case "setspectatorspawn":
               if (args.length < 3) {
                  this.messages.send(player, "general.invalid-usage", Map.of("usage", "/mduel admin setspectatorspawn <arena>"));
                  return;
               }

               Optional<Arena> specArenaOpt = this.arenaManager.find(args[2]);
               if (specArenaOpt.isEmpty()) {
                  this.messages.send(player, "arena.not-found", Map.of("arena", args[2]));
                  return;
               }

               Arena specArena = specArenaOpt.get();
               specArena.setSpectatorSpawn(player.getLocation());
               this.arenaManager.save();
               this.messages.send(player, "arena.spectator-spawn-set", Map.of("arena", specArena.name()));
               break;
            case "createarena":
               if (args.length < 3) {
                  this.messages.send(player, "general.invalid-usage", Map.of("usage", "/mduel admin createarena <name>"));
                  return;
               }

               String arenaName = args[2].toLowerCase(Locale.ROOT);
               if (this.arenaManager.create(arenaName, this.config.duelWorld())) {
                  this.messages.send(player, "arena.created", Map.of("arena", arenaName));
               } else {
                  this.messages.send(player, "general.invalid-usage", Map.of("usage", "Arena already exists"));
               }
               break;
            case "deletearena":
               if (args.length < 3) {
                  this.messages.send(player, "general.invalid-usage", Map.of("usage", "/mduel admin deletearena <name>"));
                  return;
               }

               if (this.arenaManager.delete(args[2])) {
                  this.messages.send(player, "arena.deleted", Map.of("arena", args[2].toLowerCase(Locale.ROOT)));
               } else {
                  this.messages.send(player, "arena.not-found", Map.of("arena", args[2]));
               }
               break;
            case "setlobby":
               this.config.setLobby(player.getLocation());
               this.messages.send(player, "lobby.set");
               break;
            case "reload":
               this.plugin.reload();
               this.messages.send(player, "general.reload-success");
               break;
            default:
               this.messages.send(player, "admin.usage");
         }
      }
   }

   private void sendUsage(Player player) {
      this.messages.send(player, "general.invalid-usage", Map.of("usage", "/mduel <player> | accept | deny | leave | admin ..."));
   }

   private void sendCommandDuelUsage(Player player) {
      this.messages.send(player, "general.invalid-usage", Map.of("usage", "/mduel <player> | accept | deny | leave | admin ..."));
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      if (args.length == 1) {
         List<String> base = new ArrayList<>(Arrays.asList("accept", "deny", "leave"));
         if (sender.hasPermission("monaduels.spectate")) {
            base.add("spectate");
         }

         base.add("results");
         base.add("stats");
         if (sender.hasPermission("monaduels.admin")) {
            base.add("admin");
         }

         if (sender instanceof Player) {
            Bukkit.getOnlinePlayers().forEach(p -> base.add(p.getName()));
         }

         return filter(base, args[0]);
      } else {
         if (args.length >= 2 && args[0].equalsIgnoreCase("admin") && sender.hasPermission("monaduels.admin")) {
            if (args.length == 2) {
               return filter(
                  Arrays.asList("createkit", "editkit", "removekit", "setspawn", "setspectatorspawn", "createarena", "deletearena", "setlobby", "reload"),
                  args[1]
               );
            }

            if (args.length == 3) {
               String var7 = args[1].toLowerCase(Locale.ROOT);

               return switch (var7) {
                  case "setspawn", "setspectatorspawn", "deletearena" -> filter(this.arenaManager.all().stream().map(Arena::name).toList(), args[2]);
                  case "removekit" -> filter(this.kitManager.all().stream().map(k -> k.name()).toList(), args[2]);
                  default -> List.of();
               };
            }

            if (args.length == 4) {
               String basex = args[1].toLowerCase(Locale.ROOT);
               byte var6 = -1;
               switch (basex.hashCode()) {
                  case 1433904217:
                     if (basex.equals("setspawn")) {
                        var6 = 0;
                     }
                  default:
                     return switch (var6) {
                        case 0 -> filter(List.of("1", "2"), args[3]);
                        default -> List.of();
                     };
               }
            }
         }

         return List.of();
      }
   }

   private static List<String> filter(List<String> options, String input) {
      String lower = input.toLowerCase(Locale.ROOT);
      return options.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(lower)).collect(Collectors.toList());
   }

   private static record CommandArgs(String targetName, String kitName, String arenaName) {
   }
}
