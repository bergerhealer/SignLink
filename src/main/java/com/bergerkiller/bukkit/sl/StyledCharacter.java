package com.bergerkiller.bukkit.sl;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.bukkit.ChatColor;

import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.StringUtil;

public class StyledCharacter {
    public final char character;
    public final ChatColor color;
    public final ChatColor[] formats;
    public final int width;

    public StyledCharacter(char character) {
        this(character, ChatColor.BLACK, new ChatColor[0]);
    }

    public StyledCharacter(char character, ChatColor color, ChatColor[] formats) {
        this.character = character;
        this.color = color;
        this.formats = formats;
        if (this.character == '\0') {
            this.width = 0;
        } else {
            this.width = StringUtil.getWidth(this.character) + ( (this.character == ' ') ? 2 : 1 );
        }
    }

    /**
     * Takes over the style formatting and produces an empty space
     * 
     * @return empty space with formatting of this character
     */
    public StyledCharacter asSpace() {
        return new StyledCharacter(' ', this.color, this.formats);
    }

    /**
     * Converts a sequence of styled characters back into formatted text form
     * 
     * @param characters to convert
     * @return formatted text
     */
    public static String getText(Iterable<StyledCharacter> characters) {
        ChatColor currentColor = ChatColor.BLACK;
        ChatColor[] currentFormats = new ChatColor[0];
        StringBuilder result = new StringBuilder();
        boolean isFormatReset;
        for (StyledCharacter sc : characters) {
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

    /**
     * Gets the total amount of pixel width characters take up
     * 
     * @param characters
     * @return total width
     */
    public static int getTotalWidth(Iterable<StyledCharacter> characters) {
        int totalWidth = 0;
        for (StyledCharacter sc : characters) {
            totalWidth += sc.width;
        }
        return totalWidth;
    }

    /**
     * Converts formatted text into a linked list of styled characters.
     * 
     * @param text input
     * @return styled character linked list output
     */
    public static LinkedList<StyledCharacter> getChars(String text) {
        StyledCharacter prev = new StyledCharacter(' ', ChatColor.BLACK, new ChatColor[0]);
        return getChars(prev, text);
    }

    /**
     * Converts formatted text into a linked list of styled characters.
     * 
     * @param previousChar containing the style from which to continue
     * @param text input
     * @return styled character linked list output
     */
    public static LinkedList<StyledCharacter> getChars(StyledCharacter previousChar, String text) {
        // Turn every character into a 'styled' character token
        // Every single character must know what styles are applied in case of cut-off
        LinkedList<StyledCharacter> characters = new LinkedList<StyledCharacter>();
        ChatColor currentColor = previousChar.color;
        ChatColor[] currentFormats = previousChar.formats;
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
                characters.addLast(new StyledCharacter(c, currentColor, currentFormats));
            }
        }
        if (characters.isEmpty() && hasFormatting) {
            characters.add(new StyledCharacter('\0', currentColor, currentFormats));
        }
        return characters;
    }

    /**
     * Gets the amount of signs minimally required to display a sequence of StyledCharacters
     * 
     * @param characters to display
     * @param leftPadding amount of width already occupied to the left
     * @param rightPadding amount of width already occupied to the right
     * @return number of signs needed to display
     */
    public static int getSignCount(List<StyledCharacter> characters, int leftPadding, int rightPadding) {
        // Definitely need 2 or more signs. First handle the characters to the right.
        // This handles the rightPadding logic.
        int signCount = 1;
        int endIndex = characters.size() - 1;
        int width = rightPadding;
        while (endIndex >= 0) {
            StyledCharacter sc = characters.get(endIndex);
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
            StyledCharacter sc = characters.get(i);
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

    private static boolean canFitOnSign(int width) {
        return width < VirtualLines.LINE_WIDTH_LIMIT;
    }
}
