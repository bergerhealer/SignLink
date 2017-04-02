package com.bergerkiller.bukkit.sl;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;

import com.bergerkiller.bukkit.common.MessageBuilder;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.events.PacketReceiveEvent;
import com.bergerkiller.bukkit.common.events.PacketSendEvent;
import com.bergerkiller.bukkit.common.protocol.CommonPacket;
import com.bergerkiller.bukkit.common.protocol.PacketListener;
import com.bergerkiller.bukkit.common.protocol.PacketType;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.sl.API.Variable;
import com.bergerkiller.bukkit.sl.API.Variables;

public class SLListener implements Listener, PacketListener {
	protected static boolean ignore = false;
	private final List<Variable> variableBuffer = new ArrayList<Variable>();
	private final List<LinkedSign> linkedSignBuffer = new ArrayList<LinkedSign>();

	@Override
	public void onPacketReceive(PacketReceiveEvent event) {
	}

	@Override
	public void onPacketSend(PacketSendEvent event) {
		if (ignore) {
			return;
		}
		if (event.getType() == PacketType.OUT_TILE_ENTITY_DATA) {
			CommonPacket packet = event.getPacket();
			IntVector3 position = packet.read(PacketType.OUT_TILE_ENTITY_DATA.position);
			VirtualSign sign = VirtualSign.get(event.getPlayer().getWorld(), position);
			if (sign != null) {
			    sign.applyToPacket(event.getPlayer(), packet);
			}
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onSignChange(final SignChangeEvent event) {
		//Convert colors
		for (int i = 0; i < VirtualLines.LINE_COUNT; i++) {
			event.setLine(i, StringUtil.ampToColor(event.getLine(i)));
		}

		// Update sign order and other information the next tick (after this sign is placed)
		CommonUtil.nextTick(new Runnable() {
			public void run() {
				if (event.isCancelled()) {
					return;
				}
				if (!VirtualSign.exists(event.getBlock())) {
					VirtualSign.add(event.getBlock(), event.getLines());
				}
				Variables.updateSignOrder(event.getBlock());
			}
		});
	
		//General stuff...
		boolean allowvar = Permission.ADDSIGN.has(event.getPlayer());
		final ArrayList<String> varnames = new ArrayList<String>();
		for (int i = 0; i < VirtualLines.LINE_COUNT; i++) {
			String varname = Variables.parseVariableName(event.getLine(i));
			if (varname != null) {
				if (allowvar) {
					Variable var = Variables.get(varname);
					if (var.addLocation(event.getBlock(), i)) {
						varnames.add(varname);
					} else {
						event.getPlayer().sendMessage(ChatColor.RED + "Failed to create a sign linking to variable '" + varname + "'!");
					}
				} else {
					event.getPlayer().sendMessage(ChatColor.DARK_RED + "You don't have permission to use dynamic text on signs!");
					return;
				}
			}
		}
		if (varnames.isEmpty()) {
			return;
		}

		// Send a message to the player showing that SignLink has responded
		MessageBuilder message = new MessageBuilder().green("You made a sign linking to ");
		if (varnames.size() == 1) {
			message.append("variable: ").yellow(varnames.get(0));
		} else {
			message.append("variables: ").yellow(StringUtil.join(" ", varnames));
		}
		message.send(event.getPlayer());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onChunkLoad(ChunkLoadEvent event) {
		try {
			for (BlockState state : WorldUtil.getBlockStates(event.getChunk())) {
				if (state instanceof Sign) {
					// Load the sign
					VirtualSign sign = VirtualSign.get(state.getBlock());
					if (sign != null) {
						sign.setLoaded(true);
						if (!sign.validate()) {
							// This sign is no longer valid (for whatever reason)
							continue;
						}
					}
					// Fill with variables
					Variables.find(linkedSignBuffer, variableBuffer, state.getBlock());
				}
			}
			// Size check
			if (variableBuffer.size() != linkedSignBuffer.size()) {
				throw new RuntimeException("Variable find method signature is invalid: linked sign count != variable count");
			}
			// Update all the linked signs using the respective variables
			for (int i = 0; i < variableBuffer.size(); i++) {
				variableBuffer.get(i).update(linkedSignBuffer.get(i));
			}
		} finally {
			variableBuffer.clear();
			linkedSignBuffer.clear();
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBlockBreak(BlockBreakEvent event) {
		Variables.removeLocation(event.getBlock());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerJoin(PlayerJoinEvent event) {
		final Player p = event.getPlayer();
		Variables.get("playername").forPlayer(p).set(p.getName());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerQuit(PlayerQuitEvent event) {
		VirtualSign.invalidateAll(event.getPlayer());
	}
}
