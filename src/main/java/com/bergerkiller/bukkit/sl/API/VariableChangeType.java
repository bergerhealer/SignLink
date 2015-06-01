package com.bergerkiller.bukkit.sl.API;

/**
 * The way a variable is changed
 */
public enum VariableChangeType {
	/**
	 * Default value got changed (player-specific values not affected)
	 */
	DEFAULT,
	/**
	 * Value got changed for all players
	 */
	GLOBAL,
	/**
	 * Value got changed for a specific player
	 */
	PLAYER;
}