package com.bergerkiller.bukkit.sl;

import java.util.ArrayList;
import java.util.Arrays;

import org.bukkit.ChatColor;

import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.StringUtil;

public class StyledString extends ArrayList<StyledCharacter> {
    private static final long serialVersionUID = -9201633429398374786L;

    /**
     * Sets the contents of this Styled String to that of a String
     * 
     * @param text to set to
     */
    public void setToString(String text) {
        this.clear();
        this.appendString(text);
    }

    /**
     * Sets the contents of this Styled String to that of a String
     * 
     * @param startStyle used to define the initial styling options of the text
     * @param text to set to
     */
    public void setToString(StyledCharacter startStyle, String text) {
        this.clear();
        this.appendString(startStyle, text);
    }

    /**
     * Appends the contents of a String to this Styled String
     * 
     * @param text to append
     */
    public void appendString(String text) {
        StyledCharacter prev;
        if (this.isEmpty()) {
            prev = new StyledCharacter(' ', ChatColor.BLACK, new ChatColor[0]);
        } else {
            prev = this.get(this.size() - 1);
        }
        appendString(prev, text);
    }

    /**
     * Appends the contents of a String to this Styled String
     * 
     * @param startStyle used to define the initial styling options of the text
     * @param text to append
     */
    public void appendString(StyledCharacter startStyle, String text) {
        // Turn every character into a 'styled' character token
        // Every single character must know what styles are applied in case of cut-off
        ChatColor currentColor = startStyle.color;
        ChatColor[] currentFormats = startStyle.formats;
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
            this.add(new StyledCharacter('\0', currentColor, currentFormats));
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
            if (sc.character != '\0') {
                result.append(sc.character);
            }
        }
        return result.toString();
    }

    private static final boolean canFitOnSign(int width) {
        return width < VirtualLines.LINE_WIDTH_LIMIT;
    }
}
