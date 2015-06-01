package com.bergerkiller.bukkit.sl;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.protocol.PacketType;
import com.bergerkiller.bukkit.common.utils.PacketUtil;

public class VirtualLines {
	public static final int MAX_LINE_LENGTH = 15;
	public static final int LINE_COUNT = 4;
	private String[] lines = new String[LINE_COUNT];
	private boolean changed = false;

	public VirtualLines(String[] lines) {
//		System.out.println(lines);
//		System.out.println(lines[0]);
//		System.out.println(lines[1]);
//		System.out.println(lines[2]);
//		System.out.println(lines[3]);
		System.arraycopy(lines, 0, this.lines, 0, LINE_COUNT);
	}

	public void set(int index, String value) {
		if (value.length() > MAX_LINE_LENGTH) {
			value = value.substring(0, MAX_LINE_LENGTH);
		}
		if (!this.lines[index].equals(value)) {
			this.changed = true;
			this.lines[index] = value;
		}
	}

	public String get(int index) {
		return lines[index];
	}

	public String[] get() {
		return this.lines;
	}

	public boolean hasChanged() {
		return this.changed;
	}

	public void setChanged() {
		setChanged(true);
	}

	public void setChanged(boolean changed) {
		this.changed = changed;
	}

	public void updateSign(Player player, int x, int y, int z) {
		if (SignLink.updateSigns && player != null) {
			SLListener.ignore = true;
			PacketUtil.sendPacket(player, PacketType.OUT_UPDATE_SIGN.newInstance(new Location(player.getLocation().getWorld(), x, y, z).getBlock(), LineConverter.convert(this.lines)));
			SLListener.ignore = false;
		}
	}
}
