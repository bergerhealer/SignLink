package com.bergerkiller.bukkit.sl;

import java.util.ArrayList;
import java.util.Arrays;

import org.bukkit.ChatColor;

import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.StringUtil;

/**
 * A list of styled characters with extra String-like helper methods
 */
public class StyledString extends ArrayList<StyledCharacter> {
    private static final long serialVersionUID = -9201633429398374786L;
    private StyledCharacter startStyle = StyledCharacter.INITIAL_STYLE;

    /**
     * Sets the start style of this Styled String. It's the style applied to the very
     * first character onwards. By default it is set to all-black unstyled characters.
     * 
     * @param startStyle
     */
    public void setStartStyle(StyledCharacter startStyle) {
        this.startStyle = startStyle;
    }

    /**
     * Sets the contents of this Styled String to that of a String
     * 
     * @param text to set to
     */
    public void setTo(String text) {
        this.clear();
        this.append(text);
    }

    /**
     * Appends the contents of a String to this Styled String
     * 
     * @param text to append
     */
    public void append(String text) {
        // Turn every character into a 'styled' character token
        // Every single character must know what styles are applied in case of cut-off
        StyledCharacter endStyle = this.getEndStyle();
        ChatColor currentColor = endStyle.color;
        ChatColor[] currentFormats = endStyle.formats;
        boolean hasFormatting = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == StringUtil.CHAT_STYLE_CHAR) {
                hasFormatting = true;
                if (++i >= text.length()) break;

                // Handle chat formatting characters
                ChatColor cc = StringUtil.getColor(text.charAt(i), currentColor);
                if (cc.isColor()) {
                    currentColor = cc;
                } else if (cc == ChatColor.RESET) {
                    currentFormats = new ChatColor[0];
                    currentColor = ChatColor.BLACK;
                } else if (!LogicUtil.contains(cc, currentFormats)) {
                    currentFormats = Arrays.copyOf(currentFormats, currentFormats.length + 1);
                    currentFormats[currentFormats.length - 1] = cc;
                }
            } else {
                // New character
                this.add(new StyledCharacter(c, currentColor, currentFormats));
            }
        }
        if (this.isEmpty() && hasFormatting) {
            this.add(StyledCharacter.createStyleChar(currentColor, currentFormats));
        }
    }

    /**
     * Gets the style used at the beginning of the current String.
     * 
     * @return start style character
     */
    public StyledCharacter getStartStyle() {
        return this.startStyle;
    }

    /**
     * Gets the style used at the end of the current String.
     * If this String is empty, the start style is returned instead.
     * This start style is set using {@link #appendString(StyledCharacter, String)} and
     * {@link #setToString(StyledCharacter, String)}.
     * 
     * @return end style character
     */
    public StyledCharacter getEndStyle() {
        if (this.isEmpty()) {
            return this.startStyle;
        } else {
            return this.getLast();
        }
    }

    /**
     * Gets the first styled character of this String
     * 
     * @return first character
     */
    public StyledCharacter getFirst() {
        return this.get(0);
    }

    /**
     * Gets the last styled character of this String
     * 
     * @return last character
     */
    public StyledCharacter getLast() {
        return this.get(this.size() - 1);
    }

    /**
     * Gets the total amount of pixel width of this Styled String
     * 
     * @return total width
     */
    public int getTotalWidth() {
        int totalWidth = 0;
        for (StyledCharacter sc : this) {
            totalWidth += sc.width;
        }
        return totalWidth;
    }

    /**
     * Gets the amount of signs minimally required to display this Styled String
     * 
     * @param leftPadding amount of width already occupied to the left
     * @param rightPadding amount of width already occupied to the right
     * @return number of signs needed to display
     */
    public int getSignCount(int leftPadding, int rightPadding) {
        // Definitely need 2 or more signs. First handle the characters to the right.
        // This handles the rightPadding logic.
        int signCount = 1;
        int endIndex = this.size() - 1;
        int width = rightPadding;
        while (endIndex >= 0) {
            StyledCharacter sc = this.get(endIndex);
            int newWidth = (width + sc.width);
            if (canFitOnSign(newWidth)) {
                width = newWidth;
                endIndex--;
            } else {
                break;
            }
        }

        // If no more text remains, we only have to handle left padding
        // Padding only applies for one sign, which means only 1 or 2 is returned.
        if (endIndex < 0) {
            if (canFitOnSign(width + leftPadding)) {
                return 1;
            } else {
                return 2;
            }
        }

        // All remaining characters must be filled left-to-right
        width = leftPadding;
        for (int i = 0; i <= endIndex; i++) {
            StyledCharacter sc = this.get(i);
            width += sc.width;
            if (!canFitOnSign(width)) {
                width = sc.width;
                signCount++;
            }
        }
        if (width > 0) {
            signCount++;
        }
        return signCount;
    }

    @Override
    public String toString() {
        ChatColor currentColor = ChatColor.BLACK;
        ChatColor[] currentFormats = new ChatColor[0];
        StringBuilder result = new StringBuilder();
        boolean isFormatReset;
        for (StyledCharacter sc : this) {
            // Handle format changes
            isFormatReset = false;
            if (sc.formats != currentFormats) {
                for (ChatColor oldFormat : currentFormats) {
                    if (!LogicUtil.contains(oldFormat, sc.formats)) {
                        isFormatReset = true;
                        break;
                    }
                }
                if (isFormatReset) {
                    // Format reset. Reset and then add all the new formats.
                    result.append(StringUtil.CHAT_STYLE_CHAR).append(ChatColor.RESET.getChar());
                    for (ChatColor format : sc.formats){
                        result.append(StringUtil.CHAT_STYLE_CHAR).append(format.getChar());
                    }
                    currentColor = ChatColor.BLACK; // current color resets too
                } else {
                    // Check for formats that have been added
                    for (ChatColor newFormat : sc.formats) {
                        if (!LogicUtil.contains(newFormat, currentFormats)) {
                            result.append(StringUtil.CHAT_STYLE_CHAR).append(newFormat.getChar());
                        }
                    }
                }
                currentFormats = sc.formats;
            }
            // Handle color changes
            if (currentColor != sc.color) {
                currentColor = sc.color;
                result.append(StringUtil.CHAT_STYLE_CHAR).append(currentColor.getChar());
            }
            // Append the actual character of interest
            if (!sc.isStyleOnly()) {
                result.append(sc.character);
            }
        }
        return result.toString();
    }

    @Override
    public StyledString clone() {
        return (StyledString) super.clone();
    }

    private static final boolean canFitOnSign(int width) {
        return width < VirtualLines.LINE_WIDTH_LIMIT;
    }
}
