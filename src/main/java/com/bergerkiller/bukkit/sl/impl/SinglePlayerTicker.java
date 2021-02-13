package com.bergerkiller.bukkit.sl.impl;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.sl.API.TickMode;

/**
 * Manages the ticker value of a single player,
 * with only one text value being managed.
 */
public class SinglePlayerTicker extends TickerBaseImpl {
    private final VariableValueMap.Entry entry;

    public SinglePlayerTicker(VariableValueMap.Entry entry) {
        this.entry = entry;
    }

    // clone
    private SinglePlayerTicker(SinglePlayerTicker source) {
        this.entry = source.entry;
        this.interval = source.interval;
        this.mode = source.mode;
        for (TickerPause p : source.pauses) {
            this.pauses.add(p.clone());
        }
    }

    // cloneForPlayer
    protected SinglePlayerTicker(TickerBaseImpl source, VariableValueMap.Entry entry) {
        this.entry = entry;
        this.interval = source.interval;
        this.mode = source.mode;
        for (TickerPause p : source.pauses) {
            this.pauses.add(p.clone());
        }
    }

    @Override
    public void setMode(TickMode mode) {
        boolean wasTicking = this.isTicking();
        super.setMode(mode);
        if (this.isTicking() != wasTicking) {
            this.entry.getValueMap().notifyPlayerEntryTickedChanged(this, this.entry);
        }
    }

    @Override
    public void load(ConfigurationNode node) {
        boolean wasTicking = this.isTicking();
        super.load(node);
        if (this.isTicking() != wasTicking) {
            this.entry.getValueMap().notifyPlayerEntryTickedChanged(this, this.entry);
        }
    }

    @Override
    public void reset(String value) {
        entry.text.resetTicker();
        entry.setValue(value);
    }

    @Override
    public String blink() {
        try {
            return entry.text.blink();
        } finally {
            entry.computePlayerText(false);
        }
    }

    @Override
    public String left() {
        try {
            return entry.text.left();
        } finally {
            entry.computePlayerText(false);
        }
    }

    @Override
    public String right() {
        try {
            return entry.text.right();
        } finally {
            entry.computePlayerText(false);
        }
    }

    @Override
    public String current() {
        return entry.text.get();
    }

    @Override
    public String current(String playerName) {
        return entry.text.get();
    }

    @Override
    public SinglePlayerTicker clone() {
        return new SinglePlayerTicker(this);
    }
}
