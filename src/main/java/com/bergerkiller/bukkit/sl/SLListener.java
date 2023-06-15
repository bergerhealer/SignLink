package com.bergerkiller.bukkit.sl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import com.bergerkiller.bukkit.common.block.SignSide;
import com.bergerkiller.bukkit.common.internal.CommonCapabilities;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.generated.org.bukkit.block.SignHandle;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import com.bergerkiller.bukkit.common.MessageBuilder;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.sl.API.Variable;
import com.bergerkiller.bukkit.sl.API.Variables;
import com.bergerkiller.bukkit.sl.impl.VariableImpl;
import com.bergerkiller.bukkit.sl.impl.VariableMap;

public class SLListener implements Listener {
    private final List<VariableImpl> variableBuffer = new ArrayList<VariableImpl>();
    private final List<LinkedSign> linkedSignBuffer = new ArrayList<LinkedSign>();
    private final Map<String, Player> playersByLowercaseName = new HashMap<String, Player>();

    protected SLListener() {
        // Fill cache up-front
        for (Player player : Bukkit.getOnlinePlayers()) {
            playersByLowercaseName.put(player.getName().toLowerCase(), player);
        }
    }

    /**
     * Gets the player by name, case-insensitive. The Bukkit getPlayer() has some
     * (performance) flaws, and on older versions of Minecraft the exact getter
     * wasn't case-insensitive at all.
     *
     * @param name Player name, must be all-lowercase
     * @return Player matching this name, or null if not online right now
     */
    public Player getPlayerByLowercase(String name) {
        synchronized (playersByLowercaseName) {
            Player cached = playersByLowercaseName.get(name);
            if (cached == null) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase().equals(name)) {
                        playersByLowercaseName.put(name, player);
                        cached = player;
                        break;
                    }
                }
            }
            return cached;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSignChangeMonitor(SignChangeEvent event) {
        // Detect variables on the sign and add lines that have them
        SignSide side = SignSide.sideChanged(event);
        for (int i = 0; i < VirtualLines.LINE_COUNT; i++) {
            String varname = Variables.parseVariableName(event.getLine(i));
            if (varname != null) {
                Variable var = Variables.get(varname);
                if (!var.addLocation(event.getBlock(), side, i)) {
                    event.getPlayer().sendMessage(ChatColor.RED + "Failed to create a sign linking to variable '" + varname + "'!");
                }
            }
        }

        // Update sign order and other information the next tick (after this sign is placed)
        VirtualSign.updateSign(event.getBlock(), SignSide.sideChanged(event), event.getLines());
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

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onSignClick(PlayerInteractEvent event) {
        // Only used >= 1.20 where sign editing is possible
        if (!CommonCapabilities.HAS_SIGN_BACK_TEXT) {
            return;
        }

        // Must be right-clicking a sign
        if (event.getClickedBlock() == null
                || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || !MaterialUtil.ISSIGN.get(event.getClickedBlock())
        ) {
            return;
        }

        // Must be a sign tracked here and it must have variables declared
        VirtualSign vsign = VirtualSignStore.get(event.getClickedBlock());
        if (vsign == null || !vsign.hasVariables()) {
            return;
        }

        // Send the original lines to this player, and after a single tick delay, restore
        vsign.sendRealLines(event.getPlayer());
        CommonUtil.nextTick(() -> vsign.sendCurrentLines(event.getPlayer()));
    }

    public void loadSigns(Collection<BlockState> blockStates) {
        try {
            for (BlockState state : blockStates) {
                if (state instanceof Sign) {
                    Sign sign = (Sign) state;

                    // Update the sign
                    VirtualSign vsign = VirtualSign.createSign(sign);
                    if (vsign != null) {
                        SignHandle signHandle = SignHandle.createHandle(sign);
                        detectSignVariables(vsign, sign, signHandle, SignSide.FRONT);
                        if (SignSide.BACK.isSupported()) {
                            detectSignVariables(vsign, sign, signHandle, SignSide.BACK);
                        }
                    }

                    // Fill with variables
                    VariableMap.INSTANCE.find(linkedSignBuffer, variableBuffer, state.getBlock());
                }
            }
            // Size check
            if (variableBuffer.size() != linkedSignBuffer.size()) {
                throw new RuntimeException("Variable find method signature is invalid: linked sign count != variable count");
            }
            // Update all the linked signs using the respective variables
            for (int i = 0; i < variableBuffer.size(); i++) {
                variableBuffer.get(i).updateSign(linkedSignBuffer.get(i));
            }
        } finally {
            variableBuffer.clear();
            linkedSignBuffer.clear();
        }
    }

    private void detectSignVariables(VirtualSign vsign, Sign sign, SignHandle signHandle, SignSide side) {
        for (int i = 0; i < VirtualLines.LINE_COUNT; i++) {
            String varname = Variables.parseVariableName(side.getLine(signHandle, i));
            if (varname != null) {
                Variable var = Variables.get(varname);
                if (!var.addLocation(sign.getBlock(), side, i)) {
                    SignLink.plugin.log(Level.WARNING, "Failed to create a sign linking to variable '" + varname + "'!");
                }
            }
        }
    }

    public void unloadSigns(Collection<BlockState> blockStates) {
        for (BlockState state : blockStates) {
            if (state instanceof Sign) {
                VirtualSign.remove(state.getBlock());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        loadSigns(WorldUtil.getBlockStates(event.getChunk()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        loadSigns(WorldUtil.getBlockStates(event.getWorld()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent event) {
        unloadSigns(WorldUtil.getBlockStates(event.getChunk()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        unloadSigns(WorldUtil.getBlockStates(event.getWorld()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Variables.removeLocation(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        final Player p = event.getPlayer();

        // Store early
        this.playersByLowercaseName.put(p.getName().toLowerCase(), p);

        if (SignLink.plugin.papi != null) {
            SignLink.plugin.papi.refreshVariables(p);
        }
        Variables.get("playername").forPlayer(p).set(p.getName());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (SignLink.plugin.papi != null) {
            final Player p = event.getPlayer();
            CommonUtil.nextTick(new Runnable() {
                public void run() {
                    SignLink.plugin.papi.refreshVariables(p);
                }
            });
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        VirtualSign.invalidateAll(event.getPlayer());

        // Cleanup
        this.playersByLowercaseName.remove(event.getPlayer().getName().toLowerCase());
    }
}
