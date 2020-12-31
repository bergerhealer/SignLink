package com.bergerkiller.bukkit.sl;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.BlockLocation;
import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;
import com.bergerkiller.bukkit.common.protocol.CommonPacket;
import com.bergerkiller.bukkit.common.protocol.PacketType;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.utils.PlayerUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.wrappers.ChatText;
import com.bergerkiller.bukkit.sl.API.Variables;

/**
 * Stores additional information about sign text, and keeps track of sign text, for each player individually.
 * It takes care of per-player text and text updating in general.
 */
public class VirtualSign extends VirtualSignStore {
    private final BlockLocation location;
    private Sign sign;
    private final String[] oldlines;
    private final HashMap<String, VirtualLines> playerlines = new HashMap<String, VirtualLines>();
    private final VirtualLines defaultlines;
    private HashSet<VirtualLines> outofrange = new HashSet<VirtualLines>();
    private int signcheckcounter;
    private boolean hasBeenVerified;
    private boolean hasVariablesOnSign;
    private boolean _isMidLinkSign;
    private static int signcheckcounterinitial = 0;
    private static final int SIGN_CHECK_INTERVAL = 100;
    private static final int SIGN_CHECK_INTERVAL_NOVAR = 400;

    protected VirtualSign(Block signLocation, String[] lines) {
        if (lines == null) {
            throw new IllegalArgumentException("Input lines are null");
        }
        if (lines.length < VirtualLines.LINE_COUNT) {
            throw new IllegalArgumentException("Input line count invalid: " + lines.length);
        }
        this.location = new BlockLocation(signLocation);
        this.oldlines = LogicUtil.cloneArray(lines);
        this.defaultlines = new VirtualLines(this.oldlines);
        this._isMidLinkSign = false;
        this.initCheckCounter();
        this.scheduleVerify();
    }

    protected VirtualSign(Sign sign) {
        String[] lines = sign.getLines();
        if (lines == null) {
            lines = new String[] {"", "", "", ""};
        }

        this.sign = sign;
        this.location = new BlockLocation(sign.getBlock());
        this.oldlines = LogicUtil.cloneArray(lines);
        this.defaultlines = new VirtualLines(this.oldlines);
        this._isMidLinkSign = false;
        this.initCheckCounter();
        this.scheduleVerify();
    }

    private void initCheckCounter() {
        // By setting a check counter this way we only check a single sign every tick when possible
        // This reduces bad tick lag that can occur otherwise
        ++signcheckcounterinitial;
        this.signcheckcounter = signcheckcounterinitial;
        this.hasVariablesOnSign = this.hasVariablesRefresh();
        if (this.hasVariablesOnSign) {
            this.signcheckcounter %= SIGN_CHECK_INTERVAL;
        } else {
            this.signcheckcounter %= SIGN_CHECK_INTERVAL_NOVAR;
        }
    }

    public void remove() {
        this.sign = null;
        remove(this.location);
    }

    public void resetLines() {
        this.playerlines.clear();
    }

    public void resetLines(Player player) {
        resetLines(player.getName().toLowerCase());
    }

    public void resetLines(String playerName) {
        this.playerlines.remove(playerName);
    }

    public void invalidate(Player player) {
        getLines(player).setChanged();
    }

    public void invalidate(String player) {
        getLines(player).setChanged();
    }

    /**
     * Gets the lines of text specific for a single player
     *
     * @param playerName Name of the player, must be all-lowercase.
     *                   Specifying null will return the default lines.
     * @return lines for this player
     */
    public VirtualLines getLines(String playerName) {
        if (playerName == null) {
            return getLines();
        }
        VirtualLines lines = playerlines.get(playerName);
        if (lines == null) {
            lines = new VirtualLines(defaultlines.get());
            lines.setChanged(true);
            playerlines.put(playerName, lines);
        }
        return lines;
    }

