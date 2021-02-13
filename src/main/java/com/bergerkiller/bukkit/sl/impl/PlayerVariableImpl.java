package com.bergerkiller.bukkit.sl.impl;

import org.bukkit.Bukkit;

import com.bergerkiller.bukkit.sl.API.PlayerVariable;
import com.bergerkiller.bukkit.sl.API.Ticker;
import com.bergerkiller.bukkit.sl.API.Variable;
import com.bergerkiller.bukkit.sl.API.VariableChangeEvent;
import com.bergerkiller.bukkit.sl.API.VariableChangeType;
import com.bergerkiller.bukkit.sl.impl.format.FormattedVariableValue;

/**
 * Implements the player variable
 */
public class PlayerVariableImpl extends PlayerVariable {
    private final VariableValueMap.Entry entry;

    protected PlayerVariableImpl(VariableValueMap.Entry entry) {
        this.entry = entry;
    }

    public VariableValueMap.Entry getEntry() {
        return this.entry;
    }

    @Override
    public String get() {
        return entry.getValue();
    }

    @Override
    public String getPlayer() {
        return entry.playerName;
    }

    @Override
    public void clear() {
        String variableName = this.entry.getVariableName();
        FormattedVariableValue defaultValue = FormattedVariableValue.createDefaultValue(variableName);

        // Do event, to check it isn't cancelled
        VariableChangeEvent event = new VariableChangeEvent(this.getVariable(),
                defaultValue.getFormat(),
                new PlayerVariable[] {this},
                VariableChangeType.PLAYER);
        Bukkit.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }

        // Reset ticker to the defaults
        boolean wasTicking = this.entry.ticker.isTicking();
        this.entry.ticker = new SinglePlayerTicker(this.entry);
        if (wasTicking) {
            this.entry.getValueMap().notifyPlayerEntryTickedChanged(
                    (SinglePlayerTicker) this.entry.ticker, this.entry);
        }

        // Update value and text instantly
        this.entry.setValue(defaultValue);
    }

    @Override
    public void set(String value) {
        // Is a change required?
        if (this.entry.getValue().equals(value)) {
            return;
        }

        VariableChangeEvent event = new VariableChangeEvent(this.getVariable(),
                value,
                new PlayerVariable[] {this},
                VariableChangeType.PLAYER);
        Bukkit.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }

        // Update value data, which also updates what variables are declared
        this.entry.setValue(value);
    }

    @Override
    public Variable getVariable() {
        return this.entry.getValueMap().getVariable();
    }

    @Override
    public boolean isTickerShared() {
        return this.entry.ticker instanceof DefaultTicker;
    }

    @Override
    public Ticker getTicker() {
        return this.entry.getPlayerTicker();
    }
}
