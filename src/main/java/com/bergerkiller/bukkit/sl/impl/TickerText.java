package com.bergerkiller.bukkit.sl.impl;

import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.sl.StyledCharacter;
import com.bergerkiller.bukkit.sl.StyledString;

/**
 * Stores the text value of a variable as it is displayed on signs,
 * in a format that allows the text to scroll and wrap around
 */
public class TickerText implements Cloneable {
    private String value = "";
    private final StyledString valueElements;
    protected int pauseindex;
    protected int pausedelay;
    protected int pauseduration;
    protected long counter;

    private TickerText(String value, StyledString valueElements) {
        this.value = value;
        this.valueElements = valueElements;
        this.pauseindex = 0;
        this.pausedelay = 0;
        this.pauseduration = 0;
        this.counter = 0;
    }

    // clone
    private TickerText(TickerText source) {
        this(source.value, source.valueElements.clone());
    }

    @Override
    public TickerText clone() {
        return new TickerText(this);
    }

    public void resetTicker() {
        this.pauseindex = -1;
        this.pauseduration = 0;
        this.pausedelay = 0;
        this.counter = 0;
    }

    public void setTo(String text) {
        this.value = text;
        this.valueElements.setTo(this.value);
    }

    public void setToDefault(String variableName) {
        this.value = "%" + variableName + "%";
        int len = this.value.length();

        this.valueElements.clear();
        this.valueElements.setStartStyle(StyledCharacter.INITIAL_STYLE);
        for (int i = 0; i < len; i++) {
            this.valueElements.add(new StyledCharacter(this.value.charAt(i)));
        }
    }

    /**
     * Gets the current text value
     *
     * @return text value
     */
    public String get() {
        return this.value;
    }

    /**
     * Gets the next value when blinking on and off
     * 
     * @return Next value
     */
    public String blink() {
        for (int i = 0; i < this.value.length(); i++) {
            if (this.value.charAt(i) != ' ') {
                // Now we go with 'off' - all spaces
                this.value = StringUtil.getFilledString(" ", this.value.length());
                return this.value;
            }
        }
        // Now we go with 'on' - show text
        this.value = this.valueElements.toString();
        return this.value;
    }

    /**
     * Gets the next value when ticking the text to the left
     * 
     * @return Next value
     */
    public String left() {
        // Translate elements one to the left
        if (this.valueElements.size() >= 2) {
            StyledCharacter first = this.valueElements.remove(0);
            this.valueElements.add(first);
            // Update
            this.value = this.valueElements.toString();
        }
        return this.value;
    }

    /**
     * Gets the next value when ticking the text to the right
     * 
     * @return Next value
     */
    public String right() {
        // Translate elements one to the right
        if (this.valueElements.size() >= 2) {
            StyledCharacter last = this.valueElements.remove(this.valueElements.size() - 1);
            this.valueElements.add(0, last);
            // Update
            this.value = this.valueElements.toString();
        }
        return this.value;
    }

    /**
     * Creates the ticker text of an unset variable, showing the original
     * variable name with %-signs around it. The variable name may not
     * contain chat formatting characters.
     *
     * @param variableName Name of the variable
     * @return ticker text
     */
    public static TickerText createDefaultValue(String variableName) {
        TickerText result = new TickerText("", new StyledString());
        result.setToDefault(variableName);
        return result;
    }
}
