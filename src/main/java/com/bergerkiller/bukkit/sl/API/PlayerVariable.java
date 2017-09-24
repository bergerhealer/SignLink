package com.bergerkiller.bukkit.sl.API;

import org.bukkit.Bukkit;

/**
 * Represents a Variable value for a single player
 */
public class PlayerVariable implements VariableValue {
    protected String value;
    protected Ticker ticker;
    private Variable variable;
    private String playername;

    public PlayerVariable(String playername, Variable variable) {
        this(playername, variable, variable.getDefault());
    }
    public PlayerVariable(String playername, Variable variable, String value) {
        this.playername = playername;
        this.value = value;
        this.variable = variable;
        this.ticker = this.variable.getDefaultTicker();
    }

    @Override
    public String get() {
        return this.value;
    }

    /**
     * Gets the name of the player this player variable belongs to
     * 
     * @return player name
     */
    public String getPlayer() {
        return this.playername;
    }

    @Override
    public void clear() {
        this.ticker = this.variable.getDefaultTicker();
        this.set("%" + this.variable.getName() + "%");
    }

    @Override
    public void set(String value) {
        //is a change required?
        if (this.value.equals(value)) {
            return;
        }
        VariableChangeEvent event = new VariableChangeEvent(this.variable, value, new PlayerVariable[] {this}, VariableChangeType.PLAYER);
        Bukkit.getServer().getPluginManager().callEvent(event);
        if (!event.isCancelled()) {
            this.value = value;
            getTicker().reset(value);
            this.variable.setSigns(value, this.ticker.hasWrapAround(), new String[] {this.playername});
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

    /**
     * Checks whether the ticker of this Player Variable is shared in a group with other players
     * 
     * @return True if it is shared, False if not
     */
    public boolean isTickerShared() {
        return this.ticker.isShared();
    }

    @Override
    public Ticker getTicker() {
        if (this.isTickerShared()) {
            this.ticker = this.ticker.clone();
            this.ticker.players = new String[] {this.playername};
        }
        return this.ticker;
    }

    /**
     * Sets the ticker used for this player variable
     * 
     * @param ticker to set to
     */
    void setTicker(Ticker ticker) {
        this.ticker = ticker;
    }
}
