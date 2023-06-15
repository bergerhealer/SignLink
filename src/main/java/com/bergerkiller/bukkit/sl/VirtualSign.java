package com.bergerkiller.bukkit.sl;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.function.Predicate;

import com.bergerkiller.bukkit.common.internal.CommonCapabilities;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.generated.org.bukkit.block.SignHandle;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.BlockLocation;
import com.bergerkiller.bukkit.common.block.SignChangeTracker;
import com.bergerkiller.bukkit.common.block.SignSide;
import com.bergerkiller.bukkit.common.block.SignSideMap;
import com.bergerkiller.bukkit.common.offline.OfflineBlock;
import com.bergerkiller.bukkit.common.protocol.CommonPacket;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.utils.PlayerUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.sl.API.Variables;

/**
 * Stores additional information about sign text, and keeps track of sign text, for each player individually.
 * It takes care of per-player text and text updating in general.
 */
public class VirtualSign extends VirtualSignStore {
    private final BlockLocation blockLocation;
    private final OfflineBlock location;
    private SignChangeTracker sign;
    private final SignSideMap<String[]> oldLines = new SignSideMap<>();
    private final HashMap<String, VirtualLines> playerlines = new HashMap<String, VirtualLines>();
    private final VirtualLines defaultlines;
    private final HashSet<VirtualLines> outofrange = new HashSet<VirtualLines>();
    private int signcheckcounter;
    private boolean hasBeenVerified;
    private boolean hasVariablesOnSign;
    private boolean _isMidLinkSign;
    private static int signcheckcounterinitial = 0;
    private static final int SIGN_CHECK_INTERVAL = 100;
    private static final int SIGN_CHECK_INTERVAL_NOVAR = 400;

    protected VirtualSign(Block signLocation, String[] frontLines, String[] backLines) {
        if (frontLines == null) {
            throw new IllegalArgumentException("Input front lines are null");
        }
        if (frontLines.length < VirtualLines.LINE_COUNT) {
            throw new IllegalArgumentException("Input front line count invalid: " + frontLines.length);
        }
        if (backLines == null) {
            throw new IllegalArgumentException("Input back lines are null");
        }
        if (backLines.length < VirtualLines.LINE_COUNT) {
            throw new IllegalArgumentException("Input back line count invalid: " + backLines.length);
        }
        this.location = OfflineBlock.of(signLocation);
        this.blockLocation = new BlockLocation(signLocation);
        this.sign = null;
        this.oldLines.setFront(frontLines.clone());
        this.oldLines.setBack((CommonCapabilities.HAS_SIGN_BACK_TEXT ? backLines.clone() : VirtualLines.DEFAULT_EMPTY_SIGN_LINES));
        this.defaultlines = new VirtualLines(this.oldLines.front(), this.oldLines.back());
        this._isMidLinkSign = false;
        this.initCheckCounter();
        this.scheduleVerify();
    }

