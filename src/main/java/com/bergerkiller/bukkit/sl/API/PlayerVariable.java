package com.bergerkiller.bukkit.sl.API;

/**
 * Represents a Variable value for a single player
 */
public abstract class PlayerVariable implements VariableValue {
    /**
     * Gets the name of the player this player variable belongs to.
     * This name is always all-lowercase.
     * 
     * @return player name
     */
    public abstract String getPlayer();

    /**
     * Checks whether the ticker of this Player Variable is shared in a group with other players
     * 
     * @return True if it is shared, False if not
     */
    public abstract boolean isTickerShared();

    // Inherited, ensures compatibility with older plugins using SignLink

    @Override
    public abstract String get();

    @Override
    public abstract void clear();

    @Override
    public abstract Variable getVariable();

    @Override
    public abstract void set(String value);

    @Override
    public abstract Ticker getTicker();

    @Override
    @Deprecated
    public void updateAll() {}
}
