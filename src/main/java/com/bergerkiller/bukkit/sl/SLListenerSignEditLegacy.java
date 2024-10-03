package com.bergerkiller.bukkit.sl;

import com.bergerkiller.bukkit.common.block.SignSide;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;

class SLListenerSignEditLegacy implements Listener {
    private final SLListener listener;

    public SLListenerSignEditLegacy(SLListener listener) {
        this.listener = listener;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        listener.handleSignTextChange(event, event.getPlayer(), event.getBlock(), SignSide.sideChanged(event), linesOf(event));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSignChangeMonitor(SignChangeEvent event) {
        listener.handleSignTextChangeMonitor(event, event.getPlayer(), event.getBlock(), SignSide.sideChanged(event), linesOf(event));
    }

    private static SLListener.SignEventLineAccessor linesOf(SignChangeEvent event) {
        return new SLListener.SignEventLineAccessor() {
            @Override
            public String getLine(int index) {
                return event.getLine(index);
            }

            @Override
            public void setLine(int index, String text) {
                event.setLine(index, text);
            }
        };
    }
}
