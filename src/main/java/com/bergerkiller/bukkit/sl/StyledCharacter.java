package com.bergerkiller.bukkit.sl;

import org.bukkit.ChatColor;

import com.bergerkiller.bukkit.common.utils.StringUtil;

/**
 * A single character displayed on the sign, with all styling that is applied.
 */
public class StyledCharacter {
    public static StyledCharacter INITIAL_STYLE = new StyledCharacter(' ');

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
        if (this.isStyleOnly()) {
            this.width = 0;
        } else if (this.character == '\0') {
            this.width = 1;
        } else if (isBold()) {
            this.width = StringUtil.getWidth(this.character) + ( (this.character == ' ') ? 3 : 2 );
        } else {
            this.width = StringUtil.getWidth(this.character) + ( (this.character == ' ') ? 2 : 1 );
        }
    }

    /**
     * Whether the bold format is used
     * 
     * @return True if bold
     */
    public boolean isBold() {
        for (ChatColor format : formats) {
            if (format == ChatColor.BOLD) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets whether this character only declares style, and no actual text.
     * The width of this character is guaranteed to be 0.
     * 
     * @return True if only style is stored
     */
    public boolean isStyleOnly() {
        return this.character == '\uFFFF';
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
     * Creates a style character that only declares style, and no actual text.
     * {@link #isStyleOnly()} will return true for this character.
     * 
     * @param color style color
     * @param formats style formats
     * @return styled character
     */
    public static StyledCharacter createStyleChar(ChatColor color, ChatColor[] formats) {
        return new StyledCharacter('\uFFFF', color, formats);
    }

}
