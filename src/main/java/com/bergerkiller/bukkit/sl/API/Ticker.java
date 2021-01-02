package com.bergerkiller.bukkit.sl.API;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;

/**
 * Ticks text on a per-tick basis to allow textual animations
 */
public abstract class Ticker implements Cloneable {

    public abstract void setInterval(long interval);

    public abstract long getInterval();

    public abstract void setMode(TickMode mode);

    public abstract TickMode getMode();

    /**
     * Gets whether this Ticker is actively ticking (not NONE)
     * 
     * @return True if the mode is not NONE
     */
    public abstract boolean isTicking();

    /**
     * Gets whether this ticker (in the current mode) wraps text around on signs
     * 
     * @return True if text should be wrapped around
     */
    public abstract boolean hasWrapAround();

    /**
     * Adds a pause to this ticker
     * 
     * @param delay in ticks before the pause occurs
     * @param duration in ticks of the pause
     */
    public abstract void addPause(int delay, int duration);

    /**
     * Clears all the ticker pauses
     */
    public abstract void clearPauses();

    /**
     * Resets the settings of this ticker
     * 
     * @param value to set the text to
     */
    public abstract void reset(String value);
  
    /**
     * Gets the next value when blinking on and off
     * 
     * @return Next value
     */
    public abstract String blink();

    /**
     * Gets the next value when ticking the text to the left
     * 
     * @return Next value
     */
    public abstract String left();

    /**
     * Gets the next value when ticking the text to the right
     * 
     * @return Next value
     */
    public abstract String right();

    /**
     * Gets the current value of this ticker
     * 
     * @return Current ticker value
     */
    public abstract String current();

    /**
     * Gets the current value of this ticker, for a specific
     * player. If this ticker manages the display for more
     * than one player, this gets it for specifically
     * that player.
     *
     * @param playerName Name of the player, case-insensitive
     * @return currently displayed text value for this player
     */
    public abstract String current(String playerName);

    /**
     * Loads ticker information
     * 
     * @param node to load from
     */
    public abstract void load(ConfigurationNode node);

    /**
     * Saves this ticker
     * 
     * @param node to save in
     */
    public abstract void save(ConfigurationNode node);
 
    @Override
    public abstract Ticker clone();
}
