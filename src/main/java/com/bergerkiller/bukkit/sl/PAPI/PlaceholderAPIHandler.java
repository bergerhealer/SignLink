package com.bergerkiller.bukkit.sl.PAPI;

import java.util.Map;
import java.util.stream.Collectors;

import me.clip.placeholderapi.PlaceholderAPIPlugin;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import com.bergerkiller.bukkit.sl.API.Variable;
import com.bergerkiller.bukkit.sl.API.Variables;

public class PlaceholderAPIHandler {
    private final PlaceholderExpansion hook;
    private boolean registeredSLHook;
    private Map<String, PlaceholderExpansion> plugins;
    private boolean show_on_signs;

    public PlaceholderAPIHandler(JavaPlugin plugin) {
        this.hook = new PlaceholderAPIHook(plugin);
        this.registeredSLHook = false;
    }

    public void enable() {
        this.hook.register();
        if (!PlaceholderAPIPlugin.getInstance().getLocalExpansionManager().getIdentifiers().contains("sl")) {
            this.hook.register();
            this.registeredSLHook = true;
        }
    }

    public void disable() {
        this.hook.unregister();
        if (this.registeredSLHook) {
            this.hook.unregister();
            this.registeredSLHook = false;
        }
    }

    public void refreshPlugins() {
        this.plugins = PlaceholderAPIPlugin.getInstance().getLocalExpansionManager().getExpansions().stream()
                .collect(Collectors.toMap(PlaceholderExpansion::getIdentifier, ex -> ex));
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
        return this.plugins.containsKey(pluginName);
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
        PlaceholderExpansion hook = this.plugins.get(pluginName);
        if (hook == null) {
            return null;
        }

        String name = variableName.substring(pluginIdx + 1);
        return hook.onPlaceholderRequest(player, name);
    }

}