    /**
     * Gets the lines of text specific for a single player
     *
     * @param player Player, null to get the default lines
     * @return lines for this player
     */
    public VirtualLines getLines(Player player) {
        if (player == null) {
            return getLines();
        }
        return getLines(player.getName().toLowerCase());
    }

    public VirtualLines getLines() {
        return this.defaultlines;
    }

    public void setDefaultLine(int index, String value) {
        getLines().set(index, value);
    }

    /**
     * Sets a single line of text on this virtual sign
     *
     * @param index Line index (0 - 3)
     * @param value Value to store on the line
     * @param forPlayerFilter Filters what players should be updated
     */
    public void setLine(int index, String value, VariableTextPlayerFilter forPlayerFilter) {
//        System.out.println("Set line "+index+" to "+value+" for "+(players==null?null:players.length));
        if (forPlayerFilter.isAll()) {
            //Set all lines to this value at this index
            for (VirtualLines lines : playerlines.values()) {
                lines.set(index, value);
            }
            getLines().set(index, value);
        } else if (forPlayerFilter.isExcluding()) {
            // All except some player names

            // Make sure to first create additional by-player instances for players
            // excluded, that are missing in the mapping
            for (String player : forPlayerFilter.getPlayerNames()) {
                getLines(player);
            }

            // Update all player lines that are missing from the filter
            for (Map.Entry<String, VirtualLines> entry : playerlines.entrySet()) {
                String name = entry.getKey();
                if (!forPlayerFilter.containsPlayerName(name)) {
                    VirtualLines lines = entry.getValue();
                    lines.set(index, value);
                    this.sendLines(lines, SignLink.plugin.getPlayerByLowercase(name));
                }
            }

            // Update default value
            getLines().set(index, value);
        } else {
            // Only for some player names, do not update default
            for (String player : forPlayerFilter.getPlayerNames()) {
                VirtualLines lines = getLines(player);
                lines.set(index, value);
                this.sendLines(lines, SignLink.plugin.getPlayerByLowercase(player));
            }
        }
    }

    public void restoreRealLine(int line) {
        setLine(line, getRealLine(line), VariableTextPlayerFilter.all());
    }

    /**
     * Gets a single line of this virtual sign as it is displayed
     * to players that do not have a personalized text displayed
     *
     * @param index Line index (0-3)
     * @return Line displayed by default
     */
    public String getLine(int index) {
        return this.defaultlines.get(index);
    }

    /**
     * Gets a single line of this virtual sign as it is displayed
     * to a player
     *
     * @param index Line index (0-3)
     * @param player Name of the player to get it for, all-lowercase
     * @return Line for this player
     */
    public String getLine(int index, String player) {
        return this.playerlines.getOrDefault(player, this.defaultlines).get(index);
    }

    public String[] getRealLines() {
        if (this.sign == null) {
            return this.oldlines;
        } else {
            return this.sign.getLines();
        }
    }

    public String getRealLine(int index) {
        if (this.sign == null) {
            return this.oldlines[index];
        } else {
            return this.sign.getLine(index);
        }
    }

    public void setRealLine(int index, String line) {
        this.sign.setLine(index, line);
    }

    /**
     * Gets the world this sign is in. If the world is unloaded,
     * this function returns null.
     * 
     * @return world the sign is in
     */
    public World getWorld() {
        return this.location.getWorld();
    }

    public int getX() {
        return this.location.x;
    }

    public int getY() {
        return this.location.y;
    }

    public int getZ() {
        return this.location.z;
    }

    public int getChunkX() {
        return this.getX() >> 4;
    }

    public int getChunkZ() {
        return this.getZ() >> 4;
    }

    /**
     * Gets the block this sign is at. If the world the isgn is in is unloaded,
     * this function will return null.
     * 
     * @return block of the sign
     */
    public Block getBlock() {
        return this.location.getBlock();
    }

    /**
     * Gets the location of the sign. If the world the sign is in is unloaded,
     * this function will return null.
     * 
     * @return location the sign is in
     */
    public Location getLocation() {
        return this.location.getLocation();
    }

