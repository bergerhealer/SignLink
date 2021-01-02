package com.bergerkiller.bukkit.sl.API;

import java.util.Arrays;
import java.util.Collections;

import org.bukkit.Bukkit;

import com.bergerkiller.bukkit.sl.impl.MultiPlayerTicker;
import com.bergerkiller.bukkit.sl.impl.PlayerVariableImpl;
import com.bergerkiller.bukkit.sl.impl.VariableImpl;
import com.bergerkiller.bukkit.sl.impl.VariableValueMap;

/**
 * Groups multiple player-specific variables together as one
 */
public class GroupVariable implements VariableValue {
    private PlayerVariable[] players;
    private Variable variable;
    private final MultiPlayerTicker ticker;

    public GroupVariable(PlayerVariable[] players, VariableImpl variable) {
        this.players = players;
        this.variable = variable;
        if (players.length == 0) {
            this.ticker = new MultiPlayerTicker(Collections.emptySet(), variable);
        } else {
            VariableValueMap.Entry[] entries = new VariableValueMap.Entry[players.length];
            for (int i = 0; i < players.length; i++) {
                entries[i] = ((PlayerVariableImpl) players[i]).getEntry();
            }
            this.ticker = new MultiPlayerTicker(Arrays.asList(entries), entries[0]);
        }
    }

    @Override
    public String get() {
        if (this.players.length == 0) {
            return this.variable.getDefault();
        } else {
            return this.players[0].get();
        }
    }

    /**
     * Gets the separate player variables of this group
     * 
     * @return Player variables
     */
    public PlayerVariable[] getPlayers() {
        return this.players;
    }

    /**
     * Gets the names of all the players in this group
     * 
     * @return Player variable names, all-lowercase
     */
    public String[] getPlayerNames() {
        String[] rval = new String[this.players.length];
        for (int i = 0; i < rval.length; i++) {
            rval[i] = this.players[i].getPlayer();
        }
        return rval;
    }

    @Override
    public void set(String value) {
        if (this.players.length == 0) {
            return;
        }

        VariableChangeEvent event = new VariableChangeEvent(this.variable, value, this.players, VariableChangeType.PLAYER);
        Bukkit.getServer().getPluginManager().callEvent(event);
        if (!event.isCancelled()) {
            for (PlayerVariable pVar : this.players) {
                pVar.set(value);
            }
        }
    }

    @Override
    public Variable getVariable() {
        return this.variable;
    }

    @Override
    @Deprecated
    public void updateAll() {}

    @Override
    public void clear() {
        this.ticker.reset(Variable.createDefaultValue(this.variable.getName()));
    }

    @Override
    public Ticker getTicker() {
        return this.ticker;
    }
}
