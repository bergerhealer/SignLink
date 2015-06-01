package com.bergerkiller.bukkit.sl;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.BlockLocation;
import com.bergerkiller.bukkit.common.ToggledState;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.common.utils.PlayerUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
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
	private final ToggledState loaded = new ToggledState(false);
	private int signcheckcounter = 0;

	protected VirtualSign(BlockLocation location, String[] lines) {
		this.location = location;
		this.loaded.set(this.location.isLoaded());
		if (lines == null || lines.length < VirtualLines.LINE_COUNT) {
			if (this.loaded.get()) {
				this.sign = BlockUtil.getSign(location.getBlock());
				if (this.sign == null) {
					this.loaded.clear();
				}
			}
			if (this.sign == null) {
				lines = new String[VirtualLines.LINE_COUNT];
				Arrays.fill(lines, "");
			} else {
				lines = this.sign.getLines();
			}
		}
		this.oldlines = LogicUtil.cloneArray(lines);
		this.defaultlines = new VirtualLines(lines);
	}

	public void remove() {
		remove(this.getBlock());
	}

	public void resetLines() {
		this.playerlines.clear();
	}

	public void resetLines(Player player) {
		resetLines(player.getName());
	}

	public void resetLines(String playerName) {
		playerlines.remove(playerName);
	}

	public void invalidate(Player player) {
		getLines(player).setChanged();
	}

	public void invalidate(String player) {
		getLines(player).setChanged();
	}

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

	public VirtualLines getLines(Player player) {
		if (player == null) {
			return getLines();
		}
		return getLines(player.getName());
	}

	public VirtualLines getLines() {
		return this.defaultlines;
	}

	public void setDefaultLine(int index, String value) {
		getLines().set(index, value);
	}

	public void setLine(int index, String value, String... players) {
//		System.out.println("Set line "+index+" to "+value+" for "+(players==null?null:players.length));
		if (players == null || players.length == 0) {
			//Set all lines to this value at this index
			for (VirtualLines lines : playerlines.values()) {
				lines.set(index, value);
			}
			getLines().set(index, value);
		} else {
			for (String player : players) {
				getLines(player).set(index, value);
				getLines(player).updateSign(Bukkit.getPlayer(player), location.x, location.y, location.z);
			}
		}
	}

	public String getLine(int index) {
		return getLine(index, null);
	}

	public String getLine(int index, String player) {
		return getLines(player).get(index);
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

	public Block getBlock() {
		return this.location.getBlock();
	}

	public Location getLocation() {
		return this.location.getLocation();
	}

	public boolean isLoaded() {
		return this.loaded.get();
	}

	/**
	 * Performs a Sign validation check to see whether this Virtual Sign is still valid (a Sign block).
	 * If the validation fails, this Sign is removed and False is returned.
	 * 
	 * @return True if the sign is valid, False if not
	 */
	public boolean validate() {
		if (!this.loaded.get()) {
			// Unloaded: until loaded we consider it to be valid
			return true;
		}
		final Block block = this.getBlock();
		if (!MaterialUtil.ISSIGN.get(block)) {
			this.remove();
			return false;
		}
		if (this.sign == null) {
			this.sign = BlockUtil.getSign(this.getBlock());
		}
		if (this.sign == null || this.sign.getLines() == null) {
			this.remove();
			return false;
		}
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
	 * Loads or unloads this Sign
	 * 
	 * @param loaded state to set to
	 */
	public void setLoaded(boolean loaded) {
		if (loaded) {
			if (this.loaded.set()) {
				this.sign = null;
				this.validate();
			}
		} else if (this.loaded.clear()) {
			this.sign = null;
		}
	}

	/**
	 * Updates all nearby players with the live text information
	 */
	public void update() {
		// Check whether the area this sign is at, is loaded
		this.setLoaded(this.location.isLoaded());
		if (!this.isLoaded()) {
			return;
		}

		// Refresh the Sign state now and then (just in case the tile got swapped or destroyed)
		if (signcheckcounter++ % 20 == 0) {
			this.sign = null;
		}

		// Sanity check: is this sign still there?
		if (!this.validate()) {
			return;
		}

		// Real-time changes to the text (external cause)
		for (int i = 0; i < 4; i++) {
			if (!this.oldlines[i].equals(this.sign.getLine(i))) {
				Block signblock = this.getBlock();
				String varname = Variables.parseVariableName(this.oldlines[i]);
				if (varname != null) {
					Variables.get(varname).removeLocation(signblock, i);
				}
				this.oldlines[i] = this.sign.getLine(i);
				this.setLine(i, this.oldlines[i]);
				varname = Variables.parseVariableName(this.oldlines[i]);
				if (varname != null) {
					Variables.get(varname).addLocation(signblock, i);
				}
			}
		}

		// Send updated sign text to nearby players
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

	public void sendCurrentLines(Player player) {
		sendLines(getLines(player), player);
	}

	public void sendLines(VirtualLines lines, Player player) {
		lines.updateSign(player, getX(), getY(), getZ());
	}
}