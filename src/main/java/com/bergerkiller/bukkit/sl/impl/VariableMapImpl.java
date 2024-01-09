package com.bergerkiller.bukkit.sl.impl;

import com.bergerkiller.bukkit.common.Common;
import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bergerkiller.bukkit.common.config.yaml.YamlPath;
import com.bergerkiller.bukkit.sl.API.PlayerVariable;
import com.bergerkiller.bukkit.sl.API.Variable;
import com.bergerkiller.bukkit.sl.SignLink;
import com.bergerkiller.mountiplex.MountiplexUtil;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Function;

/**
 * The actual implementation as used by SignLink on the server.
 * Includes a mechanism to load and save a persistent values.yml file.
 */
public class VariableMapImpl extends VariableMap {
    private final Map<String, ChangeState> changes = new HashMap<>();
    private FileConfiguration values;

    /**
     * Loads the values.yml file and creates and initializes all variables using it
     *
     * @param plugin SignLink plugin instance
     */
    public void load(SignLink plugin) {
        values = new FileConfiguration(plugin, "values.yml");
        if (!values.exists()) {
            values.set("test.ticker", "LEFT");
            values.set("test.tickerInterval", 3);
            values.set("test.value", "This is a test message being ticked from right to left. ");
            values.set("sign.ticker", "NONE");
            values.set("sign.value", "This is a regular message you can set and is updated only once.");
            values.save();
        }
        values.load();

        // Before loading make sure all variables are removed (forAll is concurrent safe)
        forAll(v -> remove(v.getName()));

        // Initialize from values.yml
        for (ConfigurationNode node : values.getNodes()) {
            Variable var = get(node.getName());
            var.setDefault(node.get("value", Variable.createDefaultValue(var.getName())));
            var.getDefaultTicker().load(node);
            if (node.isNode("forPlayers")) {
                for (ConfigurationNode forplayer : node.getNode("forPlayers").getNodes()) {
                    String value = forplayer.get("value", String.class, null);
                    PlayerVariable pvar = var.forPlayer(forplayer.getName());
                    if (value != null) {
                        pvar.set(value);
                    }
                    pvar.getTicker().load(forplayer);
                }
            }
        }
        changes.clear();
        forAll(v -> v.storedChangeState = ChangeState.UNCHANGED);
    }

    /**
     * Collects all variables that have changed and updates the values.yml file.
     * If autosave is false, forces a complete re-saving of all variables rather than
     * only updating the variables that have changed.
     *
     * @param autosave Whether this is an incremental auto-save or a full re-save
     */
    public void save(boolean autosave) {
        if (!autosave) {
            // Reset and re-save everything
            values.clear();
            forAll(this::saveVariable);
        } else if (!changes.isEmpty()) {
            // Apply all changes and save
            // We can ignore change state. If the variable doesn't exist it is presumed removed,
            // and otherwise we just save it anyway.
            for (String variableName : changes.keySet()) {
                Variable variable = getIfExists(variableName);
                if (variable == null) {
                    values.remove(variableName);
                } else {
                    saveVariable(variable);
                }
            }
        } else {
            // No changes.
            return;
        }

        changes.clear();
        forAll(v -> v.storedChangeState = ChangeState.UNCHANGED);
        values.save();
    }

    // This crap is needed so that variable names with '.' in it can't break everything
    private static final Function<String, YamlPath> createVarPath = findCreateVarPathFunc();
    private static Function<String, YamlPath> findCreateVarPathFunc() {
        try {
            if (Common.hasCapability("Common:Yaml:ChildWithLiteralName")) {
                // New API for this
                return getCreateVarPathNewApi();
            } else {
                // Old BKCL fallback
                Constructor<YamlPath> ctor = YamlPath.class.getDeclaredConstructor(YamlPath.class, String.class);
                ctor.setAccessible(true);
                return name -> {
                    try {
                         return ctor.newInstance(YamlPath.ROOT, name);
                    } catch (Throwable t) {
                        throw MountiplexUtil.uncheckedRethrow(t);
                    }
                };
            }
        } catch (Throwable t) {
            return s -> { throw new RuntimeException(t); };
        }
    }
    private static Function<String, YamlPath> getCreateVarPathNewApi() {
        return YamlPath.ROOT::childWithName;
    }

    private void saveVariable(Variable variable) {
        ConfigurationNode node = values.getNode(createVarPath.apply(variable.getName()));

        node.set("value", variable.getDefault());
        variable.getDefaultTicker().save(node);

        Iterator<PlayerVariable> pvar_iter = variable.forAll().iterator();
        if (pvar_iter.hasNext()) {
            ConfigurationNode forPlayers = node.getNode("forPlayers");
            forPlayers.clear();
            do {
                PlayerVariable pvar = pvar_iter.next();
                ConfigurationNode forplayer = forPlayers.getNode(pvar.getPlayer());
                forplayer.set("value", pvar.get());
                if (!pvar.isTickerShared()) {
                    pvar.getTicker().save(forplayer);
                }
            } while (pvar_iter.hasNext());
        } else {
            node.remove("forPlayers");
        }
    }

    @Override
    protected void onVariableRemoved(Variable variable) {
        // If previous state was CREATED then that means it was never written to yaml
        // In that case simply remove the variable from the change tracking
        // In all other cases the variable must be marked removed
        {
            VariableImpl variableImpl = (VariableImpl) variable;
            ChangeState oldState = variableImpl.storedChangeState;
            if (oldState == ChangeState.CREATED) {
                changes.remove(variable.getName());
            } else {
                changes.put(variable.getName(), ChangeState.REMOVED);
            }
            variableImpl.storedChangeState = ChangeState.REMOVED;
        }

        // Clean up from the editing metadata
        SignLink.plugin.removeEditing(variable);
    }

    @Override
    protected void onVariableCreated(Variable variable) {
        // In this situation we don't know a prior state, so ask the hashmap
        ((VariableImpl) variable).storedChangeState = changes.compute(variable.getName(),
                (name, oldState) -> (oldState == null) ? ChangeState.CREATED : ChangeState.CHANGED);

        // If PAPI is enabled, initialize the variable values
        if (SignLink.plugin.papi != null) {
            SignLink.plugin.papi.refreshVariableForAll(variable);
        }
    }

    @Override
    protected void onVariableChanged(Variable variable) {
        VariableImpl variableImpl = (VariableImpl) variable;
        ChangeState oldState = variableImpl.storedChangeState;
        ChangeState newState = oldState.change();
        if (oldState != newState) {
            variableImpl.storedChangeState = newState;
            changes.put(variableImpl.getName(), newState);
        }
    }
}
