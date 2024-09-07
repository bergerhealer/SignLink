package com.bergerkiller.bukkit.sl.API.events;

import com.bergerkiller.bukkit.common.block.SignSide;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import org.bukkit.block.Block;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.block.BlockEvent;

/**
 * Event fired by SignLink whenever a Sign is detected that contains variables
 * denoted by %-characters. The event can be cancelled to suppress this
 * detection.<br>
 * <br>
 * This event could be fired a lot of times as signs are discovered in the world,
 * so do not do too much processing when handling this event.
 */
public class SignVariablesDetectEvent extends BlockEvent implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    private final SignSide side;
    private final String[] lines;
    private boolean cancelled = false;

    public SignVariablesDetectEvent(Block signBlock, SignSide side, String[] lines) {
        super(signBlock);
        this.side = side;
        this.lines = lines;
    }

    /**
     * Gets whether variables were detected on the front side. On older Minecraft versions
     * this always returns true.
     *
     * @return True if front side
     */
    public boolean isFrontSide() {
        return side.isFront();
    }

    /**
     * Gets the side of the sign where variables were detected. On older Minecraft versions
     * this is always FRONT.
     *
     * @return Side side
     */
    public SignSide getSide() {
        return side;
    }

    public String[] getLines() {
        return lines;
    }

    public String getLine(int index) {
        return lines[index];
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    /**
     * Used by SignLink to fire the event and check variable detection is allowed
     *
     * @param signBlock Block of the sign
     * @param side Side of the sign where variables were detected
     * @param lines Lines on that side of the sign
     * @return True if variables are allowed, False if not (suppressed)
     */
    public static boolean checkCanDetect(Block signBlock, SignSide side, String[] lines) {
        if (!CommonUtil.hasHandlers(handlers)) {
            return true;
        }

        SignVariablesDetectEvent event = new SignVariablesDetectEvent(signBlock, side, lines);
        CommonUtil.callEvent(event);
        return !event.isCancelled();
    }
}
