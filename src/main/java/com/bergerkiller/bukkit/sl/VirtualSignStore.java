package com.bergerkiller.bukkit.sl;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.BlockLocation;
import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.offline.OfflineBlock;
import com.bergerkiller.bukkit.common.offline.OfflineWorld;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.sl.API.Variable;
import com.bergerkiller.bukkit.sl.API.Variables;
import com.bergerkiller.bukkit.sl.util.ConcurrentMapList;

/**
 * Maps all Virtual Signs against the block location of the respective sign.
 * This allows storing, obtaining and global management of Virtual Signs.
 */
public class VirtualSignStore {
    private static ConcurrentMapList<OfflineBlock, VirtualSign> virtualSigns;
    private static HashSet<OfflineBlock> changedSignBlocks = new HashSet<OfflineBlock>();

    public static void deinit() {
        virtualSigns.clear();
        virtualSigns = null;
    }

    public static void init() {
        virtualSigns = new ConcurrentMapList<>();
    }

    public static synchronized VirtualSign add(Block block, String[] lines) {
        if (virtualSigns == null) {
            return null;
        }
        VirtualSign vsign = new VirtualSign(block, lines);
        virtualSigns.put(OfflineBlock.of(vsign.getBlock()), vsign);
        return vsign;
    }

    public static synchronized VirtualSign add(Sign sign) {
        if (virtualSigns == null) {
            return null;
        }
        VirtualSign vsign = new VirtualSign(sign);
        virtualSigns.put(OfflineBlock.of(vsign.getBlock()), vsign);
        return vsign;
    }

    public static VirtualSign add(Block signBlock) {
        Sign sign = BlockUtil.getSign(signBlock);
        if (sign == null) {
            return null;
        } else {
            return add(sign);
        }
    }

    public static synchronized VirtualSign get(Location at) {
        return get(at.getBlock());
    }

    public static synchronized VirtualSign get(World world, IntVector3 position) {
        return virtualSigns == null ? null : virtualSigns.get(OfflineWorld.of(world).getBlockAt(position));
    }

