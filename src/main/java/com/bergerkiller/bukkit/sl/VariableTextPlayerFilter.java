package com.bergerkiller.bukkit.sl;

import java.util.Collections;
import java.util.Set;

/**
 * Specifies what players to include or what players to
 * exclude when updating a text value. Names used with this
 * class <b>must</b> be all-lowercased.
 */
public class VariableTextPlayerFilter {
    private static final VariableTextPlayerFilter ALL = new VariableTextPlayerFilter(true, Collections.emptySet());
    private boolean exclude;
    private Set<String> names;

    private VariableTextPlayerFilter(boolean exclude, Set<String> names) {
        this.exclude = exclude;
        this.names = names;
    }

    /**
     * Gets whether this filter specifies all players are
     * included.
     *
     * @return True if all player names are included
     */
    public boolean isAll() {
        return this.exclude && this.names.isEmpty();
    }

    /**
     * Gets whether this filter excludes some player names, or instead
     * includes only a given list
     *
     * @return True if this filter is excluding a list of player names.
     *         False if this filter is including.
     */
    public boolean isExcluding() {
        return this.exclude;
    }

    /**
     * Gets the set of player names to include or exclude,
     * depending on {@link #isExcluding()}
     *
     * @return set of player names to include/exclude
     */
    public Set<String> getPlayerNames() {
        return this.names;
    }

    /**
     * Gets whether a given player name is contained in {@link #getPlayerNames()}
     *
     * @param name Player name to check
     * @return True if it is in the set
     */
    public boolean containsPlayerName(String name) {
        return this.names.contains(name);
    }

    /**
     * Filter that specifies all possible players are included
     *
     * @return VariableTextPlayerFilter
     */
    public static VariableTextPlayerFilter all() {
        return ALL;
    }

    /**
     * Filter that specifies only the given player names are included
     *
     * @param includedPlayerNames Set of names to include
     * @return VariableTextPlayerFilter
     */
    public static VariableTextPlayerFilter only(Set<String> includedPlayerNames) {
        return new VariableTextPlayerFilter(false, includedPlayerNames);
    }

    /**
     * Filter that specifies only the given single player name is included
     *
     * @param playerName Name of the player to include
     * @return VariableTextPlayerFilter
     */
    public static VariableTextPlayerFilter only(String playerName) {
        return new VariableTextPlayerFilter(false, Collections.singleton(playerName));
    }

    /**
     * Filter that specifies all player, except a few, should be included
     *
     * @param excludedPlayerNames Set of names to exclude
     * @return VariableTextPlayerFilter
     */
    public static VariableTextPlayerFilter allExcept(Set<String> excludedPlayerNames) {
        if (excludedPlayerNames.isEmpty()) {
            return ALL;
        } else {
            return new VariableTextPlayerFilter(true, excludedPlayerNames);
        }
    }
}