    /**
     * Gets the position of the sign, consisting of block coordinates and world name.
     * 
     * @return block location
     */
    public BlockLocation getPosition() {
        return this.location;
    }

    /**
     * Updates the sign contents to the update packet belonging to this sign
     * 
     * @param viewer of this sign
     * @param updatePacket for this sign
     */
    public void applyToPacket(Player viewer, CommonPacket updatePacket) {
        applyToPacket(getLines(viewer), updatePacket);
    }

    /**
     * Updates the sign contents to the update packet belonging to this sign
     * 
     * @param virtual line information to apply
     * @param updatePacket for this sign
     */
    public void applyToPacket(VirtualLines lines, CommonPacket updatePacket) {
        if (updatePacket.getType() == PacketType.OUT_TILE_ENTITY_DATA) {
            // >= MC 1.10.2
            
            CommonTagCompound compound = updatePacket.read(PacketType.OUT_TILE_ENTITY_DATA.data);
            // ====================================================================

            for (int i = 0; i < VirtualLines.LINE_COUNT; i++) {
                //String old_text = compound.getValue(key, "");
                //System.out.println("Ln[" + i + "] = " + old_text);
                //System.out.println("Tn[" + i + "] = " + Conversion.chatJsonToText.convert(old_text));

                String key = "Text" + (i+1);
                String text = ChatText.fromMessage(lines.get(i)).getJson();
                compound.putValue(key, text);
            }

            // ====================================================================
            updatePacket.write(PacketType.OUT_TILE_ENTITY_DATA.data, compound);
        } else if (updatePacket.getType() == PacketType.OUT_UPDATE_SIGN) {
            // <= MC 1.8.8

            ChatText[] pkt_lines = updatePacket.read(PacketType.OUT_UPDATE_SIGN.lines);
            for (int i = 0; i < VirtualLines.LINE_COUNT; i++) {
                pkt_lines[i] = ChatText.fromMessage(lines.get(i));
            }
            updatePacket.write(PacketType.OUT_UPDATE_SIGN.lines, pkt_lines);
        }
    }

    /**
     * Loads the sign from the world if it was not loaded yet
     * 
     * @return True if the sign is valid, False if not
     */
    public boolean loadSign() {
        if (this.sign == null) {
            // Check loaded. If not loaded, remove ourselves.
            if (!this.location.isLoaded()) {
                this.remove();
                return false;
            }

            final Block block = this.getBlock();

            // Verify that the sign still exists
            if (!MaterialUtil.ISSIGN.get(block)) {
                this.remove();
                return false;
            }
            this.sign = BlockUtil.getSign(block);
            if (this.sign == null || this.sign.getLines() == null) {
                this.remove();
                return false;
            }

            // Check for changes to the text on the sign (external cause)
            boolean changed = false;
            for (int i = 0; i < 4; i++) {
                if (!this.oldlines[i].equals(this.sign.getLine(i))) {
                    Block signblock = this.getBlock();
                    String varname = Variables.parseVariableName(this.oldlines[i]);
                    if (varname != null) {
                        Variables.get(varname).removeLocation(signblock, i);
                    }
                    this.oldlines[i] = this.sign.getLine(i);
                    this.setLine(i, this.oldlines[i], VariableTextPlayerFilter.all());
                    varname = Variables.parseVariableName(this.oldlines[i]);
                    if (varname != null) {
                        Variables.get(varname).addLocation(signblock, i);
                    }
                    changed = true;
                }
            }
            if (changed) {
                this.hasVariablesOnSign = this.hasVariablesRefresh();
            }
        }
        return true;
    }

    /**
     * Performs a Sign validation check to see whether this Virtual Sign is still valid (a Sign block).
     * If the validation fails, this Sign is removed and False is returned.
     * 
     * @param sign BlockState of this sign
     * @return True if the sign is valid, False if not
     */
    public boolean loadSign(Sign sign) {
        if (sign.getLines() == null) {
            this.sign = null;
            this.remove();
            return false;
        }

        this.sign = sign;
        return true;
    }

