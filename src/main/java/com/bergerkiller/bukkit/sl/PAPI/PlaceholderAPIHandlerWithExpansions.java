package com.bergerkiller.bukkit.sl.PAPI;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;

import com.bergerkiller.bukkit.common.RunOnceTask;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.sl.SignLink;
import com.bergerkiller.bukkit.sl.API.Variable;
import com.bergerkiller.bukkit.sl.API.Variables;

import me.clip.placeholderapi.PlaceholderAPIPlugin;
import me.clip.placeholderapi.events.ExpansionRegisterEvent;
import me.clip.placeholderapi.events.ExpansionUnregisterEvent;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.clip.placeholderapi.expansion.manager.LocalExpansionManager;

/**
 * Uses the PlaceHolderAPI LocalExpansionManager to track what expansions are registered
 */
public class PlaceholderAPIHandlerWithExpansions implements PlaceholderAPIHandler, Listener {
    private final SignLink plugin;
    private final List<Hook> hooks;
    private boolean show_on_signs;

    // This is kept up-to-date when plugins or expansions enable/disable
    private Map<String, PlaceholderExpansion> expansions;

    // This task runs the next tick to do a full expansions mapping refresh
    // Makes sure it stays up-to-date when a change is detected
    private final RunOnceTask expansionsUpdateTask;

    public PlaceholderAPIHandlerWithExpansions(SignLink plugin) {
        this.hooks = new ArrayList<Hook>();
        this.plugin = plugin;
        this.expansions = Collections.emptyMap();
        this.expansionsUpdateTask = RunOnceTask.create(plugin, this::updateExpansions);
    }

    public void enable() {
        // Hook as signlink, only hook as 'sl' if not already an expansion
        LocalExpansionManager manager = PlaceholderAPIPlugin.getInstance().getLocalExpansionManager();

        Hook mainHook = new Hook(this.plugin, "signlink");
        if (manager.register(mainHook)) {
            hooks.add(mainHook);
        }

        Hook aliasHook = new Hook(this.plugin, "sl");
        if (manager.getExpansion("sl") == null && manager.register(aliasHook)) {
            hooks.add(aliasHook);
        }

        this.updateExpansions();

        // Register listener for changes
        this.plugin.register(this);
    }

    public void disable() {
        // Make sure this doesn't run again, could be bad
        this.expansionsUpdateTask.cancel();

        // Unregister hooks
        LocalExpansionManager manager = PlaceholderAPIPlugin.getInstance().getLocalExpansionManager();
        for (Hook hook : hooks) {
            manager.unregister(hook);
        }

        // Unregister listener
        CommonUtil.unregisterListener(this);
    }

    public void setShowOnSigns(boolean showOnSigns) {
        this.show_on_signs = showOnSigns;
    }

    /**
     * Refreshes the PAPI variables displayed to a particular Player
     */
    public void refreshVariables(Player player) {
        if (!this.show_on_signs) {
            return;
        }
        for (Variable var : Variables.getAll()) {
            if (!isHookedVariable(var.getName())) {
                continue;
            }

            String value = this.getVariableValue(player, var.getName());
            if (value != null) {
                var.forPlayer(player).set(value);
            }
        }
    }

