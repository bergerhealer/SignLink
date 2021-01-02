package com.bergerkiller.bukkit.sl;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Specifies what players to include or what players to
 * exclude when updating a text value. Names used with this
 * class <b>must</b> be all-lowercased.
 */
public class VariableTextPlayerFilter {
    private static final VariableTextPlayerFilter ALL = new VariableTextPlayerFilter(true, Collections.emptySet());
    private static final VariableTextPlayerFilter NONE = new VariableTextPlayerFilter(false, Collections.emptySet());
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
     * Gets whether this filter specifies no players are
     * included.
     *
     * @return True if no player names are included
     */
    public boolean isNone() {
        return !this.exclude && this.names.isEmpty();
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
     * Filter that specifies no players are included at all
     *
     * @return VariableTextPlayerFilter
     */
    public static VariableTextPlayerFilter none() {
        return NONE;
    }

    /**
     * Filter that specifies only the given player names are included
     *
     * @param includedPlayerNames Set of names to include
     * @return VariableTextPlayerFilter
     */
    public static VariableTextPlayerFilter only(Set<String> includedPlayerNames) {
        if (includedPlayerNames.isEmpty()) {
            return NONE;
        } else {
            return new VariableTextPlayerFilter(false, includedPlayerNames);
        }
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

    /**
     * Combines two filters together to form a single filter rule that satisfies both
     *
     * @param a Filter A
     * @param b Filter B
     * @return Filter that satisfies both
     */
    public static VariableTextPlayerFilter combine(VariableTextPlayerFilter a, VariableTextPlayerFilter b) {
        if (a.isExcluding()) {
            return combineExcludingWith(a, b);
        } else if (b.isExcluding()) {
            return combineExcludingWith(b, a);
        } else {
            // Both including filters, create an intersection
            HashSet<String> combined = new HashSet<String>(a.names);
            combined.retainAll(b.names);
            return only(combined);
        }
    }

    private static VariableTextPlayerFilter combineExcludingWith(VariableTextPlayerFilter excl, VariableTextPlayerFilter b) {
        if (b.isExcluding()) {
            // One excluding filter with names of excl and b combined
            if (excl.names.isEmpty()) {
                return b;
            } else if (b.names.isEmpty()) {
                return excl;
            } else {
                HashSet<String> combined = new HashSet<String>(excl.names.size() + b.names.size());
                combined.addAll(excl.names);
                combined.addAll(b.names);
                return new VariableTextPlayerFilter(true, combined);
            }
        } else {
            // One including filter with names of b not in excl
            HashSet<String> combined = new HashSet<String>(b.names);
            combined.removeAll(excl.names);
            return only(combined);
        }
    }
}
