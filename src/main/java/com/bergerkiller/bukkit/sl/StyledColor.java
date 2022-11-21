package com.bergerkiller.bukkit.sl;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.ChatColor;

import com.bergerkiller.bukkit.common.utils.StringUtil;

/**
 * A mode of styling the color of the text. Supports both hex-style and
 * legacy-style color codes.
 */
public abstract class StyledColor {
    private final String formatStr;

    // Map ChatColor -> StyledColor constants for performance
    private static final EnumMap<ChatColor, StyledColor> BY_CHAT_COLOR = new EnumMap<>(ChatColor.class);
    private static final Map<Character, StyledColor> BY_CODE = new HashMap<>();
    static {
        for (ChatColor color : ChatColor.values()) {
            if (color.isColor()) {
                LegacyStyledColor styled = new LegacyStyledColor(color);
                BY_CHAT_COLOR.put(color, styled);
                BY_CODE.put(Character.toLowerCase(color.getChar()), styled);
                BY_CODE.put(Character.toUpperCase(color.getChar()), styled);
            }
        }
    }

    public static final StyledColor NONE = new DefaultStyledColor();
    public static final StyledColor BLACK = BY_CHAT_COLOR.get(ChatColor.BLACK);

    public static StyledColor byColor(ChatColor color) {
        return BY_CHAT_COLOR.getOrDefault(color, NONE);
    }

    public static StyledColor byColorCode(char code) {
        return BY_CODE.getOrDefault(code, NONE);
    }

    /**
     * Decodes a hex-encoded color sequence at a position specified. This includes
     * the $x portion at the beginning. The text at the offset should start with
     * the style character, followed by a single 0-F hex value. This should repeat
     * 6 times. As such, the text should be at least 14 chars long after the offset.<br>
     * <br>
     * If non-null is returned, the caller can safely increment the offset scanning the
     * text with by 14.
     *
     * @param text Text to parse
     * @param offset Offset from which to decode
     * @return Decoded hex color, or null if decoding failed
     */
    public static StyledColor decodeHex(String text, int offset) {
        int remaining = text.length() - offset;
        if (remaining < 14) {
            return null;
        }

        int index = offset;
        if (text.charAt(index++) != StringUtil.CHAT_STYLE_CHAR) {
            return null;
        }
        if (text.charAt(index) != 'x' && text.charAt(index) != 'X') {
            return null;
        }
        index++;

        for (int n = 0; n < 6; n++) {
            if (text.charAt(index++) != StringUtil.CHAT_STYLE_CHAR) {
                return null;
            }
            char code = text.charAt(index++);
            if (!(code >= '0' && code <= '9') && !(code >= 'a' && code <= 'f') && !(code >= 'A' && code <= 'F')) {
                return null;
            }
        }
        return new HexStyledColor(text.substring(offset, offset + 14));
    }

    protected StyledColor(String formatStr) {
        this.formatStr = formatStr;
    }

    public final String format() {
        return formatStr;
    }

    public abstract boolean sameFormat(StyledColor color);

    @Override
    public String toString() {
        return formatStr;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof StyledColor) {
            return sameFormat((StyledColor) o);
        } else {
            return false;
        }
    }

    private static final class LegacyStyledColor extends StyledColor {

        private LegacyStyledColor(ChatColor color) {
            super(color.toString());
        }

        @Override
        public boolean sameFormat(StyledColor color) {
            return color == this;
        }
    }

    private static final class HexStyledColor extends StyledColor {

        public HexStyledColor(String formatStr) {
            super(formatStr);
        }

        @Override
        public boolean sameFormat(StyledColor color) {
            return color instanceof HexStyledColor && format().equals(color.format());
        }
    }

    private static final class DefaultStyledColor extends StyledColor {

        protected DefaultStyledColor() {
            super(ChatColor.RESET.toString());
        }

        @Override
        public boolean sameFormat(StyledColor color) {
            return color == this;
        }
    }
}
