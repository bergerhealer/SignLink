package com.bergerkiller.bukkit.sl.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.collections.ImplicitlySharedList;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.sl.LinkedSign;
import com.bergerkiller.bukkit.sl.VirtualSign;
import com.bergerkiller.bukkit.sl.API.GroupVariable;
import com.bergerkiller.bukkit.sl.API.PlayerVariable;
import com.bergerkiller.bukkit.sl.API.Ticker;
import com.bergerkiller.bukkit.sl.API.Variable;
import com.bergerkiller.bukkit.sl.API.VariableChangeEvent;
import com.bergerkiller.bukkit.sl.API.VariableChangeType;

/**
 * Further implements Variable with some internally needed
 * data structures
 */
public class VariableImpl extends Variable {
    private final VariableMap map;
    private final VariableValueMap values;

    VariableImpl(VariableMap map, String name) {
        super(name);
        this.map = map;
        this.values = new VariableValueMap(this);
    }

    /**
     * Gets the map that stores all variables in which
     * this variable is stored
     *
     * @return variable map
     */
    public VariableMap getVariableMap() {
        return this.map;
    }

    /**
     * Gets the map that stores all possible values set
     * for this variable. This can be the default values,
     * or values only visible to certain players.
     *
     * @return variable value map
     */
    public VariableValueMap getValueMap() {
        return this.values;
    }

    @Override
    public void clear() {
        this.values.reset();
    }

    @Override
    public Ticker getTicker() {
        return this.values.setSharedTicker();
    }

    @Override
    public Ticker getDefaultTicker() {
        return this.values.getDefault().ticker;
    }

    @Override
    public String getDefault() {
        return this.values.getDefault().getValue();
    }

    @Override
    public String get(String playername) {
        return this.values.getValue(playername.toLowerCase());
    }

    @Override
    public void set(String value) {
        if (value == null) {
            value = createDefaultValue(this.getName());
        }

        // Is a change required?
        if (this.values.getDefault().getValue().equals(value) && this.values.isAllDefaultValue()) {
            return;
        }

        // Fire the event
        VariableChangeEvent event = new VariableChangeEvent(this, value, null, VariableChangeType.GLOBAL);
        if (CommonUtil.callEvent(event).isCancelled()) {
            return;
        }

        // Update the text
        this.values.setAll(value);
    }

    @Override
    public void setDefault(String value) {
        if (value == null) {
            value = createDefaultValue(this.getName());
        }

        // Is a change required?
        if (this.values.getDefault().getValue().equals(value)) {
            return;
        }

        // Fire the event
        VariableChangeEvent event = new VariableChangeEvent(this, value, null, VariableChangeType.DEFAULT);
        Bukkit.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }

        // Update the text
        this.values.setDefault(value);
    }

    @Override
    public Collection<PlayerVariable> forAll() {
        return this.values.getPlayersWithData();
    }

    @Override
    public PlayerVariable forPlayer(Player player) {
        return this.forPlayer(player.getName());
    }

    @Override
    public PlayerVariable forPlayer(String playername) {
        return this.values.getPlayerEntry(playername.toLowerCase()).toPlayerVariable();
    }

    @Override
    public GroupVariable forGroup(Player... players) {
        String[] playernames = new String[players.length];
        for (int i = 0; i < players.length; i++) {
            playernames[i] = players[i].getName();
        }
        return this.forGroup(playernames);
    }

    @Override
    public GroupVariable forGroup(String... playernames) {
        PlayerVariable[] vars = new PlayerVariable[playernames.length];
        for (int i = 0; i < vars.length; i++) {
            vars[i] = forPlayer(playernames[i]);
        }
        return new GroupVariable(vars, this);
    }

    @Override
    public void updateSign(LinkedSign sign) {
        this.values.updateSigns(Collections.singleton(sign));
    }

    /**
     * Gets the internal boundTo list of linked signs. It is up
     * to the caller to safely clone the list if mutation during
     * iteration is expected.
     *
     * @return bound to list (unsafe)
     */
    public ImplicitlySharedList<LinkedSign> getBoundTo() {
        return this.boundTo;
    }

    /**
     * Updates a single sign
     * 
     * @param signBlock where this variable is displayed
     */
    public void update(Block signBlock) {
        if (signBlock != null) {
            for (LinkedSign sign : getSigns()) {
                if (BlockUtil.equals(signBlock, sign.getStartBlock())) {
                    updateSign(sign);
                }
            }
        }
    }

    @Override
    @Deprecated
    public void updateAll() {
        for (LinkedSign sign : getSigns()) {
            updateSign(sign);
        }
    }

    /**
     * Updates the sign block order of all signs that display this Variable
     */
    public void updateSignOrder() {
        for (LinkedSign sign : getSigns()) {
            sign.updateSignOrder();
        }
    }

    /**
     * Updates the sign block order of all signs near a block that displays this Variable
     * 
     * @param near block
     */
    public void updateSignOrder(Block near) {
        for (LinkedSign sign : this.boundTo) {
            if (sign.location.getLoadedWorld() != near.getWorld()) {
                continue;
            }
            ArrayList<VirtualSign> signs = sign.getSigns();
            if (!LogicUtil.nullOrEmpty(signs)) {
                for (VirtualSign vsign : signs) {
                    if (Math.abs(vsign.getX() - near.getX()) >= 2) continue;
                    if (Math.abs(vsign.getY() - near.getY()) >= 2) continue;
                    if (Math.abs(vsign.getZ() - near.getZ()) >= 2) continue;
                    sign.updateSignOrder();
                    this.updateSign(sign);
                    break;
                }
            }
        }
    }

    /**
     * Updates the sign block order of all signs on a world that display this Variable
     * 
     * @param world
     */
    public void updateSignOrder(World world) {
        for (LinkedSign sign : getSigns()) {
            if (sign.location.getLoadedWorld() == world) {
                sign.updateSignOrder();
            }
        }
    }

    public boolean find(List<LinkedSign> signs, List<VariableImpl> variables, Block at) {
        boolean found = false;
        for (LinkedSign sign : boundTo) {
            if (sign.location.getX() == at.getX() && sign.location.getY() == at.getY() && sign.location.getZ() == at.getZ()) {
                if (sign.location.getLoadedWorld() == at.getWorld()) {
                    found = true;
                    if (signs != null) signs.add(sign);
                    if (variables != null) variables.add(this);
                }
            }
        }
        return found;
    }

    public boolean find(List<LinkedSign> signs, List<VariableImpl> variables, Location at) {
        return find(signs, variables, at.getBlock());
    }
}
