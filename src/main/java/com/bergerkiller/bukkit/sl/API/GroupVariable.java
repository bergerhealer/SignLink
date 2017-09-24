package com.bergerkiller.bukkit.sl.API;

import org.bukkit.Bukkit;

/**
 * Groups multiple player-specific variables together as one
 */
public class GroupVariable implements VariableValue {
    private PlayerVariable[] players;
    private Variable variable;
    private String value;
    private Ticker ticker;

    public GroupVariable(PlayerVariable[] players, Variable variable) {
        this(players, variable, variable.getDefault());
    }

    public GroupVariable(PlayerVariable[] players, Variable variable, String value) {
        this.players = players;
        this.variable = variable;
        this.value = value;
    }

    @Override
    public String get() {
        return this.value;
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
     * @return Player variable names
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
        VariableChangeEvent event = new VariableChangeEvent(this.variable, value, this.players, VariableChangeType.PLAYER);
        Bukkit.getServer().getPluginManager().callEvent(event);
        if (!event.isCancelled()) {
            this.value = value;
            if (this.ticker != null) {
                this.ticker.reset(value);
            }
            for (PlayerVariable pvar : this.players) {
                if (this.ticker == null) {
                    pvar.ticker.reset(value);
                }
                pvar.value = value;
            }
            this.variable.setSigns(value, this.ticker != null && this.ticker.hasWrapAround(), getPlayerNames());
        }
    }

    @Override
    public Variable getVariable() {
        return this.variable;
    }

    @Override
    public void updateAll() {
        this.variable.updateAll();
    }

    @Override
    public void clear() {
        this.set("%" + this.variable.getName() + "%");
        this.ticker = null;
    }

    @Override
    public Ticker getTicker() {
        if (this.ticker == null) {
            this.ticker = new Ticker(this.value, this.getPlayerNames());
            for (PlayerVariable pvar : this.players) {
                pvar.ticker = this.ticker;
            }
        }
        return this.ticker;
    }
}
