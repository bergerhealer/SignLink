package com.bergerkiller.bukkit.sl;

import org.bukkit.ChatColor;

import com.bergerkiller.bukkit.common.utils.StringUtil;

/**
 * A single character displayed on the sign, with all styling that is applied.
 */
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

}
