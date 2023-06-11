package com.bergerkiller.bukkit.sl.API;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.function.Predicate;

import com.bergerkiller.bukkit.common.block.SignSide;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.collections.ImplicitlySharedList;
import com.bergerkiller.bukkit.common.offline.OfflineBlock;
import com.bergerkiller.bukkit.common.offline.OfflineWorld;
import com.bergerkiller.bukkit.sl.LinkedSign;
import com.bergerkiller.bukkit.sl.SignDirection;
import com.bergerkiller.bukkit.sl.VirtualSign;

/**
 * All-encompassing class that stores the information of a single variable
 */
public abstract class Variable implements VariableValue {
    protected String name;
    protected final ImplicitlySharedList<LinkedSign> boundTo = new ImplicitlySharedList<LinkedSign>();

    protected Variable(String name) {
        this.name = name;
    }

    /**
     * Gets the name of this Variable
     * 
     * @return variable name
     */
    public String getName() {
        return this.name;
    }

    @Override
    public abstract void clear();

    @Override
    public abstract Ticker getTicker();

    /**
     * Gets the ticker used for all (new) players that have no specific ticker or value set
     * 
     * @return Default ticker
     */
    public abstract Ticker getDefaultTicker();

    /**
     * Gets the default value used for all (new) players that have no specific value set
     * 
     * @return Default value
     */
    public abstract String getDefault();

    /**
     * Gets the Variable value for the player specified
     * 
     * @param playername to get the variable value of, case-insensitive
     * @return Player-specific variable value, or the default if none is set
     */
    public abstract String get(String playername);

    @Override
    public abstract void set(String value);

    /**
     * Sets the default value for all (new) players that don't have a specific value set
     * 
     * @param value to set to
     */
    public abstract void setDefault(String value);

    /**
     * Gets all the individual player variables used by this Variable
     * 
     * @return Player variables
     */
    public abstract Collection<PlayerVariable> forAll();

    /**
     * Gets a variable specific for a single player
     * 
     * @param player
     * @return Player-specific variable
     */
    public abstract PlayerVariable forPlayer(Player player);

    /**
     * Gets a variable specific for a single player
     * 
     * @param playername Name of the player, case-insensitive
     * @return Player-specific variable
     */
    public abstract PlayerVariable forPlayer(String playername);

    /**
     * Gets a variable specific for a group of players
     * 
     * @param players Players in the group
     * @return Player group variable
     */
    public abstract GroupVariable forGroup(Player... players);

    /**
     * Gets a variable specific for a group of players
     * 
     * @param playernames Names of the players in the group, names are case-insensitive
     * @return Player group variable
     */
    public abstract GroupVariable forGroup(String... playernames);

    @Override
    @Deprecated
    public void updateAll() {}

    /**
     * Gets all the signs on which this Variable is displayed
     * 
     * @return signs
     */
    public LinkedSign[] getSigns() {
        return this.boundTo.toArray(new LinkedSign[0]);
    }

    /**
     * Gets all the signs on a block on which this Variable is displayed
     * 
     * @param onBlock which it is displayed
     * @return signs
     */
    public LinkedSign[] getSigns(Block onBlock) {
        if (onBlock == null || boundTo.isEmpty()) {
            return new LinkedSign[0];
        }
        ArrayList<LinkedSign> signs = new ArrayList<LinkedSign>(boundTo.size());
        for (LinkedSign sign : boundTo) {
            Block block = sign.getStartBlock();
            if (block != null && block.equals(onBlock)) {
                signs.add(sign);
            }
        }
        return signs.toArray(new LinkedSign[0]);
    }

    @Override
    public String toString() {
        return this.name;
    }

    public boolean addLocation(OfflineWorld world, int x, int y, int z, SignSide side, int lineAt, SignDirection direction) {
        return addLocation(new LinkedSign(world, x, y, z, side, lineAt, direction));
    }

    public boolean addLocation(Block signblock, SignSide side, int lineAt) {
        return addLocation(new LinkedSign(signblock, side, lineAt));
    }

    public boolean addLocation(LinkedSign sign) {
        //Not already added?
        for (LinkedSign ls : boundTo) {
            if (ls == sign) {
                return false;
            }
            if (ls.location.equals(sign.location)) {
                if (ls.line == sign.line && ls.getSide() == sign.getSide()) {
                    this.removeLocation(ls);
                    break;
                }
            }
        }

        SignAddEvent event = new SignAddEvent(this, sign);
        Bukkit.getServer().getPluginManager().callEvent(event);
        if (!event.isCancelled()) {
            boundTo.add(sign);
            updateSign(sign);
            return true;
        }
        return false;
    }

    /**
     * Updates the text on a sign
     *
     * @param sign
     */
    protected abstract void updateSign(LinkedSign sign);

    public boolean removeLocation(Block signblock, SignSide side, int lineAt) {
        return removeLocation(OfflineBlock.of(signblock), side, lineAt);
    }

    public boolean removeLocation(OfflineBlock signblock, final SignSide side, final int lineAt) {
        return removeLocation(signblock, sign -> sign.getSide() == side && sign.line == lineAt);
    }

    public boolean removeLocation(Block signblock) {
        return this.removeLocation(signblock, LogicUtil.alwaysTruePredicate());
    }

    public boolean removeLocation(OfflineBlock signblock) {
        return this.removeLocation(signblock, LogicUtil.alwaysTruePredicate());
    }

    public boolean removeLocation(LinkedSign sign) {
        return this.removeLocation(sign, true);
    }

    private boolean removeLocation(Block signblock, Predicate<LinkedSign> which) {
        return removeLocation(OfflineBlock.of(signblock), which);
    }

    private boolean removeLocation(OfflineBlock signblock, Predicate<LinkedSign> which) {
        boolean rem = false;
        Iterator<LinkedSign> iter = this.boundTo.iterator();
        while (iter.hasNext()) {
            LinkedSign sign = iter.next();
            if (sign.location.equals(signblock) && which.test(sign)) {
                if (removeLocation(sign, false)) {
                    iter.remove();
                    rem = true;
                }
            }
        }
        return rem;
    }

    private boolean removeLocation(LinkedSign sign, boolean removeBoundTo) {
        SignRemoveEvent event = new SignRemoveEvent(this, sign);
        Bukkit.getServer().getPluginManager().callEvent(event);
        if (!removeBoundTo || boundTo.remove(sign)) {
            ArrayList<VirtualSign> signs = sign.getSigns(false);
            if (signs != null) {
                for (VirtualSign vsign : signs) {
                    vsign.restoreRealLine(sign.getSide(), sign.line);
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Returns this same Variable instance, there is no use to call this method
     */
    @Override
    @Deprecated
    public Variable getVariable() {
        return this;
    }

    /**
     * Returns the default value, to prevent confusion, this method is deprecated
     */
    @Override
    @Deprecated
    public String get() {
        return this.getDefault();
    }

    /**
     * Creates the default variable value, used before a value is assigned
     *
     * @param name Variable name
     * @return Default variable value, which is %name%
     */
    public static String createDefaultValue(String name) {
        return "%%" + name + "%%";
    }
}
