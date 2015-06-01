package com.bergerkiller.bukkit.sl.API;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import com.bergerkiller.bukkit.sl.LinkedSign;

/**
 * Event fired whenever a sign is added to a Variable
 */
public class SignAddEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
	private boolean cancelled = false;
	private Variable var;
	private LinkedSign sign;

	public SignAddEvent(Variable to, LinkedSign sign) {
		this.var = to;
		this.sign = sign;
	}

	public Variable getVariable() {
		return this.var;
	}

    public LinkedSign getSign() {
    	return this.sign;
    }

	@Override
	public boolean isCancelled() {
		return cancelled;
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
