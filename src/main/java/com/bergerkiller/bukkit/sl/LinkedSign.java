package com.bergerkiller.bukkit.sl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import com.bergerkiller.bukkit.common.MaterialBooleanProperty;
import com.bergerkiller.bukkit.common.MaterialTypeProperty;
import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.common.ToggledState;
import com.bergerkiller.bukkit.common.offline.OfflineBlock;
import com.bergerkiller.bukkit.common.offline.OfflineWorld;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.ChunkUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;

/**
 * Links multiple (Virtual) Signs together to create a single, long, horizontal line of text which can be altered
 */
public class LinkedSign {
    //TODO: Make this cleaner
    private static final MaterialBooleanProperty IS_WALL_SIGN = new MaterialTypeProperty(MaterialUtil.getMaterial("LEGACY_WALL_SIGN"));

    public OfflineBlock location;
    public final int line;
    private final ToggledState updateSignOrder = new ToggledState();
    public final SignDirection direction;
    private String oldtext;
    private final ArrayList<VirtualSign> displaySigns = new ArrayList<VirtualSign>();
    private final LinkedText linkedText = new LinkedText(); // Handles text formatting
    private static HashSet<Block> loopCheck = new HashSet<Block>(); // Used to prevent server freeze when finding signs

    public LinkedSign(Block from, int line) {
        this(OfflineBlock.of(from), line, findDirection(from, line));
    }

    public LinkedSign(OfflineWorld world, int x, int y, int z, int lineAt, SignDirection direction) {
        this(world.getBlockAt(x, y, z), lineAt, direction);
    }

    public LinkedSign(OfflineBlock location, int lineAt, SignDirection direction) {
        this.location = location;
        this.line = lineAt;
        this.direction = direction;
        this.linkedText.setDirection(direction);
        this.linkedText.setLine(line);
    }

    private static SignDirection findDirection(Block from, int line) {
        VirtualSign sign = VirtualSign.getOrCreate(from);
        if (sign != null) {
            String text = sign.getRealLine(line);
            int peri = text.indexOf("%");
            if (peri != -1 && text.lastIndexOf("%") == peri) {
                //get direction from text
                if (peri == 0) {
                    return SignDirection.RIGHT;
                } else if (peri == text.length() - 1) {
                    return SignDirection.LEFT;
                } else if (text.substring(peri).contains(" ")) {
                    return SignDirection.LEFT;
                } else {
                    return SignDirection.RIGHT;
                }
            }
        }
        
        return SignDirection.NONE;
    }

    /**
     * Gets the full line of text this LinkedSign currently displays
     * 
     * @return Line of text
     */
    public String getText() {
        return this.oldtext;
    }

    /**
     * Refreshes the text on this linked sign, updating all the signs.
     * 
     * @param value of the variable to display
     * @param wrapAround whether the value wraps around endlessly (ticker)
     * @param forplayers which players need to be updated, null or empty for all players
     * @deprecated Use the VariableTextPlayerFilter-based setText instead
     */
    @Deprecated
    public void setText(String value, boolean wrapAround, String... forplayers) {
        if (forplayers == null || forplayers.length == 0) {
            setText(value, wrapAround, VariableTextPlayerFilter.all());
        } else {
            setText(value, wrapAround, VariableTextPlayerFilter.only(new HashSet<String>(Arrays.asList(forplayers))));
        }
    }

    /**
     * Refreshes the text on this linked sign, updating all the signs.
     * 
     * @param value of the variable to display
     * @param wrapAround whether the value wraps around endlessly (ticker)
     * @param forPlayerFilter Filter that specifies what players to refresh with this value,
     *                        and which to ignore
     */
    public void setText(String value, boolean wrapAround, VariableTextPlayerFilter forPlayerFilter) {
        oldtext = value;
        if (!SignLink.updateSigns) {
            return; 
        }
        final ArrayList<VirtualSign> signs = getSigns();
        if (signs.isEmpty()) {
            return;
        }
        for (VirtualSign sign : signs) {
            if (!sign.loadSign()) {
                return;
            }
        }

        linkedText.setSigns(signs);
        linkedText.setWrapAround(wrapAround);
        linkedText.generate(value);
        linkedText.apply(forPlayerFilter);
    }

    /**
     * Gets the starting Block, the first sign block this Linked Sign shows text on
     * 
     * @return Linked sign starting block
     */
    public Block getStartBlock() {
        return this.location.getLoadedBlock();
    }

    /**
     * Tells this Linked Sign to update the order of the signs, to update how text is divided
     */
    public void updateSignOrder() {
        this.updateSignOrder.set();
    }

    /**
     * Gets the signs which this Linked Sign displays text on, validates the signs
     * 
     * @return The virtual signs on which this Linked Sign shows text
     */
    public ArrayList<VirtualSign> getSigns() {
        return this.getSigns(true);
    }