    public static synchronized VirtualSign get(Block b) {
        return virtualSigns == null ? null : virtualSigns.get(OfflineBlock.of(b));
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
            VirtualSign sign = virtualSigns.get(OfflineBlock.of(b));
            if (sign == null || !sign.loadSign()) {
                sign = add(b);
            }
            return sign;
        } else {
            virtualSigns.remove(OfflineBlock.of(b));
            return null;
        }
    }

    /**
     * Supplies a consumer with every virtual sign on the server. Slightly more
     * efficient than {@link #getAll()} as it does not require a copy.
     *
     * @param method
     */
    public static synchronized void forEachSign(Consumer<VirtualSign> method) {
        virtualSigns.forEachValue(method);
    }

    /**
     * Creates a new array of all current virtual signs
     *
     * @return Array of all current virtual signs
     * @deprecated Is inefficient, use {@link #forEachSign(Consumer)} or
     *             {@link #getAllAsList()} instead
     */
    @Deprecated
    public static synchronized VirtualSign[] getAll() {
        return getAllAsList().toArray(new VirtualSign[0]);
    }

    /**
     * Creates a new List of all current virtual signs
     *
     * @return List of all current virtual sign
     */
    public static synchronized List<VirtualSign> getAllAsList() {
        return (virtualSigns == null) ? Collections.emptyList() : virtualSigns.toListCopy();
    }

    public static synchronized boolean exists(Location at) {
        return exists(at.getBlock());
    }

    public static synchronized boolean exists(Block at) {
        return virtualSigns != null && virtualSigns.containsKey(OfflineBlock.of(at));
    }

    /**
     * Removes a Virtual Sign from the storage.
     * 
     * @param signBlock to remove
     * @return True if a Virtual Sign was removed, False if not
     */
    public static synchronized boolean remove(Block signBlock) {
        return remove(OfflineBlock.of(signBlock));
    }

    /**
     * Removes a Virtual Sign from the storage.
     * 
     * @param signBlock to remove
     * @return True if a Virtual Sign was removed, False if not
     * @deprecated BlockLocation is inefficient, replaced with OfflineBlock/Block
     */
    @Deprecated
    public static synchronized boolean remove(BlockLocation signBlock) {
        World world = signBlock.getWorld();
        return (world == null) ? false : remove(OfflineWorld.of(world)
                .getBlockAt(signBlock.x, signBlock.y, signBlock.z));
    }

    /**
     * Removes a Virtual Sign from the storage.
     * 
     * @param signBlock OfflineBlock sign block to remove
     * @return True if a Virtual Sign was removed, False if not
     */
    public static synchronized boolean remove(OfflineBlock signBlock) {
        if (virtualSigns == null || virtualSigns.remove(signBlock) == null) {
            return false;
        }

        for (Variable var : Variables.getAll()) {
            var.removeLocation(signBlock);
        }
        return true;
    }

    public static synchronized void removeAll(World world) {
        virtualSigns.forEachValue(vsign -> {
            if (vsign.getWorld() == world) {
                virtualSigns.remove(vsign.getOfflineBlock());
            }
        });
    }

    public static void globalUpdateSignOrders() {
        // Does some housekeeping (the next tick) to clean up issues and update sign order
        // This makes multi-sign variable displays work
        if (!changedSignBlocks.isEmpty()) {
            OfflineBlock[] blocks = changedSignBlocks.toArray(new OfflineBlock[changedSignBlocks.size()]);
            changedSignBlocks.clear();

            for (OfflineBlock block : blocks) {
                Block signBlock = block.getLoadedBlock();
                if (signBlock != null) {
                    if (!VirtualSign.exists(signBlock)) {
                        VirtualSign.add(signBlock);
                    }
                    Variables.updateSignOrder(signBlock);
                }
            }
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
        final Block playerBlock = forplayer.getLocation().getBlock();
        virtualSigns.forEachValue(sign -> {
            if (sign.getWorld() == playerBlock.getWorld()) {
                int dx = playerBlock.getX() - sign.getX();
                int dy = playerBlock.getY() - sign.getY();
                int dz = playerBlock.getZ() - sign.getZ();
                if (((dx * dx) + (dy * dy) + (dz * dz)) < (SIGN_RADIUS * SIGN_RADIUS)) {
                    sign.sendCurrentLines(forplayer);
                }
            }
        });
    }

    /**
     * Removes player-specific metadata for a player name
     *
     * @param playerName Name of the player, must be all-lowercase
     */
    public static synchronized void clearPlayer(final String playerName) {
        virtualSigns.forEachValue(sign -> sign.resetLines(playerName));
    }

    public static synchronized void invalidateAll(final Player player) {
        virtualSigns.forEachValue(vs -> {
            if (vs.isInRange(player)) {
                vs.invalidate(player);
            }
        });
    }

    /**
     * Schedules a refresh of the sign order and variable display for a sign block
     * 
     * @param signBlock to update
     */
    public static synchronized void updateSign(Block signBlock, String[] lines) {
        if (!exists(signBlock)) {
            add(signBlock, lines);
        }
        changedSignBlocks.add(OfflineBlock.of(signBlock));
    }

    /**
     * Tells the store that a new sign is available, creating the virtual sign that represents it.
     * The sign will be scheduled for further refreshing in the next tick.
     * 
     * @param sign to update
     */
    public static synchronized VirtualSign createSign(Sign sign) {
        Block signBlock = sign.getBlock();
        VirtualSign vsign = get(signBlock);
        if (vsign == null) {
            vsign = add(sign);
        }
        if (vsign != null && vsign.loadSign(sign)) {
            changedSignBlocks.add(OfflineBlock.of(signBlock));
        }
        return vsign;
    }
}
