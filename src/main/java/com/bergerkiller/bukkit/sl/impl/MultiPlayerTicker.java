package com.bergerkiller.bukkit.sl.impl;

import java.util.Collection;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.sl.API.TickMode;
import com.bergerkiller.bukkit.sl.API.Ticker;
import com.bergerkiller.bukkit.sl.impl.VariableValueMap.Entry;

/**
 * Manages multiple player tickers at the same time using a single ticker instance.
 * This ticker isn't actually stored anywhere.
 */
public class MultiPlayerTicker extends Ticker {
    private final Collection<VariableValueMap.Entry> entries;
    private final VariableValueMap.Entry defaultEntry;

    /**
     * Initializes a new MultiPlayerTicker
     *
     * @param entries The entries that changes are pushed to
     * @param variable The variable whose default to pick if the entries are empty
     */
    public MultiPlayerTicker(Collection<VariableValueMap.Entry> entries, VariableImpl variable) {
        this.entries = entries;
        if (entries.isEmpty()) {
            this.defaultEntry = variable.getValueMap().getDefault();
        } else {
            this.defaultEntry = entries.iterator().next();
        }
    }

    /**
     * Initializes a new MultiPlayerTicker
     *
     * @param entries The entries that changes are pushed to
     * @param defaultEntry Default entry to read properties from, when getters are called
     */
    public MultiPlayerTicker(Collection<VariableValueMap.Entry> entries, VariableValueMap.Entry defaultEntry) {
        this.entries = entries;
        this.defaultEntry = defaultEntry;
    }

    @Override
    public void setInterval(long interval) {
        for (Entry e : entries) {
            e.getPlayerTicker().setInterval(interval);
        }
    }

    @Override
    public long getInterval() {
        return defaultEntry.ticker.getInterval();
    }

    @Override
    public void setMode(TickMode mode) {
        for (Entry e : entries) {
            e.getPlayerTicker().setMode(mode);
        }
    }

    @Override
    public TickMode getMode() {
        return defaultEntry.ticker.getMode();
    }

    @Override
    public boolean isTicking() {
        return defaultEntry.ticker.isTicking();
    }

    @Override
    public boolean hasWrapAround() {
        return defaultEntry.ticker.hasWrapAround();
    }

    @Override
    public void addPause(int delay, int duration) {
        
    }

    @Override
    public void clearPauses() {
        for (Entry e : entries) {
            e.getPlayerTicker().clearPauses();
        }
    }

    @Override
    public void reset(String value) {
        for (Entry e : entries) {
            e.getPlayerTicker().reset(value);
        }
    }

    @Override
    public String blink() {
        for (Entry e : this.entries) {
            e.getPlayerTicker().blink();
        }
        return this.defaultEntry.ticker.current();
    }

    @Override
    public String left() {
        for (Entry e : this.entries) {
            e.getPlayerTicker().left();
        }
        return this.defaultEntry.ticker.current();
    }

    @Override
    public String right() {
        for (Entry e : this.entries) {
            e.getPlayerTicker().right();
        }
        return this.defaultEntry.ticker.current();
    }

    @Override
    public String current() {
        return defaultEntry.ticker.current();
    }

    @Override
    public String current(String playerName) {
        String playerNameLower = playerName.toLowerCase();
        for (Entry e : entries) {
            if (e.playerName != null && e.playerName.equals(playerNameLower)) {
                return e.ticker.current();
            }
        }

        return defaultEntry.ticker.current(playerName);
    }

    @Override
    public void load(ConfigurationNode node) {
        for (Entry e : entries) {
            e.getPlayerTicker().load(node);
        }
    }

    @Override
    public void save(ConfigurationNode node) {
        defaultEntry.ticker.save(node);
    }

    @Override
    public Ticker clone() {
        return new MultiPlayerTicker(this.entries, this.defaultEntry);
    }
}
