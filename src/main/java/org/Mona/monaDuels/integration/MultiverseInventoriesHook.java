package org.Mona.monaDuels.integration;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import org.Mona.monaDuels.MonaDuels;
import org.Mona.monaDuels.arena.Arena;
import org.Mona.monaDuels.arena.ArenaManager;
import org.Mona.monaDuels.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class MultiverseInventoriesHook {
   private static final String PLUGIN_NAME = "Multiverse-Inventories";
   private static final String PLUGIN_CLASS = "org.mvplugins.multiverse.inventories.MultiverseInventories";
   private static final String WRITE_HANDLER_CLASS = "org.mvplugins.multiverse.inventories.handleshare.WriteOnlyShareHandler";
   private static final String WORLD_GROUP_CLASS = "org.mvplugins.multiverse.inventories.profile.group.WorldGroup";
   private static final String SHARABLES_CLASS = "org.mvplugins.multiverse.inventories.share.Sharables";
   private final MonaDuels plugin;
   private final ConfigManager config;
   private final ArenaManager arenaManager;
   private Plugin mvInvPlugin;
   private Object groupManager;
   private Object lastLocationSharable;
   private boolean active;

   public MultiverseInventoriesHook(MonaDuels plugin, ConfigManager config, ArenaManager arenaManager) {
      this.plugin = plugin;
      this.config = config;
      this.arenaManager = arenaManager;
   }

   public void initialize() {
      if (!this.config.mvInventoriesEnabled()) {
         this.plugin.getLogger().info("Multiverse-Inventories integration disabled in config.");
      } else {
         Plugin mvInv = Bukkit.getPluginManager().getPlugin("Multiverse-Inventories");
         if (mvInv != null && mvInv.isEnabled()) {
            try {
               this.mvInvPlugin = mvInv;
               this.groupManager = mvInv.getClass().getMethod("getGroupManager").invoke(mvInv);
               this.lastLocationSharable = this.resolveLastLocationSharable();
               this.active = true;
               if (this.config.mvAutoCreateGroup()) {
                  this.ensureDuelGroup();
               }

               this.plugin.getLogger().info("Multiverse-Inventories hooked. Duel group: " + this.config.mvGroupName());
            } catch (ReflectiveOperationException var3) {
               this.plugin.getLogger().log(Level.WARNING, "Failed to hook Multiverse-Inventories API", (Throwable)var3);
               this.active = false;
            }
         } else {
            this.plugin.getLogger().info("Multiverse-Inventories not found — using built-in inventory snapshots.");
         }
      }
   }

   public boolean isActive() {
      return this.active;
   }

   public void refreshGroup() {
      if (this.active && this.config.mvAutoCreateGroup()) {
         this.ensureDuelGroup();
      }
   }

   public void ensureDuelGroup() {
      if (this.active && this.groupManager != null) {
         try {
            String groupName = this.config.mvGroupName();
            Object group = this.getOrCreateGroup(groupName);
            if (group == null) {
               this.plugin.getLogger().warning("Could not create Multiverse-Inventories group: " + groupName);
               return;
            }

            for (String world : this.collectDuelWorlds()) {
               invoke(group, "addWorld", String.class, world);
            }

            this.removeLobbyFromGroup(group);
            Collection<Object> allShares = this.loadAllSharables();
            if (this.lastLocationSharable != null) {
               allShares.remove(this.lastLocationSharable);
            }

            Collection<Object> shares = (Collection<Object>)invokeNoArgs(group, "getShares");
            shares.clear();
            shares.addAll(allShares);
            this.disableLastLocation(group);
            Class<?> worldGroupClass = Class.forName("org.mvplugins.multiverse.inventories.profile.group.WorldGroup");
            Method updateGroup = this.groupManager.getClass().getMethod("updateGroup", worldGroupClass);
            updateGroup.invoke(this.groupManager, group);
            this.plugin.getLogger().info("Multiverse-Inventories group '" + groupName + "' updated (LAST_LOCATION disabled).");
            this.runDisabledSharesCommand(groupName);
         } catch (ReflectiveOperationException var7) {
            this.plugin.getLogger().log(Level.WARNING, "Failed to update Multiverse-Inventories duel group", (Throwable)var7);
         }
      }
   }

   public void saveCurrentWorldProfile(Player player) {
      if (this.active && this.mvInvPlugin != null) {
         try {
            Class<?> handlerClass = Class.forName("org.mvplugins.multiverse.inventories.handleshare.WriteOnlyShareHandler");
            Constructor<?> ctor = handlerClass.getConstructor(Class.forName("org.mvplugins.multiverse.inventories.MultiverseInventories"), Player.class);
            Object handler = ctor.newInstance(this.mvInvPlugin, player);
            Method handleSharing = handlerClass.getMethod("handleSharing");
            if (handleSharing.invoke(handler) instanceof CompletableFuture<?> completableFuture) {
               completableFuture.join();
            }
         } catch (ReflectiveOperationException var8) {
            this.plugin.getLogger().log(Level.WARNING, "Failed to save MV profile for " + player.getName(), (Throwable)var8);
         }
      }
   }

   public void prepareForArena(Player player) {
      this.saveCurrentWorldProfile(player);
      this.clearDuelLastLocations(player);
   }

   public void prepareForLobbyReturn(Player player) {
      this.saveCurrentWorldProfile(player);
      this.clearDuelLastLocations(player);
   }

   public void clearDuelLastLocations(Player player) {
      if (this.active && this.lastLocationSharable != null) {
         try {
            if (this.mvInvPlugin == null) {
               return;
            }

            Object profileStore = this.mvInvPlugin.getClass().getMethod("getProfileContainerStoreProvider").invoke(this.mvInvPlugin);
            Method getStore = profileStore.getClass()
               .getMethod("getStore", Class.forName("org.mvplugins.multiverse.inventories.profile.container.ContainerType"));
            Class<?> containerType = Class.forName("org.mvplugins.multiverse.inventories.profile.container.ContainerType");
            Object groupType = Enum.valueOf((Class)containerType, "GROUP");
            Object store = getStore.invoke(profileStore, groupType);
            String groupName = this.config.mvGroupName();
            Method getContainer = store.getClass().getMethod("getContainer", String.class);
            Object container = getContainer.invoke(store, groupName);
            Method getProfile = container.getClass().getMethod("getPlayerProfileNow", Player.class);
            Object profile = getProfile.invoke(container, player);
            Method set = profile.getClass().getMethod("set", Class.forName("org.mvplugins.multiverse.inventories.share.Sharable"), Object.class);
            set.invoke(profile, this.lastLocationSharable, null);

            for (String world : this.collectDuelWorlds()) {
               try {
                  Object worldType = Enum.valueOf((Class)containerType, "WORLD");
                  Object worldStore = getStore.invoke(profileStore, worldType);
                  Object worldContainer = getContainer.invoke(worldStore, world);
                  Object worldProfile = getProfile.invoke(worldContainer, player);
                  set.invoke(worldProfile, this.lastLocationSharable, null);
               } catch (ReflectiveOperationException var19) {
               }
            }
         } catch (ReflectiveOperationException var20) {
            if (this.config.debug()) {
               this.plugin.getLogger().log(Level.FINE, "Could not clear MV last_location for " + player.getName(), (Throwable)var20);
            }
         }
      }
   }

   private void removeLobbyFromGroup(Object group) {
      String lobbyWorld = this.config.lobbyWorldName();
      if (lobbyWorld != null && !lobbyWorld.isBlank()) {
         try {
            invoke(group, "removeWorld", String.class, lobbyWorld);
         } catch (ReflectiveOperationException var4) {
         }
      }
   }

   private void disableLastLocation(Object group) throws ReflectiveOperationException {
      if (this.lastLocationSharable != null) {
         try {
            Collection<Object> disabled = (Collection<Object>)invokeNoArgs(group, "getDisabledShares");
            disabled.add(this.lastLocationSharable);
         } catch (ReflectiveOperationException var4) {
            Collection<Object> shares = (Collection<Object>)invokeNoArgs(group, "getShares");
            shares.remove(this.lastLocationSharable);
         }
      }
   }

   private Object resolveLastLocationSharable() {
      try {
         Class<?> sharables = Class.forName("org.mvplugins.multiverse.inventories.share.Sharables");
         Field field = sharables.getField("LAST_LOCATION");
         return field.get(null);
      } catch (ReflectiveOperationException var5) {
         try {
            Class<?> sharablesx = Class.forName("org.mvplugins.multiverse.inventories.share.Sharables");
            Method byName = sharablesx.getMethod("get", String.class);
            return byName.invoke(null, "last_location");
         } catch (ReflectiveOperationException var4) {
            this.plugin.getLogger().warning("Could not resolve Sharables.LAST_LOCATION");
            return null;
         }
      }
   }

   private Object getOrCreateGroup(String groupName) throws ReflectiveOperationException {
      Method getGroup = this.groupManager.getClass().getMethod("getGroup", String.class);
      Object group = getGroup.invoke(this.groupManager, groupName);
      if (group != null) {
         return group;
      } else {
         Method newEmptyGroup = this.groupManager.getClass().getMethod("newEmptyGroup", String.class);
         return newEmptyGroup.invoke(this.groupManager, groupName);
      }
   }

   private Collection<Object> loadAllSharables() throws ReflectiveOperationException {
      Class<?> sharables = Class.forName("org.mvplugins.multiverse.inventories.share.Sharables");
      return (Collection<Object>)sharables.getMethod("allOf").invoke(null);
   }

   public Set<String> collectDuelWorlds() {
      Set<String> worlds = new LinkedHashSet<>();
      worlds.add(this.config.duelWorld());

      for (Arena arena : this.arenaManager.all()) {
         if (arena.world() != null && !arena.world().isBlank()) {
            worlds.add(arena.world());
         }
      }

      String lobby = this.config.lobbyWorldName();
      if (lobby != null) {
         worlds.remove(lobby);
      }

      return worlds;
   }

   private static Object invoke(Object target, String method, Class<?> paramType, Object arg) throws ReflectiveOperationException {
      Method m = target.getClass().getMethod(method, paramType);
      return m.invoke(target, arg);
   }

   private static Object invokeNoArgs(Object target, String method) throws ReflectiveOperationException {
      Method m = target.getClass().getMethod(method);
      return m.invoke(target);
   }

   private void runDisabledSharesCommand(String groupName) {
      Bukkit.getScheduler()
         .runTask(this.plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mvinv adddisabledshares " + groupName + " last_location"));
   }
}
