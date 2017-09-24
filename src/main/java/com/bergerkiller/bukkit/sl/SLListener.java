package com.bergerkiller.bukkit.sl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;

import org.bukkit.ChatColor;
import org.bukkit.World;
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
import org.bukkit.event.world.WorldLoadEvent;

import com.bergerkiller.bukkit.common.MessageBuilder;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.events.PacketReceiveEvent;
import com.bergerkiller.bukkit.common.events.PacketSendEvent;
import com.bergerkiller.bukkit.common.protocol.CommonPacket;
import com.bergerkiller.bukkit.common.protocol.PacketListener;
import com.bergerkiller.bukkit.common.protocol.PacketType;
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

        CommonPacket packet = event.getPacket();
        IntVector3 position;
        World world;
		if (event.getType() == PacketType.OUT_TILE_ENTITY_DATA) {
			position = packet.read(PacketType.OUT_TILE_ENTITY_DATA.position);
			world = event.getPlayer().getWorld();
		} else if (event.getType() == PacketType.OUT_UPDATE_SIGN) {
		    position = packet.read(PacketType.OUT_UPDATE_SIGN.position);
		    world = packet.read(PacketType.OUT_UPDATE_SIGN.world);
		} else {
		    return;
		}

        VirtualSign sign = VirtualSign.get(world, position);
        if (sign != null) {
            sign.applyToPacket(event.getPlayer(), packet);
        }
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onSignChangeMonitor(SignChangeEvent event) {
	    // Detect variables on the sign and add lines that have them
	    for (int i = 0; i < VirtualLines.LINE_COUNT; i++) {
	        String varname = Variables.parseVariableName(event.getLine(i));
	        if (varname != null) {
                Variable var = Variables.get(varname);
                if (!var.addLocation(event.getBlock(), i)) {
                    event.getPlayer().sendMessage(ChatColor.RED + "Failed to create a sign linking to variable '" + varname + "'!");
                }
	        }
	    }

	    // Update sign order and other information the next tick (after this sign is placed)
	    VirtualSign.updateSign(event.getBlock());
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onSignChange(final SignChangeEvent event) {
	    //Convert colors
		for (int i = 0; i < VirtualLines.LINE_COUNT; i++) {
			event.setLine(i, StringUtil.ampToColor(event.getLine(i)));
		}
	
		//General stuff...
		boolean allowvar = Permission.ADDSIGN.has(event.getPlayer());
		boolean showedDisallowMessage = false;
		final ArrayList<String> varnames = new ArrayList<String>();
		for (int i = 0; i < VirtualLines.LINE_COUNT; i++) {
		    String varname = Variables.parseVariableName(event.getLine(i));
			if (varname != null) {
			    if (allowvar) {
			        varnames.add(varname);
			    } else {
			        if (!showedDisallowMessage) {
			            showedDisallowMessage = true;
			            event.getPlayer().sendMessage(ChatColor.RED + "Failed to create a sign linking to variable '" + varname + "'!");
			        }
			        event.setLine(i, event.getLine(i).replace('%', ' '));
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

	public void refreshBlockStates(Collection<BlockState> blockStates) {
        try {
            for (BlockState state : blockStates) {
                if (state instanceof Sign) {
                    Sign sign = (Sign) state;

                    // Load the sign
                    VirtualSign vsign = VirtualSign.get(state.getBlock());
                    if (vsign != null) {
                        vsign.setLoaded(true);
                        if (!vsign.validate(sign)) {
                            // This sign is no longer valid (for whatever reason)
                            continue;
                        }
                    } else {
                        // Detect variables on the sign and add lines that have them
                        for (int i = 0; i < VirtualLines.LINE_COUNT; i++) {
                            String varname = Variables.parseVariableName(sign.getLine(i));
                            if (varname != null) {
                                Variable var = Variables.get(varname);
                                if (!var.addLocation(sign.getBlock(), i)) {
                                    SignLink.plugin.log(Level.WARNING, "Failed to create a sign linking to variable '" + varname + "'!");
                                }
                            }
                        }

                        // Refresh newly found signs
                        VirtualSign.updateSign(sign.getBlock());
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

	@EventHandler(priority = EventPriority.MONITOR)
	public void onChunkLoad(ChunkLoadEvent event) {
	    refreshBlockStates(WorldUtil.getBlockStates(event.getChunk()));
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onWorldLoad(WorldLoadEvent event) {
	    refreshBlockStates(WorldUtil.getBlockStates(event.getWorld()));
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
