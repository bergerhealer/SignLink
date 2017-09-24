package com.bergerkiller.bukkit.sl.API;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Event fired whenever a Variable (or for a player/group of players) changes value
 */
public class VariableChangeEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    private boolean cancelled = false;
    private String newvalue;
    private Variable variable;
    private PlayerVariable[] players;
    private VariableChangeType type;

    public VariableChangeEvent(Variable variable, String newvalue, PlayerVariable[] players, VariableChangeType type) {
        this.newvalue = newvalue;
        this.variable = variable;
        this.players = players;
        this.type = type;
    }

    public String getNewValue() {
        return this.newvalue;
    }
    public void setNewValue(String value) {
        this.newvalue = value;
    }
    public Variable getVariable() {
        return this.variable;
    }
    public PlayerVariable[] getPlayers() {
        return this.players;
    }
    public VariableChangeType getChangeType() {
        return this.type;
    }

    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    @Override
    public void setCancelled(boolean arg0) {
        this.cancelled = arg0;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
