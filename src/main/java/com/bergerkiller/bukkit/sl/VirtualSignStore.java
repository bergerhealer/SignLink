package com.bergerkiller.bukkit.sl;

import java.util.HashSet;
import java.util.Iterator;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.BlockLocation;
import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.collections.BlockMap;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.sl.API.Variable;
import com.bergerkiller.bukkit.sl.API.Variables;

/**
 * Maps all Virtual Signs against the block location of the respective sign.
 * This allows storing, obtaining and global management of Virtual Signs.
 */
public class VirtualSignStore {
    private static BlockMap<VirtualSign> virtualSigns;
    private static HashSet<BlockLocation> changedSignBlocks = new HashSet<BlockLocation>();

    public static void deinit() {
        virtualSigns.clear();
        virtualSigns = null;
    }

    public static void init() {
        virtualSigns = new BlockMap<VirtualSign>();
    }

    public static synchronized VirtualSign add(Block block, String[] lines) {
        if (virtualSigns == null) {
            return null;
        }
        BlockLocation loc = new BlockLocation(block);
        VirtualSign vsign = new VirtualSign(loc, lines);
        virtualSigns.put(loc, vsign);
        return vsign;
    }

    public static synchronized VirtualSign add(Sign sign) {
        if (virtualSigns == null) {
            return null;
        }
        BlockLocation loc = new BlockLocation(sign.getBlock());
        VirtualSign vsign = new VirtualSign(loc, sign);
        virtualSigns.put(loc, vsign);
        return vsign;
    }

    public static VirtualSign add(Block signBlock) {
        return add(signBlock, (String[]) null);
    }

    public static synchronized VirtualSign get(Location at) {
        return get(at.getBlock());
    }

    public static synchronized VirtualSign get(World world, IntVector3 position) {
        return virtualSigns == null ? null : virtualSigns.get(world, position);
    }
    
    public static synchronized VirtualSign get(Block b) {
        return virtualSigns == null ? null : virtualSigns.get(b);
    }

    public static synchronized VirtualSign getOrCreate(Location at) {
        return getOrCreate(at.getBlock());
    }

    /**
     * Gets or creates a Virtual Sign for a Sign Block specified.
     * If the block specified is not a sign at all, null is returned.
     * 
     * @param b block of the Sign
     * @return the Virtual Sign at this Block
     */
    public static synchronized VirtualSign getOrCreate(Block b) {
        if (virtualSigns == null) {
            return null;
        }
        if (MaterialUtil.ISSIGN.get(b)) {
            VirtualSign sign = virtualSigns.get(b);
            if (sign == null || !sign.validate()) {
                sign = add(b);
            }
            return sign;
        } else {
            virtualSigns.remove(b);
            return null;
        }
    }

    public static synchronized VirtualSign[] getAll() {
        if (virtualSigns == null) {
            return null;
        }
        return virtualSigns.values().toArray(new VirtualSign[0]);
    }

    public static synchronized boolean exists(Location at) {
        return exists(at.getBlock());
    }

    public static synchronized boolean exists(Block at) {
        return virtualSigns != null && virtualSigns.containsKey(at);
    }

    /**
     * Removes a Virtual Sign from the storage.
     * 
     * @param signBlock to remove
     * @return True if a Virtual Sign was removed, False if not
     */
    public static synchronized boolean remove(Block signBlock) {
        if (virtualSigns == null || virtualSigns.remove(signBlock) == null) {
            return false;
        }
        for (Variable var : Variables.getAll()) {
            var.removeLocation(signBlock);
        }
        return true;
    }

    public static synchronized void removeAll(World world) {
        Iterator<VirtualSign> iter = virtualSigns.values().iterator();
        while (iter.hasNext()) {
            if (iter.next().getWorld() == world) {
                iter.remove();
            }
        }
    }

    public static void updateAll() {
        // Does some housekeeping (the next tick) to clean up issues and update sign order
        // This makes multi-sign variable displays work
        if (!changedSignBlocks.isEmpty()) {
            BlockLocation[] blocks = changedSignBlocks.toArray(new BlockLocation[changedSignBlocks.size()]);
            changedSignBlocks.clear();

            for (BlockLocation block : blocks) {
                Block signBlock = block.getBlock();
                if (signBlock != null) {
                    if (!VirtualSign.exists(signBlock)) {
                        VirtualSign.add(signBlock);
                    }
                    Variables.updateSignOrder(signBlock);
                }
            }
        }

        // Update all the signs
        for (VirtualSign sign : getAll()) {
            sign.update();
        }
    }

    public static void forcedUpdate(final Player forplayer, long delay) {
        if (forplayer == null) {
            return;
        }
        new Task(SignLink.plugin) {
            public void run() {
                forcedUpdate(forplayer);
            }
        }.start(delay);
    }

    public static synchronized void forcedUpdate(Player forplayer) {
        if (forplayer == null || forplayer.getWorld() == null) {
            return;
        }
        final int SIGN_RADIUS = 25;
        BlockLocation playerpos = new BlockLocation(forplayer.getLocation());
        for (VirtualSign sign : virtualSigns.values()) {
            BlockLocation pos = sign.getPosition();
            if (pos.world.equals(playerpos.world)) {
                int dx = playerpos.x - pos.x;
                int dy = playerpos.y - pos.y;
                int dz = playerpos.z - pos.z;
                if (((dx * dx) + (dy * dy) + (dz * dz)) < (SIGN_RADIUS * SIGN_RADIUS)) {
                    sign.sendCurrentLines(forplayer);
                }
            }
        }
    }

    public static synchronized void clearPlayer(String playerName) {
        for (VirtualSign sign : virtualSigns.values()) {
            sign.resetLines(playerName);
        }
    }

    public static synchronized void invalidateAll(Player player) {
        for (VirtualSign vs : virtualSigns.values()) {
            if (vs.isInRange(player)) {
                vs.invalidate(player);
            }
        }
    }

    /**
     * Schedules a refresh of the sign order and variable display for a sign block
     * 
     * @param signBlock to update
     */
    public static synchronized void updateSign(Block signBlock) {
        changedSignBlocks.add(new BlockLocation(signBlock));
    }
}
