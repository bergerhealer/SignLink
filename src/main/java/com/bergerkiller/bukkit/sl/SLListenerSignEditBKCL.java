package com.bergerkiller.bukkit.sl;

import com.bergerkiller.bukkit.common.events.SignEditTextEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

class SLListenerSignEditBKCL implements Listener {
    private final SLListener listener;

    public SLListenerSignEditBKCL(SLListener listener) {
        this.listener = listener;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSignChange(SignEditTextEvent event) {
        listener.handleSignTextChange(event, event.getPlayer(), event.getBlock(), event.getSide(), linesOf(event));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSignChangeMonitor(SignEditTextEvent event) {
        listener.handleSignTextChangeMonitor(event, event.getPlayer(), event.getBlock(), event.getSide(), linesOf(event));
    }

    private static SLListener.SignEventLineAccessor linesOf(SignEditTextEvent event) {
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
