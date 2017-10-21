package com.bergerkiller.bukkit.sl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.common.BlockLocation;
import com.bergerkiller.bukkit.common.ToggledState;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;

/**
 * Links multiple (Virtual) Signs together to create a single, long, horizontal line of text which can be altered
 */
public class LinkedSign {
    public BlockLocation location;
    public final int line;
    private final ToggledState updateSignOrder = new ToggledState();
    public final SignDirection direction;
    private String oldtext;
    private final ArrayList<VirtualSign> displaySigns = new ArrayList<VirtualSign>();
    private final LinkedText linkedText = new LinkedText(); // Handles text formatting
    private static HashSet<Block> loopCheck = new HashSet<Block>(); // Used to prevent server freeze when finding signs

    public LinkedSign(Block from, int line) {
        this(new BlockLocation(from), line, findDirection(from, line));
    }

    public LinkedSign(String worldname, int x, int y, int z, int lineAt, SignDirection direction) {
        this(new BlockLocation(worldname, x, y, z), lineAt, direction);
    }

    public LinkedSign(BlockLocation location, int lineAt, SignDirection direction) {
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
     * @param forplayers which players need to be updated, null for all players
     */
    public void setText(String value, boolean wrapAround, String... forplayers) {
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
        linkedText.apply(forplayers);
    }

    /**
     * Gets the starting Block, the first sign block this Linked Sign shows text on
     * 
     * @return Linked sign starting block
     */
    public Block getStartBlock() {
        return this.location.isLoaded() ? this.location.getBlock() : null;
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

    private Block nextSign(Block from) {
        // Check if there is a sign to the left/right of the current one (direction)
        BlockFace from_facing = BlockUtil.getFacing(from);
        BlockFace direction = FaceUtil.rotate(from_facing, 2);
        if (this.direction == SignDirection.RIGHT) {
            direction = direction.getOppositeFace();
        }
        Block next = from.getRelative(direction);
        if (MaterialUtil.ISSIGN.get(next) && BlockUtil.getFacing(next) == from_facing &&  loopCheck.add(next)) {
            return next;
        }

        // Check if there is another wall sign attached to the same block as the current one
        if (from.getType() == Material.WALL_SIGN) {
            BlockFace attachedSide = BlockUtil.getAttachedFace(from);
            Block attachedBlock = from.getRelative(attachedSide);
            BlockFace cornerDir;
            if (this.direction == SignDirection.RIGHT) {
                cornerDir = FaceUtil.rotate(attachedSide, 2);
            } else {
                cornerDir = FaceUtil.rotate(attachedSide, -2);
            }
            next = attachedBlock.getRelative(cornerDir);
            if (next.getType() == Material.WALL_SIGN && BlockUtil.getAttachedFace(next) == cornerDir.getOppositeFace() && loopCheck.add(next)) {
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
                int index1 = realline.indexOf('%');
                int index2 = realline.lastIndexOf('%');
                if (index1 != -1) {
                    // End delimiter - check whether the sign is 'valid'
                    // Only if a single % is on the sign, can it be used as a last sign
                    if (index1 == 0) {
                        char rightOf = (realline.length() == 1) ? ' ' : realline.charAt(index1 + 1);
                        if (rightOf != ' ' && rightOf != '%') {
                            // No space right of the % or %%, stop!
                            break;
                        }
                    } else if (index2 == realline.length() - 1) {
                        char leftOf = (realline.length() == 1) ? ' ' : realline.charAt(index2 - 1);
                        if (leftOf != ' ' && leftOf != '%') {
                            // No space left of the % or %%, stop!
                            break;
                        }
                    } else {
                        //centered - surrounded by spaces?
                        if (realline.charAt(index1 - 1) != ' ') break;
                        if (realline.charAt(index2 + 1) != ' ') break;
                    }
                    this.displaySigns.add(sign);
                    signsRemoved.remove(sign);
                    break;
                }
                this.displaySigns.add(sign);
                signsRemoved.remove(sign);
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