    public void refreshVariableForAll(Variable var) {
        if (!this.show_on_signs) {
            return;
        }
        if (isHookedVariable(var.getName())) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                String value = this.getVariableValue(player, var.getName());
                if (value != null) {
                    var.forPlayer(player).set(value);
                }
            }
        }
    }

    /**
     * Checks whether a particular variable is a potential PlaceholderAPI hooked variable name
     * 
     * @param variableName to check
     * @return True of a PAPI variable
     */
    public boolean isHookedVariable(String variableName) {
        int pluginIdx = variableName.indexOf('_');
        if (pluginIdx == -1) {
            return false;
        }

        String pluginName = variableName.substring(0, pluginIdx);

        // This shit is way too slow! Use a cached map we track ourselves, instead.
        //return PlaceholderAPI.isRegistered(pluginName);
        return this.expansions.containsKey(pluginName.toLowerCase());
    }

    /**
     * Obtains the value of a variable according to PlaceholderAPI.
     * If the variable is not in the PAPI format or does not exist, null is returned.
     * 
     * @param player to get the value of the variable for
     * @param variableName to get (identifier_varname format)
     * @return PlaceholderAPI variable name
     */
    public String getVariableValue(Player player, String variableName) {
        int pluginIdx = variableName.indexOf('_');
        if (pluginIdx == -1) {
            return null;
        }

        String pluginName = variableName.substring(0, pluginIdx);

        // Too slow!!! Makes an entire copy of the expansions map every time. Ew.
        //PlaceholderExpansion expansion = PlaceholderAPIPlugin.getInstance().getLocalExpansionManager().getExpansion(pluginName);
        PlaceholderExpansion expansion = this.expansions.get(pluginName.toLowerCase());
        if (expansion == null) {
            return null;
        }

        String name = variableName.substring(pluginIdx + 1);
        try {
            return expansion.onRequest(player, name);
        } catch (Throwable t) {
            t.printStackTrace();
            return null;
        }
    }

    private void updateExpansions() {
        Map<String, PlaceholderExpansion> new_expansions = new HashMap<>();
        for (PlaceholderExpansion expansion : PlaceholderAPIPlugin.getInstance().getLocalExpansionManager().getExpansions()) {
            new_expansions.put(expansion.getName().toLowerCase(), expansion);
        }
        this.expansions = Collections.unmodifiableMap(new_expansions);
    }

    private void modifyExpansions(Consumer<Map<String, PlaceholderExpansion>> modifier) {
        Map<String, PlaceholderExpansion> new_expansions = new HashMap<>(this.expansions);
        modifier.accept(new_expansions);
        this.expansions = Collections.unmodifiableMap(new_expansions);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginEnabled(PluginEnableEvent event) {
        // Just in case, refresh now and schedule another one next tick
        this.updateExpansions();
        this.expansionsUpdateTask.start();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisabled(PluginDisableEvent event) {
        // Just in case, refresh now and schedule another one next tick
        this.updateExpansions();
        this.expansionsUpdateTask.start();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onExpansionRegistered(final ExpansionRegisterEvent event) {
        if (!event.isCancelled()) {
            // Add expansion to our map when not cancelled
            this.modifyExpansions(map -> map.put(event.getExpansion().getName().toLowerCase(), event.getExpansion()));
        }

        // Just in case, always schedule a refresh
        this.expansionsUpdateTask.start();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onExpansionUnregistered(final ExpansionUnregisterEvent event) {
        // Remove expansion from our map and schedule a refresh the next tick
        this.modifyExpansions(map -> map.remove(event.getExpansion().getName().toLowerCase()));
        this.expansionsUpdateTask.start();
    }

    /**
     * This hook makes it possible to use SignLink variables as
     * placeholders.
     */
    private static class Hook extends PlaceholderExpansion {
        private final SignLink plugin;
        private final String identifier;

        public Hook(SignLink plugin, String identifier) {
            this.plugin = plugin;
            this.identifier = identifier;
        }

        @Override
        public String getIdentifier() {
            return this.identifier;
        }

        @Override
        public String getAuthor() {
            return this.plugin.getDescription().getAuthors().get(0);
        }

        @Override
        public String getVersion() {
            return this.plugin.getDescription().getVersion();
        }

        @Override
        public List<String> getPlaceholders() {
            return Arrays.asList(Variables.getNames());
        }

        @Override
        public String onRequest(final OfflinePlayer player, String identifier) {
            Variable variable = Variables.getIfExists(identifier);
            if (variable == null) {
                return null;
            }
            return variable.get(player.getName());
        }
    }
}