    protected VirtualSign(Sign sign) {
        if (CommonCapabilities.HAS_SIGN_BACK_TEXT) {
            SignHandle handle = SignHandle.createHandle(sign);
            this.oldLines.setFront(handle.getFrontLines().clone());
            this.oldLines.setBack(handle.getBackLines().clone());
        } else {
            String[] frontLines = sign.getLines();
            if (frontLines == null) {
                frontLines = VirtualLines.DEFAULT_EMPTY_SIGN_LINES;
            }
            this.oldLines.setFront(frontLines.clone());
            this.oldLines.setBack(VirtualLines.DEFAULT_EMPTY_SIGN_LINES);
        }

        this.sign = SignChangeTracker.track(sign);
        this.location = OfflineBlock.of(this.sign.getBlock());
        this.blockLocation = new BlockLocation(this.sign.getBlock());
        this.defaultlines = new VirtualLines(this.oldLines.front(), this.oldLines.back());
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

    public synchronized void resetLines() {
        this.playerlines.clear();
    }

    public void resetLines(Player player) {
        resetLines(player.getName().toLowerCase());
    }

    public synchronized void resetLines(String playerName) {
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
    public synchronized VirtualLines getLines(String playerName) {
        if (playerName == null) {
            return getLines();
        }
        VirtualLines lines = playerlines.get(playerName);
        if (lines == null) {
            lines = new VirtualLines(defaultlines.get(SignSide.FRONT), defaultlines.get(SignSide.BACK));
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

    @Deprecated
    public void setDefaultLine(int index, String value) {
        setDefaultLine(SignSide.FRONT, index, value);
    }

    @Deprecated
    public void setLine(int index, String value, VariableTextPlayerFilter forPlayerFilter) {
        setLine(SignSide.FRONT, index, value, forPlayerFilter);
    }

    @Deprecated
    public void restoreRealLine(int line) {
        restoreRealLine(SignSide.FRONT, line);
    }

    @Deprecated
    public String getLine(int index) {
        return getLine(SignSide.FRONT, index);
    }

    @Deprecated
    public String getLine(int index, String player) {
        return getLine(SignSide.FRONT, index, player);
    }

    public void setDefaultLine(SignSide side, int index, String value) {
        getLines().set(side, index, value);
    }

    /**
     * Sets a single line of text on this virtual sign
     *
     * @param side Side of the sign
     * @param index Line index (0 - 3)
     * @param value Value to store on the line
     * @param forPlayerFilter Filters what players should be updated
     */
    public synchronized void setLine(SignSide side, int index, String value, VariableTextPlayerFilter forPlayerFilter) {
//        System.out.println("Set line "+index+" to "+value+" for "+(players==null?null:players.length));
        if (forPlayerFilter.isAll()) {
            //Set all lines to this value at this index
            for (VirtualLines lines : playerlines.values()) {
                lines.set(side, index, value);
            }
            getLines().set(side, index, value);
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
                    lines.set(side, index, value);
                    this.sendLines(lines, SignLink.plugin.getPlayerByLowercase(name));
                }
            }

            // Update default value
            getLines().set(side, index, value);
        } else {
            // Only for some player names, do not update default
            for (String player : forPlayerFilter.getPlayerNames()) {
                VirtualLines lines = getLines(player);
                lines.set(side, index, value);
                this.sendLines(lines, SignLink.plugin.getPlayerByLowercase(player));
            }
        }
    }

    public void restoreRealLine(SignSide side, int line) {
        setLine(side, line, getRealLine(side, line), VariableTextPlayerFilter.all());
    }

    /**
     * Gets a single line of this virtual sign as it is displayed
     * to players that do not have a personalized text displayed
     *
     * @param side Side of the sign
     * @param index Line index (0-3)
     * @return Line displayed by default
     */
    public String getLine(SignSide side, int index) {
        return this.defaultlines.get(side, index);
    }

    /**
     * Gets a single line of this virtual sign as it is displayed
     * to a player
     *
     * @param side Side of the sign
     * @param index Line index (0-3)
     * @param player Name of the player to get it for, all-lowercase
     * @return Line for this player
     */
    public synchronized String getLine(SignSide side, int index, String player) {
        return this.playerlines.getOrDefault(player, this.defaultlines).get(side, index);
    }

    @Deprecated
    public String[] getRealLines() {
        return getRealLines(SignSide.FRONT);
    }

    @Deprecated
    public String getRealLine(int index) {
        return getRealLine(SignSide.FRONT, index);
    }

    @Deprecated
    public void setRealLine(int index, String line) {
        setRealLine(SignSide.FRONT, index, line);
    }

    public String[] getRealLines(SignSide side) {
        if (this.sign == null) {
            return this.oldLines.side(side);
        } else {
            return this.sign.getLines(side);
        }
    }

    public String getRealLine(SignSide side, int index) {
        if (this.sign == null) {
            return this.oldLines.side(side)[index];
        } else {
            return this.sign.getLine(side, index);
        }
    }

    public void setRealLine(SignSide side, int index, String line) {
        this.sign.setLine(side, index, line);
    }

    /**
     * Gets the world this sign is in. If the world is unloaded,
     * this function returns null.
     * 
     * @return world the sign is in
     */
    public World getWorld() {
        return this.location.getLoadedWorld();
    }

    public int getX() {
        return this.location.getX();
    }

    public int getY() {
        return this.location.getY();
    }

    public int getZ() {
        return this.location.getZ();
    }

    public int getChunkX() {
        return this.getX() >> 4;
    }

    public int getChunkZ() {
        return this.getZ() >> 4;
    }

    /**
     * Gets the block this sign is at. If the world the sign is in is unloaded,
     * this function will return null.
     * 
     * @return block of the sign
     */
    public Block getBlock() {
        return this.location.getLoadedBlock();
    }

    /**
     * Gets the location of the sign. If the world the sign is in is unloaded,
     * this function will return null.
     * 
     * @return location the sign is in
     */
    public Location getLocation() {
        return this.blockLocation.getLocation();
    }

    /**
     * Gets the position of the sign, consisting of block coordinates and world name.
     * 
     * @return block location
     */
    public BlockLocation getPosition() {
        return this.blockLocation;
    }

    /**
     * Gets the Offline Block position of the sign.
     *
     * @return offline block position
     */
    public OfflineBlock getOfflineBlock() {
        return this.location;
    }

    /**
     * Verifies the already tracked sign still exists. Handles changes that are detected.
     *
     * @return True if the sign is valid, False if not
     */
    public boolean verifySign() {
        if (!this.sign.update()) {
            return true; // No changes
        }

        // Check whether sign got removed
        if (this.sign.isRemoved()) {
            this.sign = null;
            this.remove();
            return false;
        }

        // Detect misc. changes in orientation/lines of text
        detectLineChanges();
        return true;
    }

    /**
     * Loads the sign from the world if it was not loaded yet
     * 
     * @return True if the sign is valid, False if not
     */
    public boolean loadSign() {
        if (this.sign == null) {
            // Check loaded. If not loaded, remove ourselves.
            if (!this.blockLocation.isLoaded()) {
                this.remove();
                return false;
            }

            final Block block = this.getBlock();

            // Verify that the sign still exists
            if (!MaterialUtil.ISSIGN.get(block)) {
                this.remove();
                return false;
            }
            Sign signAtBlock = BlockUtil.getSign(block);
            if (signAtBlock == null || signAtBlock.getLines() == null) {
                this.remove();
                return false;
            }

            // Check for changes to the text on the sign (external cause)
            this.sign = SignChangeTracker.track(signAtBlock);
            detectLineChanges();
        }
        return true;
    }

    private void detectLineChanges() {
        if (detectLineChangesOfSide(SignSide.FRONT) || detectLineChangesOfSide(SignSide.BACK)) {
            boolean hadVariables = this.hasVariablesOnSign;
            this.hasVariablesOnSign = this.hasVariablesRefresh();

            // It's possible variables were added and/or removed. Make sure to update the sign.
            if (hadVariables || this.hasVariablesOnSign || this._isMidLinkSign) {
                resendLines(LogicUtil.alwaysTruePredicate());
            }
        }
    }

    private boolean detectLineChangesOfSide(SignSide side) {
        if (!side.isSupported()) {
            return false;
        }

        boolean changed = false;
        String[] oldLines = this.oldLines.side(side);
        for (int i = 0; i < 4; i++) {
            String currentLine = sign.getLine(side, i);
            if (!oldLines[i].equals(currentLine)) {
                Block signblock = this.getBlock();
                String varname = Variables.parseVariableName(oldLines[i]);
                if (varname != null) {
                    Variables.get(varname).removeLocation(signblock, side, i);
                }
                oldLines[i] = currentLine;
                this.setLine(side, i, oldLines[i], VariableTextPlayerFilter.all());
                varname = Variables.parseVariableName(oldLines[i]);
                if (varname != null) {
                    Variables.get(varname).addLocation(signblock, side, i);
                }
                changed = true;
            }
        }
        return changed;
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

        this.sign = SignChangeTracker.track(sign);
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
        for (String line : this.getRealLines(SignSide.FRONT)) {
            if (line.indexOf('%') != -1) {
                return true;
            }
        }
        if (SignSide.BACK.isSupported()) {
            for (String line : this.getRealLines(SignSide.BACK)) {
                if (line.indexOf('%') != -1) {
                    return true;
                }
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
        if (this.sign == null) {
            // Load sign for the first time
            if (this.loadSign()) {
                this.hasBeenVerified = true;
            } else {
                return; // Removed
            }

        } else if (!this.hasBeenVerified) {
            // Must verify right now!
            this.hasBeenVerified = true;
            if (!this.verifySign()) {
                return;
            }

        } else {
            // Refresh the Sign state now and then (just in case the tile got swapped or destroyed)
            // Only do this for signs that have variables on them. Otherwise check less often.
            // When disabled, don't do a refresh of the sign at all when no variables are displayed.
            this.signcheckcounter++;
            if (this.signcheckcounter == SIGN_CHECK_INTERVAL && this.hasVariablesRefresh()) {
                this.signcheckcounter = 0;
                if (!this.verifySign()) {
                    return;
                }
            } else if (this.signcheckcounter >= SIGN_CHECK_INTERVAL_NOVAR) {
                this.signcheckcounter = 0;
                if (SignLink.plugin.discoverSignChanges()) {
                    if (!this.verifySign()) {
                        return;
                    }
                }
            }
        }

        // Send updated sign text to nearby players
        //FIX: Only do this for signs with variables on them!
        if (this.hasVariablesOnSign || this._isMidLinkSign) {
            resendLines(VirtualLines::hasChanged);
        }
    }

    private void resendLines(Predicate<VirtualLines> changedCheck) {
        for (Player player : WorldUtil.getPlayers(getWorld())) {
            VirtualLines lines = getLines(player);
            if (isInRange(player)) {
                if (outofrange.remove(lines) || changedCheck.test(lines)) {
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

    public void sendCurrentLines(Player player) {
        sendLines(getLines(player), player);
    }

    public void sendLines(VirtualLines lines, Player player) {
        if (SignLink.updateSigns && player != null && sign != null) {
            CommonPacket updatePacket = BlockUtil.getUpdatePacket(sign.getSign());
            if (updatePacket != null) {
                SLBlockStateChangeListener.applyDirect(this, player, updatePacket);
                PacketUtil.sendPacket(player, updatePacket, false); // Send and skip listeners
            }
        }
    }

    public void sendRealLines(Player player) {
        if (player != null && sign != null) {
            CommonPacket updatePacket = BlockUtil.getUpdatePacket(sign.getSign());
            if (updatePacket != null) {
                PacketUtil.sendPacket(player, updatePacket, false); // Send and skip listeners
            }
        }
    }
}