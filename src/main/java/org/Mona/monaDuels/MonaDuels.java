package org.Mona.monaDuels;

import org.Mona.monaDuels.arena.ArenaManager;
import org.Mona.monaDuels.arena.MapPoolManager;
import org.Mona.monaDuels.celebration.CelebrationService;
import org.Mona.monaDuels.command.CommandDuelService;
import org.Mona.monaDuels.command.MduelCommand;
import org.Mona.monaDuels.config.ConfigManager;
import org.Mona.monaDuels.config.MessageService;
import org.Mona.monaDuels.cooldown.CooldownManager;
import org.Mona.monaDuels.display.DuelBossBarService;
import org.Mona.monaDuels.display.DuelDisplayManager;
import org.Mona.monaDuels.duel.DuelManager;
import org.Mona.monaDuels.gui.CelebrationMenuService;
import org.Mona.monaDuels.gui.KitLayoutEditorService;
import org.Mona.monaDuels.gui.KitPreviewService;
import org.Mona.monaDuels.gui.KitSelectionService;
import org.Mona.monaDuels.gui.PartyMenuService;
import org.Mona.monaDuels.gui.SettingsMenuService;
import org.Mona.monaDuels.gui.TrimsService;
import org.Mona.monaDuels.kit.KitManager;
import org.Mona.monaDuels.listener.CelebrationMenuListener;
import org.Mona.monaDuels.listener.DuelBlockListener;
import org.Mona.monaDuels.listener.DuelListener;
import org.Mona.monaDuels.listener.DuelProtectionListener;
import org.Mona.monaDuels.listener.KitLayoutEditorListener;
import org.Mona.monaDuels.listener.KitPreviewListener;
import org.Mona.monaDuels.listener.LobbyItemListener;
import org.Mona.monaDuels.listener.PlayAgainListener;
import org.Mona.monaDuels.listener.PartyLifecycleListener;
import org.Mona.monaDuels.listener.PartyMenuListener;
import org.Mona.monaDuels.listener.PearlBarrierListener;
import org.Mona.monaDuels.listener.ResultsGuiListener;
import org.Mona.monaDuels.listener.SettingsMenuListener;
import org.Mona.monaDuels.listener.TrimsListener;
import org.Mona.monaDuels.lobby.LobbyLayoutService;
import org.Mona.monaDuels.menu.MenuManager;
import org.Mona.monaDuels.party.PartyManager;
import org.Mona.monaDuels.party.PartyRequestManager;
import org.Mona.monaDuels.player.PlayerDataManager;
import org.Mona.monaDuels.queue.PlayAgainService;
import org.Mona.monaDuels.queue.QueueManager;
import org.Mona.monaDuels.request.DuelRequestMessenger;
import org.Mona.monaDuels.request.RequestManager;
import org.Mona.monaDuels.service.DuelChallengeService;
import org.Mona.monaDuels.service.DuelResultService;
import org.Mona.monaDuels.service.DuelSoundService;
import org.Mona.monaDuels.service.TrimService;
import org.Mona.monaDuels.spectator.SpectateMenuListener;
import org.Mona.monaDuels.spectator.SpectatorManager;
import org.Mona.monaDuels.stats.StatsManager;
import org.Mona.monaDuels.team.TeamDuelManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class MonaDuels extends JavaPlugin {
   private ConfigManager configManager;
   private MessageService messageService;
   private ArenaManager arenaManager;
   private MapPoolManager mapPoolManager;
   private KitManager kitManager;
   private MenuManager menuManager;
   private CooldownManager cooldownManager;
   private RequestManager requestManager;
   private DuelManager duelManager;
   private TeamDuelManager teamDuelManager;
   private KitSelectionService kitSelectionService;
   private DuelChallengeService challengeService;
   private DuelDisplayManager displayManager;
   private DuelBossBarService bossBarService;
   private DuelSoundService soundService;
   private DuelResultService resultService;
   private StatsManager statsManager;
   private SpectatorManager spectatorManager;
   private PlayerDataManager playerDataManager;
   private LobbyLayoutService lobbyLayoutService;
   private PartyManager partyManager;
   private PartyRequestManager partyRequestManager;
   private PartyMenuService partyMenuService;
   private CelebrationService celebrationService;
   private CelebrationMenuService celebrationMenuService;
   private CommandDuelService commandDuelService;
   private KitPreviewService kitPreviewService;
   private KitLayoutEditorService kitLayoutEditorService;
   private TrimService trimService;
   private TrimsService trimsService;
   private SettingsMenuService settingsMenuService;
   private QueueManager queueManager;
   private PlayAgainService playAgainService;

   public void onEnable() {
      this.configManager = new ConfigManager(this);
      this.configManager.loadAll();
      this.messageService = new MessageService(this.configManager);
      this.statsManager = new StatsManager(this, this.configManager);
      this.playerDataManager = new PlayerDataManager(this);
      this.playerDataManager.load();
      this.arenaManager = new ArenaManager(this.configManager);
      this.arenaManager.load();
      this.kitManager = new KitManager(this);
      this.kitManager.load();
      this.mapPoolManager = new MapPoolManager(this.configManager);
      this.mapPoolManager.load();
      this.mapPoolManager.validate(this.arenaManager, this.kitManager);
      this.menuManager = new MenuManager(this);
      this.menuManager.initialize();
      this.bossBarService = new DuelBossBarService(this.configManager, this.kitManager, this.playerDataManager);
      this.soundService = new DuelSoundService(this.configManager);
      this.displayManager = new DuelDisplayManager(
         this, this.configManager, this.messageService, this.bossBarService, this.soundService, this.playerDataManager
      );
      this.resultService = new DuelResultService(this.configManager, this.messageService, this.statsManager);
      DuelRequestMessenger requestMessenger = new DuelRequestMessenger(this.configManager, this.messageService, this.kitManager);
      this.cooldownManager = new CooldownManager();
      this.requestManager = new RequestManager(this.configManager, this.messageService, requestMessenger);
      this.partyManager = new PartyManager(this.configManager, this.messageService);
      this.partyRequestManager = new PartyRequestManager(this.configManager, this.messageService);
      this.celebrationService = new CelebrationService(this, this.configManager, this.playerDataManager, this.messageService);
      this.duelManager = new DuelManager(
         this,
         this.configManager,
         this.messageService,
         this.arenaManager,
         this.mapPoolManager,
         this.kitManager,
         this.requestManager,
         this.cooldownManager,
         this.displayManager,
         this.resultService,
         this.soundService,
         this.statsManager,
         this.playerDataManager
      );
      this.teamDuelManager = new TeamDuelManager(
         this, this.configManager, this.messageService, this.arenaManager, this.mapPoolManager, this.kitManager, this.partyManager, this.partyRequestManager
      );
      this.teamDuelManager.bindCelebrationService(this.celebrationService);
      this.duelManager.bindTeamDuelManager(this.teamDuelManager);
      this.duelManager.bindCelebrationService(this.celebrationService);
      this.spectatorManager = new SpectatorManager(
         this, this.configManager, this.messageService, this.duelManager, this.kitManager, this.bossBarService, this.displayManager
      );
      this.duelManager.bindSpectatorManager(this.spectatorManager);
      this.lobbyLayoutService = new LobbyLayoutService(this, this.configManager, this.kitManager, this.playerDataManager, this.duelManager);
      this.lobbyLayoutService.load();
      this.kitSelectionService = new KitSelectionService(
         this,
         this.configManager,
         this.messageService,
         this.menuManager,
         this.kitManager,
         this.requestManager,
         this.playerDataManager,
         this.partyManager,
         this.partyRequestManager
      );
      this.challengeService = new DuelChallengeService(
         this.configManager, this.messageService, this.cooldownManager, this.duelManager, this.requestManager, this.kitSelectionService
      );
      this.commandDuelService = new CommandDuelService(
         this.configManager,
         this.messageService,
         this.cooldownManager,
         this.duelManager,
         this.kitManager,
         this.arenaManager,
         this.mapPoolManager,
         this.playerDataManager,
         this.requestManager
      );
      this.partyMenuService = new PartyMenuService(
         this.messageService, this.partyManager, this.partyRequestManager, this.kitSelectionService, this.teamDuelManager, this.duelManager
      );
      this.celebrationMenuService = new CelebrationMenuService(this, this.celebrationService, this.kitManager, this.messageService);
      this.trimService = new TrimService(this.configManager, this.playerDataManager);
      this.kitManager.bindTrims(this.trimService);
      this.settingsMenuService = new SettingsMenuService(this.configManager, this.playerDataManager);
      this.kitPreviewService = new KitPreviewService(this, this.configManager, this.kitManager, this.playerDataManager, this.messageService);
      this.kitLayoutEditorService = new KitLayoutEditorService(
         this, this.configManager, this.kitManager, this.playerDataManager, this.messageService, this.duelManager
      );
      this.trimsService = new TrimsService(this, this.configManager, this.messageService, this.kitManager, this.playerDataManager, this.trimService);
      this.queueManager = new QueueManager(this, this.configManager, this.messageService, this.duelManager, this.kitManager, this.mapPoolManager, this.arenaManager);
      this.playAgainService = new PlayAgainService(this, this.configManager, this.kitManager);
      this.kitPreviewService.bindEditor(this.kitLayoutEditorService);
      this.kitPreviewService.bindTrims(this.trimsService);
      this.kitPreviewService.bindCelebrationMenu(this.celebrationMenuService);
      this.kitPreviewService.bindKitSelection(this.kitSelectionService);
      this.kitLayoutEditorService.bindQueue(this.queueManager);
      this.kitLayoutEditorService.bindPreview(this.kitPreviewService);
      this.trimsService.bindPreview(this.kitPreviewService);
      this.celebrationMenuService.bindPreview(this.kitPreviewService);
      this.queueManager.bindEditor(this.kitLayoutEditorService);
      this.queueManager.bindLobbyLayout(this.lobbyLayoutService);
      this.queueManager.bindPlayerDataManager(this.playerDataManager);
      this.lobbyLayoutService.bindEditor(this.kitLayoutEditorService);
      this.kitSelectionService.bindPreview(this.kitPreviewService);
      this.kitSelectionService.bindQueue(this.queueManager);
      this.duelManager.bindQueueManager(this.queueManager);
      this.duelManager.bindPlayAgainService(this.playAgainService);
      this.registerCommands();
      this.registerListeners();
      this.getLogger().info("MonaDuels enabled.");
   }

   public void onDisable() {
      if (this.teamDuelManager != null) {
         this.teamDuelManager.shutdown();
      }

      if (this.duelManager != null) {
         this.duelManager.shutdown();
      }

      if (this.statsManager != null) {
         this.statsManager.save();
      }

      if (this.kitLayoutEditorService != null) {
         this.kitLayoutEditorService.shutdown();
      }

      if (this.playerDataManager != null) {
         this.playerDataManager.save();
      }

      this.getLogger().info("MonaDuels disabled.");
   }

   public void reload() {
      this.configManager.reloadAll();
      this.statsManager.reload();
      this.arenaManager.load();
      this.kitManager.load();
      this.mapPoolManager.load();
      this.mapPoolManager.validate(this.arenaManager, this.kitManager);
      this.menuManager.reloadMenus();
      if (this.playerDataManager != null) {
         this.playerDataManager.load();
      }

      if (this.lobbyLayoutService != null) {
         this.lobbyLayoutService.load();
      }

      if (this.celebrationService != null) {
         this.celebrationService.reloadEffects();
      }
   }

   public DuelManager duelManager() {
      return this.duelManager;
   }

   public TeamDuelManager teamDuelManager() {
      return this.teamDuelManager;
   }

   public PartyManager partyManager() {
      return this.partyManager;
   }

   public PartyRequestManager partyRequestManager() {
      return this.partyRequestManager;
   }

   public LobbyLayoutService lobbyLayoutService() {
      return this.lobbyLayoutService;
   }

   public StatsManager statsManager() {
      return this.statsManager;
   }

   public PartyMenuService partyMenuService() {
      return this.partyMenuService;
   }

   private void registerCommands() {
      PluginCommand command = this.getCommand("mduel");
      if (command == null) {
         this.getLogger().severe("Command 'mduel' is not defined in plugin.yml");
      } else {
         MduelCommand executor = new MduelCommand(
            this,
            this.configManager,
            this.messageService,
            this.arenaManager,
            this.kitManager,
            this.duelManager,
            this.teamDuelManager,
            this.cooldownManager,
            this.challengeService,
            this.kitSelectionService,
            this.partyManager,
            this.partyRequestManager,
            this.commandDuelService
         );
         command.setExecutor(executor);
         command.setTabCompleter(executor);
      }
   }

   private void registerListeners() {
      this.getServer().getPluginManager().registerEvents(new DuelListener(this, this.configManager, this.duelManager, this.teamDuelManager), this);
      this.getServer().getPluginManager().registerEvents(new DuelProtectionListener(this.configManager, this.messageService, this.duelManager), this);
      this.getServer().getPluginManager().registerEvents(new PearlBarrierListener(this.duelManager, this.teamDuelManager), this);
      this.getServer().getPluginManager().registerEvents(new DuelBlockListener(this.configManager, this.duelManager, this.teamDuelManager), this);
      this.getServer().getPluginManager().registerEvents(new SpectateMenuListener(this.configManager, this.spectatorManager), this);
      this.getServer().getPluginManager().registerEvents(new ResultsGuiListener(this.configManager), this);
      this.getServer()
         .getPluginManager()
         .registerEvents(
            new LobbyItemListener(
               this,
               this.lobbyLayoutService,
               this.kitSelectionService,
               this.partyMenuService,
               this.settingsMenuService,
               this.duelManager,
               this.messageService,
               this.kitLayoutEditorService,
               this.playerDataManager,
               this.queueManager
            ),
            this
         );
      this.getServer().getPluginManager().registerEvents(new PartyMenuListener(this.partyMenuService), this);
      this.getServer().getPluginManager().registerEvents(new CelebrationMenuListener(this.celebrationMenuService), this);
      this.getServer().getPluginManager().registerEvents(new PartyLifecycleListener(this.partyManager), this);
      this.getServer().getPluginManager().registerEvents(new KitPreviewListener(this.kitPreviewService), this);
      this.getServer().getPluginManager().registerEvents(new KitLayoutEditorListener(this.kitLayoutEditorService), this);
      this.getServer().getPluginManager().registerEvents(new TrimsListener(this.trimsService), this);
      this.getServer().getPluginManager().registerEvents(new SettingsMenuListener(this.settingsMenuService), this);
      this.getServer()
         .getPluginManager()
         .registerEvents(new PlayAgainListener(this.playAgainService, this.queueManager, this.duelManager, this.messageService), this);
   }
}
