package com.bergerkiller.bukkit.sl.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.block.Block;

import com.bergerkiller.bukkit.common.collections.ImplicitlySharedSet;
import com.bergerkiller.bukkit.sl.LinkedSign;
import com.bergerkiller.bukkit.sl.SignLink;
import com.bergerkiller.bukkit.sl.API.Variable;

/**
 * Stores all the variable made available by the plugin
 */
public class VariableMap {
    /**
     * Default instance, which is used at runtime.
     * For tests, please create a new VariableMap
     * each time, instead.
     */
    public static final VariableMap INSTANCE = new VariableMap() {
        @Override
        protected void onVariableRemoved(Variable variable) {
            // Clean up from the editing metadata
            SignLink.plugin.removeEditing(variable);
        }

        @Override
        protected void onVariableCreated(Variable variable) {
            // If PAPI is enabled, initialize the variable values
            if (SignLink.plugin.papi != null) {
                SignLink.plugin.papi.refreshVariableForAll(variable);
            }
        }
    };

    private final HashMap<String, VariableImpl> variablesMap = new HashMap<String, VariableImpl>();
    private final ImplicitlySharedSet<VariableImpl> variablesSet = new ImplicitlySharedSet<VariableImpl>();

    /**
     * Callback called when a variable was deleted
     *
     * @param variable Variable that was deleted
     */
    protected void onVariableRemoved(Variable variable) {
    }

    /**
     * Callback called when a new variable is created
     *
     * @param variable Variable that was created
     */
    protected void onVariableCreated(Variable variable) {
    }

    public synchronized void deinit() {
        variablesMap.clear();
        variablesSet.clear();
    }

    /**
     * Updates all the tickers of all the Variables on the server
     */
    public void updateTickers() {
        for (VariableImpl var : variablesSet.cloneAsIterable()) {
            var.getValueMap().updateTickers();
        }
    }

    /**
     * Gets a current copy of all the variables on the server.
     * Use java 8's try-with-resources idiom to use this collection
     * to prevent unneeded copying. Example:<br>
     * <pre> 
     * try (ImplicitlySharedSet&lt;VariableImpl&gt; tmp = map.all()) {
     *     // Do stuff with the temporary copy
     *     for (VariableImpl var : tmp) {
     *         System.out.println(var.getName());
     *     }
     * }
     * </pre>
     * 
     * @return Collection of all variables
     */
    public ImplicitlySharedSet<VariableImpl> all() {
        return variablesSet.clone();
    }

    /**
     * Gets a new mutual list of all variables available
     * 
     * @return Variables
     */
    public List<Variable> getAllAsList() {
        try (ImplicitlySharedSet<VariableImpl> tmp = all()) {
            return new ArrayList<Variable>(tmp);
        }
    }

    /**
     * Gets an array of all variables available
     * 
     * @return Variables
     */
    public Variable[] getAll() {
        try (ImplicitlySharedSet<VariableImpl> tmp = all()) {
            return tmp.toArray(new Variable[tmp.size()]);
        }
    }

    /**
     * Gets an array of all variable names
     * 
     * @return Variable names
     */
    public synchronized String[] getNames() {
        return variablesMap.keySet().toArray(new String[0]);
    }

    /**
     * Sends all existing variables to the consumer.
     * This is done synchronized, so during this time no variables
     * will be removed or created, unless done in this method.
     *
     * @param consumer
     */
    public synchronized void forAll(Consumer<VariableImpl> consumer) {
        for (VariableImpl var : variablesSet.cloneAsIterable()) {
            consumer.accept(var);
        }
    }

    /**
     * Gets or creates a variable of the given name
     * 
     * @param name of the variable
     * @return the Variable, or null if the name is of an unsupported format
     */
    public synchronized VariableImpl get(String name) {
        if (name == null || name.contains("\000")) {
            return null;
        }
        VariableImpl var = variablesMap.get(name);
        if (var == null) {
            var = new VariableImpl(this, name);
            variablesMap.put(name, var);
            variablesSet.add(var);
            onVariableCreated(var);
        }
        return var;
    }

    /**
     * Gets a variable of the given name. Returns null if it does not exist.
     * 
     * @param name of the variable
     * @return the Variable
     */
    public synchronized Variable getIfExists(String name) {
        return variablesMap.get(name);
    }

    /**
     * Removes (and thus clears) a Variable from the server
     * 
     * @param name of the Variable to remove
     * @return True if the variable was removed, False if it was not found
     */
    public synchronized boolean remove(String name) {
        Variable var = variablesMap.remove(name);
        if (var != null) {
            variablesSet.remove(var);
            onVariableRemoved(var);
            return true;
        }
        return false;
    }

    /**
     * Attempts to find all linked signs and variables for a given Block.
     * The amount of signs and variables returned are the same, and match each other.
     * Multiple variables could be contained in the List.
     * The Linked Sign at signs index X shows the Variable at variables index X.
     * 
     * @param signs to fill with results (null to ignore)
     * @param variables to fill with results (null to ignore)
     * @param at Block to find
     * @return True if something was found, False if not
     */
    public synchronized boolean find(List<LinkedSign> signs, List<VariableImpl> variables, Block at) {
        boolean found = false;
        for (VariableImpl var : variablesSet.cloneAsIterable()) {
            if (var.find(signs, variables, at)) {
                found = true;
            }
        }
        return found;
    }

    /**
     * Attempts to find all linked signs and variables for a given Block.
     * The amount of signs and variables returned are the same, and match each other.
     * Multiple variables could be contained in the List.
     * The Linked Sign at signs index X shows the Variable at variables index X.
     * 
     * @param signs to fill with results (null to ignore)
     * @param variables to fill with results (null to ignore)
     * @param at Block Location to find
     * @return True if something was found, False if not
     */
    public boolean find(List<LinkedSign> signs, List<VariableImpl> variables, Location at) {
        return find(signs, variables, at.getBlock());
    }
}
