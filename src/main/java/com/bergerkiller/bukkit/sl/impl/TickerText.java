package com.bergerkiller.bukkit.sl.impl;

import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.sl.StyledCharacter;
import com.bergerkiller.bukkit.sl.StyledString;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Stores the text value of a variable as it is displayed on signs,
 * in a format that allows the text to scroll and wrap around
 */
public class TickerText implements Cloneable {
    private String value = "";
    private final StyledString styledValue;
    private StyledElementSequence styledValueElements;
    protected int pauseindex;
    protected int pausedelay;
    protected int pauseduration;
    protected long counter;

    private TickerText(String value, StyledString styledValue, StyledElementSequence styledValueElements) {
        this.value = value;
        this.styledValue = styledValue;
        this.styledValueElements = styledValueElements;
        this.pauseindex = 0;
        this.pausedelay = 0;
        this.pauseduration = 0;
        this.counter = 0;
    }

    @Override
    public TickerText clone() {
        StyledString clonedValue = this.styledValue.clone();
        return new TickerText(this.value, clonedValue, this.styledValueElements.withValue(clonedValue));
    }

    public void resetTicker() {
        this.pauseindex = -1;
        this.pauseduration = 0;
        this.pausedelay = 0;
        this.counter = 0;
        this.none();
    }

    public void setTo(String text) {
        this.styledValue.setTo(text);
        this.value = this.styledValueElements.isDefault() ? text : this.styledValueElements.stringify();
    }

    public void setToDefault(String variableName) {
        String text = "%" + variableName + "%";
        int len = text.length();

        this.styledValue.clear();
        this.styledValue.setStartStyle(StyledCharacter.INITIAL_STYLE);
        for (int i = 0; i < len; i++) {
            this.styledValue.add(new StyledCharacter(text.charAt(i)));
        }
        this.value = this.styledValueElements.isDefault() ? text : this.styledValueElements.stringify();
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
     * Disables any ongoing left/right shift or blink, and resets to show
     * the variable text as if no ticker is active.
     *
     * @return text value
     */
    public String none() {
        if (!this.styledValueElements.isDefault()) {
            this.styledValueElements = new ShiftedText(this.styledValue);
            this.value = this.styledValueElements.stringify();
        }
        return this.value;
    }

    /**
     * Gets the next value when blinking on and off
     * 
     * @return Next value
     */
    public String blink() {
        if (this.styledValueElements instanceof BlinkOffText) {
            this.styledValueElements = new ShiftedText(this.styledValue);
        } else {
            this.styledValueElements = new BlinkOffText(this.styledValue);
        }

        this.value = this.styledValueElements.stringify();
        return this.value;
    }

    /**
     * Gets the next value when ticking the text to the left
     * 
     * @return Next value
     */
    public String left() {
        if (!(this.styledValueElements instanceof ShiftedText)) {
            this.styledValueElements = new ShiftedText(this.styledValue);
        } else {
            ((ShiftedText) this.styledValueElements).shiftLeft();
        }

        this.value = this.styledValueElements.stringify();
        return this.value;
    }

    /**
     * Gets the next value when ticking the text to the right
     * 
     * @return Next value
     */
    public String right() {
        if (!(this.styledValueElements instanceof ShiftedText)) {
            this.styledValueElements = new ShiftedText(this.styledValue);
        } else {
            ((ShiftedText) this.styledValueElements).shiftRight();
        }

        this.value = this.styledValueElements.stringify();
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
        StyledString defaultString = new StyledString();
        TickerText result = new TickerText("", defaultString, new ShiftedText(defaultString));
        result.setToDefault(variableName);
        return result;
    }

    private interface StyledElementSequence extends Iterable<StyledCharacter> {
        boolean isDefault();
        StyledElementSequence withValue(StyledString value);

        default String stringify() {
            return StyledCharacter.stringify(this);
        }
    }

    /**
     * Represents a filled string of all spaces
     */
    private static final class BlinkOffText implements StyledElementSequence {
        private final int length;

        public BlinkOffText(StyledString value) {
            this.length = value.size();
        }

        @Override
        public boolean isDefault() {
            return false;
        }

        @Override
        public StyledElementSequence withValue(StyledString value) {
            return new BlinkOffText(value);
        }

        @Override
        public String stringify() {
            return StringUtil.getFilledString(" ", this.length);
        }

        @Override
        public Iterator<StyledCharacter> iterator() {
            return new Iterator<StyledCharacter>() {
                int remaining = length;

                @Override
                public boolean hasNext() {
                    return remaining > 0;
                }

                @Override
                public StyledCharacter next() {
                    if (remaining > 0) {
                        --remaining;
                        return StyledCharacter.INITIAL_STYLE;
                    } else {
                        throw new NoSuchElementException();
                    }
                }
            };
        }
    }

    /**
     * Indexes into the source string at a shifted index offset
     */
    private static final class ShiftedText implements StyledElementSequence {
        private final StyledString value;
        private int shiftOffset;

        public ShiftedText(StyledString value) {
            this(value, 0);
        }

        private ShiftedText(StyledString value, int shiftOffset) {
            this.value = value;
            this.shiftOffset = shiftOffset;
        }

        @Override
        public boolean isDefault() {
            return shiftOffset == 0;
        }

        @Override
        public ShiftedText withValue(StyledString value) {
            return new ShiftedText(value, this.shiftOffset);
        }

        public void shiftLeft() {
            if (value.isEmpty()) {
                this.shiftOffset = 0;
            } else if (++this.shiftOffset >= value.size()) {
                this.shiftOffset %= value.size();
            }
        }

        public void shiftRight() {
            if (value.isEmpty()) {
                this.shiftOffset = 0;
            } else if (--this.shiftOffset < 0) {
                this.shiftOffset = value.size() - 1;
            }
        }

        @Override
        public Iterator<StyledCharacter> iterator() {
            if (shiftOffset == 0) {
                return value.iterator();
            }

            return new Iterator<StyledCharacter>() {
                int index = shiftOffset;
                int endIndex = index + value.size();

                @Override
                public boolean hasNext() {
                    return index != endIndex;
                }

                @Override
                public StyledCharacter next() {
                    if (hasNext()) {
                        return value.get(index++ % value.size());
                    } else {
                        throw new NoSuchElementException();
                    }
                }
            };
        }
    }
}
