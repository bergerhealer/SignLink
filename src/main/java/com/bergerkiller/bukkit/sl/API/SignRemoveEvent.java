package com.bergerkiller.bukkit.sl.API;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import com.bergerkiller.bukkit.sl.LinkedSign;

/**
 * Event fired whenever a sign is removed from a Variable
 */
public class SignRemoveEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
	private Variable var;
	private LinkedSign sign;

	public SignRemoveEvent(Variable from, LinkedSign sign) {
		this.var = from;
		this.sign = sign;
	}

	public Variable getVariable() {
		return this.var;
	}

    public LinkedSign getSign() {
    	return this.sign;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
