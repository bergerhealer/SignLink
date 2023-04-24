package com.bergerkiller.bukkit.sl.impl;

import java.util.ArrayList;
import java.util.List;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.sl.API.TickMode;
import com.bergerkiller.bukkit.sl.API.Ticker;

/**
 * Base implementation common to tickers both for single players
 * and multiple players alike.
 */
public abstract class TickerBaseImpl extends Ticker {
    protected long interval = 1;
    protected TickMode mode = TickMode.NONE;
    protected final ArrayList<TickerPause> pauses = new ArrayList<TickerPause>();

    protected TickerBaseImpl() {
    }

    // clone constructor
    protected TickerBaseImpl(TickerBaseImpl source) {
        this.interval = source.interval;
        this.mode = source.mode;
        this.pauses.clear();
        for (TickerPause p : source.pauses) {
            this.pauses.add(p.clone());
        }
    }

    /**
     * Updates pausing the ticking of text. Returns false if the value can
     * be updated the current tick, or true if it is currently paused.
     *
     * @param text Ticker text
     * @return True if updating is currently paused
     */
    protected boolean updatePaused(TickerText text) {
        // Check no pauses configured at all
        if (pauses.isEmpty()) {
            text.pauseindex = -1;
            return false;
        }

        // If only pauses with duration=0 or delay=0 are added, it could crash the server
        // We check the number of loops around the pauses we've done, and if exceeded,
        // stops updating to prevent this.
        int numLoops = 0;
        while (true) {
            TickerPause p;
            if (text.pauseindex == -1 || text.pauseindex >= pauses.size()) {
                // If number of loop exceeds the limit, abort
                if (++numLoops == 2) {
                    text.pauseindex = -1;
                    return false;
                }

                // Initialize for pause[0]
                p = pauses.get(0);
                text.pauseindex = 0;
                text.pausedelay = 0;
                text.pauseduration = 0;
            } else {
                // Already in a pause operation
                p = pauses.get(text.pauseindex);
            }

            // While in the delay before the pause, it is not paused
            if (text.pausedelay < p.delay) {
                text.pausedelay++;
                return false;
            }

            // We have paused for the full duration, no more pausing, go to next one
            if (text.pauseduration >= p.duration) {
                text.pauseindex++;
                text.pausedelay = 0;
                text.pauseduration = 0;
                continue;
            }

            // Currently paused, track tick timer
            text.pauseduration++;
            return true;
        }
    }

    boolean updateText(TickerText text) {
        // Check ticker is used at all
        if (this.mode == TickMode.NONE) {
            String orig = text.get();
            return !orig.equals(text.none());
        }

        // Check tick update interval has elapsed
        if (++text.counter < this.interval) {
            return false;
        }

        // Reset counter
        text.counter = 0;

        // If currently paused, do not update
        if (this.updatePaused(text)) {
            return false;
        }

        // Update text based on ticker configuration
        switch (this.mode) {
        case LEFT:
            text.left(); break;
        case RIGHT:
            text.right(); break;
        case BLINK:
            text.blink(); break;
        default:
            break;
        }

        return true;
    }

    @Override
    public void setInterval(long interval) {
        this.interval = interval;
    }

    @Override
    public long getInterval() {
        return this.interval;
    }

    @Override
    public void setMode(TickMode mode) {
        this.mode = mode;
    }

    @Override
    public TickMode getMode() {
        return this.mode;
    }

    @Override
    public boolean isTicking() {
        return mode != TickMode.NONE;
    }

    @Override
    public boolean hasWrapAround() {
        return mode == TickMode.LEFT || mode == TickMode.RIGHT;
    }

    @Override
    public void addPause(int delay, int duration) {
        TickerPause p = new TickerPause();
        p.delay = delay;
        p.duration = duration;
        this.pauses.add(p);
    }

    @Override
    public void clearPauses() {
        this.pauses.clear();
    }

    @Override
    public void load(ConfigurationNode node) {
        this.mode = node.get("ticker", TickMode.NONE);
        this.interval = node.get("tickerInterval", 1L);
        if (node.contains("pauseDelays") && node.contains("pauseDurations")) {
            List<Integer> delays = node.getList("pauseDelays", Integer.class);
            List<Integer> durations = node.getList("pauseDurations", Integer.class);
            if (delays.size() == durations.size()) {
                for (int i = 0; i < delays.size(); i++) {
                    int delay = delays.get(i);
                    int duration = durations.get(i);
                    this.addPause(delay, duration);
                }
            }
        }
    }

    @Override
    public void save(ConfigurationNode node) {
        if (this.mode == TickMode.NONE) {
            node.remove("ticker");
            node.remove("tickerInterval");
        } else {
            node.set("ticker", this.mode);
            node.set("tickerInterval", this.interval <= 1 ? null : this.interval);
        }

        List<Integer> delays = null;
        List<Integer> durations = null;
        if (!this.pauses.isEmpty()) {
            delays = new ArrayList<Integer>();
            durations = new ArrayList<Integer>();
            for (TickerPause p : this.pauses) {
                delays.add(p.delay);
                durations.add(p.duration);
            }
        }
        node.set("pauseDelays", delays);
        node.set("pauseDurations", durations);
    }

    /**
     * Represents a pause between updating text
     */
    public static final class TickerPause implements Cloneable {
        public int delay;
        public int duration;

        @Override
        public TickerPause clone() {
            TickerPause p = new TickerPause();
            p.delay = this.delay;
            p.duration = this.duration;
            return p;
        }
    }
}
