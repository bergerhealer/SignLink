package com.bergerkiller.bukkit.sl.impl;

import java.util.function.Consumer;

import com.bergerkiller.bukkit.sl.impl.VariableValueMap.Entry;

/**
 * The default ticker, which is used when a value is set for a variable that
 * all players see
 */
public class DefaultTicker extends TickerBaseImpl {
    private final VariableValueMap.Entry entry;

    public DefaultTicker(VariableValueMap.Entry entry) {
        this.entry = entry;
    }

    // clone constructor (unused)
    protected DefaultTicker(DefaultTicker source) {
        super(source);
        this.entry = source.entry;
    }

    @Override
    public DefaultTicker clone() {
        return new DefaultTicker(this);
    }

    @Override
    public void reset(String value) {
        // Reset ticker state for all default-value entries
        entry.text.resetTicker();
        for (Entry e : entry.getValueMap().getPlayerEntries()) {
            if (!e.hasCustomValue()) {
                e.text.resetTicker();
            }
        }

        // Set the value for default entries
        entry.setValue(value);
    }

    private String updateNow(Consumer<TickerText> operation) {
        operation.accept(entry.text);
        entry.computePlayerText(false);

        for (VariableValueMap.Entry e : entry.getValueMap().getPlayerEntries()) {
            if (!e.hasCustomValue()) {
                operation.accept(e.text);
                e.computePlayerText(false);
            }
        }

        return entry.text.get();
    }

    @Override
    public String blink() {
        return updateNow(TickerText::blink);
    }

    @Override
    public String left() {
        return updateNow(TickerText::left);
    }

    @Override
    public String right() {
        return updateNow(TickerText::right);
    }

    @Override
    public String current() {
        return entry.text.get();
    }

    @Override
    public String current(String playerName) {
        return entry.getValueMap().getText(playerName.toLowerCase());
    }
}