    private boolean validateSigns() {
        if (!this.displaySigns.isEmpty()) {
            for (VirtualSign sign : this.displaySigns) {
                if (!sign.loadSign()) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private BlockData getBlockDataIfLoaded(Block block, int curr_chunkX, int curr_chunkZ) {
        int next_chunkX = MathUtil.toChunk(block.getX());
        int next_chunkZ = MathUtil.toChunk(block.getZ());
        if (next_chunkX != curr_chunkX || next_chunkZ != curr_chunkZ) {
            Chunk nextChunk = WorldUtil.getChunk(block.getWorld(), next_chunkX, next_chunkZ);
            if (nextChunk == null) {
                // Not loaded (yet)
                return BlockData.AIR;
            } else {
                // Loaded, query using Chunk
                return ChunkUtil.getBlockData(nextChunk, block.getX(), block.getY(), block.getZ());
            }
        } else {
            // Simple
            return WorldUtil.getBlockData(block);
        }
    }

    private Block nextSign(Block from) {
        // Check if there is a sign to the left/right of the current one (direction)
        BlockFace from_facing = BlockUtil.getFacing(from);
        BlockFace direction = FaceUtil.rotate(from_facing, 2);
        if (this.direction == SignDirection.RIGHT) {
            direction = direction.getOppositeFace();
        }

        // From block chunk coordinates
        int from_chunkX = MathUtil.toChunk(from.getX());
        int from_chunkZ = MathUtil.toChunk(from.getZ());

        // Make sure the chunk at this next block is actually loaded, when next chunk is different
        Block next = from.getRelative(direction);
        BlockData nextBlockData = getBlockDataIfLoaded(next, from_chunkX, from_chunkZ);

        if (MaterialUtil.ISSIGN.get(nextBlockData) && nextBlockData.getFacingDirection() == from_facing && loopCheck.add(next)) {
            return next;
        }

        // Check if there is another wall sign attached to the same block as the current one
        // MC 1.13 note: both legacy and new are called WALL_SIGN, so in this instance, it will work!
        BlockData fromBlockData = WorldUtil.getBlockData(from);
        if (IS_WALL_SIGN.get(fromBlockData)) {
            BlockFace attachedSide = BlockUtil.getAttachedFace(from);
            Block attachedBlock = from.getRelative(attachedSide);
            BlockFace cornerDir;
            if (this.direction == SignDirection.RIGHT) {
                cornerDir = FaceUtil.rotate(attachedSide, 2);
            } else {
                cornerDir = FaceUtil.rotate(attachedSide, -2);
            }
            next = attachedBlock.getRelative(cornerDir);
            nextBlockData = getBlockDataIfLoaded(next, from_chunkX, from_chunkZ);

            if (IS_WALL_SIGN.get(nextBlockData) && nextBlockData.getAttachedFace() == cornerDir.getOppositeFace() && loopCheck.add(next)) {
                return next;
            }
        }
        return null;
    }

    /**
     * Gets the signs which this Linked Sign displays text on
     * 
     * @param validate the signs, True to validate that all signs exist, False to ignore that check
     * @return The virtual signs on which this Linked Sign shows text
     */
    public ArrayList<VirtualSign> getSigns(boolean validate) {
        if (!validate) {
            return this.displaySigns;
        }
        Block start = getStartBlock();
        //Unloaded chunk?
        if (start == null) {
            for (VirtualSign sign : this.displaySigns) {
                sign.restoreRealLine(this.line);
                sign.setMidLinkSign(false);
            }
            this.displaySigns.clear();
            return this.displaySigns;
        }

        if (validateSigns() && !updateSignOrder.clear()) {
            return displaySigns;
        }

        //Regenerate old signs and return
        ArrayList<VirtualSign> signsRemoved = new ArrayList<VirtualSign>(this.displaySigns);
        this.displaySigns.clear();
        if (MaterialUtil.ISSIGN.get(start)) {
            VirtualSign startSign = VirtualSign.getOrCreate(start);
            this.displaySigns.add(startSign);
            signsRemoved.remove(startSign);
            if (this.direction == SignDirection.NONE) {
                return displaySigns;
            }

            // Look (recursively) for new signs
            loopCheck.clear();
            loopCheck.add(start);
            while ((start = nextSign(start)) != null) {
                VirtualSign sign = VirtualSign.getOrCreate(start);
                String realline = sign.getRealLine(this.line);
                boolean isEndSign = false;
                if (realline.equals("%")) {
                    // End delimiter (sign is included)
                    // Works for both left and right mode
                    isEndSign = true;
                } else {
                    int index1 = realline.indexOf('%');
                    int index2 = realline.lastIndexOf('%');
                    if (index1 != -1) {
                        // End delimiter - check whether the sign is 'valid'
                        // Only if a single % is on the sign, can it be used as a last sign
                        if (index1 == 0) {
                            char rightOf = (realline.length() == 1) ? ' ' : realline.charAt(index1 + 1);
                            if (rightOf == ' ') {
                                isEndSign = true;
                            } else if (rightOf != '%') {
                                // No space right of the % or %%, stop!
                                break;
                            }
                        } else if (index2 == realline.length() - 1) {
                            char leftOf = (realline.length() == 1) ? ' ' : realline.charAt(index2 - 1);
                            if (leftOf == ' ') {
                                isEndSign = true;
                            } else if (leftOf != '%') {
                                // No space left of the % or %%, stop!
                                break;
                            }
                        } else {
                            //centered - surrounded by spaces?
                            if (realline.charAt(index1 - 1) != ' ') break;
                            if (realline.charAt(index2 + 1) != ' ') break;
                        }
                    }
                }

                sign.setMidLinkSign(true);
                this.displaySigns.add(sign);
                signsRemoved.remove(sign);
                if (isEndSign) {
                    break;
                }
            }
            if (this.direction == SignDirection.LEFT) {
                Collections.reverse(this.displaySigns);
            }
        }
        for (VirtualSign sign : signsRemoved) {
            sign.restoreRealLine(this.line);
        }
        return this.displaySigns;
    }
}