    public boolean isInRange(Player player) {
        if (!PlayerUtil.isChunkVisible(player, this.getChunkX(), this.getChunkZ())) {
            return false;
        }
        if (player.getWorld() != this.getWorld()) {
            return false;
        }
        return EntityUtil.isNearBlock(player, this.getX(), this.getZ(), 60);
    }

    @Override
    public String toString() {
        return "VirtualSign {" + this.getX() + ", " + this.getY() + ", " + this.getZ() + ", " + this.getWorld().getName() + "}";
    }

    /**
     * Gets whether this virtual sign stores variables on it or not
     * 
     * @return True if variables are stored
     */
    public boolean hasVariables() {
        return this.hasVariablesOnSign;
    }

    private boolean hasVariablesRefresh() {
        if (this._isMidLinkSign) {
            return true;
        }
        for (String line : this.getRealLines()) {
            if (line.indexOf('%') != -1) {
                return true;
            }
        }
        return false;
    }

    /**
     * Forces sign re-verification the next tick this sign is updated
     */
    public void scheduleVerify() {
        this.hasBeenVerified = false;
    }

    /**
     * Gets whether this Virtual Sign was discovered
     * in the middle of a chain of signs displaying a variable.
     * 
     * @return midLink
     */
    public boolean isMidLinkSign() {
        return this._isMidLinkSign;
    }

    /**
     * Declares that this sign is in the middle of a chain of signs displaying a variable.
     * 
     * @param midLink
     */
    public void setMidLinkSign(boolean midLink) {
        this._isMidLinkSign = midLink;
        this.scheduleVerify();
    }

    /**
     * Updates all nearby players with the live text information
     */
    public void update() {
        // Force a verify after a new sign is created
        if (!this.hasBeenVerified) {
            this.hasBeenVerified = true;
            this.sign = null;
        }

        // Refresh the Sign state now and then (just in case the tile got swapped or destroyed)
        // Only do this for signs that have variables on them. Otherwise check less often.
        // When disabled, don't do a refresh of the sign at all when no variables are displayed.
        if (this.sign != null) {
            this.signcheckcounter++;
            if (this.signcheckcounter == SIGN_CHECK_INTERVAL && this.hasVariablesRefresh()) {
                this.signcheckcounter = 0;
                this.sign = null;
            } else if (this.signcheckcounter >= SIGN_CHECK_INTERVAL_NOVAR) {
                this.signcheckcounter = 0;
                if (SignLink.plugin.discoverSignChanges()) {
                    this.sign = null;
                }
            }
        }

        // When the sign is re-loaded, make sure to also refresh the isLoaded state just in case
        if (!this.loadSign()) {
            return;
        }

        // Send updated sign text to nearby players
        //FIX: Only do this for signs with variables on them!
        if (this.hasVariablesOnSign || this._isMidLinkSign) {
            for (Player player : WorldUtil.getPlayers(getWorld())) {
                VirtualLines lines = getLines(player);
                if (isInRange(player)) {
                    if (outofrange.remove(lines) || lines.hasChanged()) {
                        this.sendLines(lines, player);
                    }
                } else {
                    outofrange.add(lines);
                }
            }

            // All signs updated - they are no longer 'dirty'
            this.defaultlines.setChanged(false);
            for (VirtualLines lines : playerlines.values()) {
                lines.setChanged(false);
            }
        }
    }

    public void sendCurrentLines(Player player) {
        sendLines(getLines(player), player);
    }

    public void sendLines(VirtualLines lines, Player player) {
        if (SignLink.updateSigns && player != null) {
            CommonPacket updatePacket = BlockUtil.getUpdatePacket(sign);
            if (updatePacket != null) {
                applyToPacket(lines, updatePacket);

                SLListener.ignore = true;
                PacketUtil.sendPacket(player, updatePacket);
                SLListener.ignore = false;
            }
        }
    }

}