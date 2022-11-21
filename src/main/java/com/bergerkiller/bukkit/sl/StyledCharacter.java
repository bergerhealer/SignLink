package com.bergerkiller.bukkit.sl;

import org.bukkit.ChatColor;

import com.bergerkiller.bukkit.common.utils.StringUtil;

/**
 * A single character displayed on the sign, with all styling that is applied.
 */
public class StyledCharacter {
    /**
     * Constant denoting no chat color formatting at all
     */
    public static final ChatColor[] NO_FORMATS = new ChatColor[0];

    public static StyledCharacter INITIAL_STYLE = new StyledCharacter(' ');

    public final char character;
    public final StyledColor color;
    public final ChatColor[] formats;
    public final int width;

    public StyledCharacter(char character) {
        this(character, StyledColor.NONE, NO_FORMATS);
    }

    public StyledCharacter(char character, ChatColor color, ChatColor[] formats) {
        this(character, StyledColor.byColor(color), formats);
    }

    public StyledCharacter(char character, StyledColor color, ChatColor[] formats) {
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
     * Writes all chat formatting (bold, italic, etc.) to the output builder
     *
     * @param out
     */
    public void appendFormats(StringBuilder out) {
        for (ChatColor format : this.formats) {
            out.append(StringUtil.CHAT_STYLE_CHAR).append(format.getChar());
        }
    }

    /**
     * Creates a style character that only declares style, and no actual text.
     * {@link #isStyleOnly()} will return true for this character.
     * 
     * @param color style color
     * @param formats style formats
     * @return styled character
     */
    public static StyledCharacter createStyleChar(StyledColor color, ChatColor[] formats) {
        return new StyledCharacter('\uFFFF', color, formats);
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
