package com.bergerkiller.bukkit.sl;

import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.BlockLocation;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;
import com.bergerkiller.bukkit.common.protocol.CommonPacket;
import com.bergerkiller.bukkit.common.protocol.PacketBlockStateChangeListener;
import com.bergerkiller.bukkit.common.resources.BlockStateType;
import com.bergerkiller.bukkit.common.wrappers.BlockStateChange;

class SLBlockStateChangeListener implements PacketBlockStateChangeListener {
    private static final AtomicReference<DirectApplier> applierCache = new AtomicReference<DirectApplier>(new DirectApplier());

    public static void applyDirect(VirtualSign sign, Player player, CommonPacket packet) {
        DirectApplier applier = applierCache.getAndSet(null);
        if (applier == null) {
            applier = new DirectApplier();
        }
        applier.sign = sign;
        applier.lines = sign.getLines(player);
        PacketBlockStateChangeListener.process(player, packet, applier);
        applierCache.set(applier);
    }

    @Override
    public boolean onBlockChange(final Player player, BlockStateChange change) {
        // Only interested in changes done to signs
        if (change.getType() != BlockStateType.SIGN) {
            return true;
        }

        // Check managed by signlink at all
        final VirtualSign sign = VirtualSign.get(player.getWorld(), change.getPosition());
        if (sign == null) {
            return true;
        }

        // Sign was updated: schedule a verify of the current text on the sign
        // In case this was also changed, cleans up other stuff
        sign.scheduleVerify();

        // Ignore vanilla signs
        if (!sign.hasVariables()) {
            return true;
        }

        CommonTagCompound metadata = change.getMetadata();

        final VirtualLines lines = sign.getLines(player);
        if (lines.isDifferentThanMetadata(metadata)) {
            lines.applyToSignMetadata(metadata);
        }

        return true;
    }

    private static class DirectApplier implements PacketBlockStateChangeListener {
        public VirtualSign sign;
        public VirtualLines lines;

        @Override
        public boolean onBlockChange(Player player, BlockStateChange change) {
            // Little protection guard that this really is the right sign...
            BlockLocation signPos = sign.getPosition();
            IntVector3 changePos = change.getPosition();
            if (signPos.x == changePos.x && signPos.y == changePos.y && signPos.z == changePos.z) {
                lines.applyToSignMetadata(change.getMetadata());
            }

            return true;
        }
    }
}
