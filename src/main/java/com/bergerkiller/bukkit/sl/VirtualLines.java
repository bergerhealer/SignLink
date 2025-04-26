package com.bergerkiller.bukkit.sl;

import com.bergerkiller.bukkit.common.block.SignChangeTracker;
import com.bergerkiller.bukkit.common.block.SignSide;
import com.bergerkiller.bukkit.common.block.SignSideMap;
import com.bergerkiller.bukkit.common.internal.CommonCapabilities;
import com.bergerkiller.bukkit.common.nbt.CommonTag;
import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;
import com.bergerkiller.bukkit.common.nbt.CommonTagList;
import com.bergerkiller.bukkit.common.wrappers.ChatText;

import java.util.function.Supplier;
import java.util.logging.Level;

public class VirtualLines {
    private static final String[] SIGN_META_LINE_KEYS_PRE_1_20 = new String[] { "Text1", "Text2", "Text3", "Text4" };
    public static final int LINE_WIDTH_LIMIT = 90;
    public static final int LINE_COUNT = 4;
    private final SignSideMap<SignSideLines> lines = new SignSideMap<>();
    private boolean changed = false;

    @Deprecated
    public VirtualLines(String[] frontLines, String[] backLines) {
        this(new SignSideLines(frontLines), new SignSideLines(backLines));
    }

    public VirtualLines(VirtualLines originalLines) {
        this(originalLines.lines.front().clone(), originalLines.lines.back().clone());
    }

    public VirtualLines(SignSideLines frontLines, SignSideLines backLines) {
        this.lines.setFront(frontLines);
        if (CommonCapabilities.HAS_SIGN_BACK_TEXT) {
            this.lines.setBack(backLines);
        } else {
            this.lines.setBack(SignSideLines.UNSUPPORTED);
        }
    }

    public boolean isDifferentThanMetadata(CommonTagCompound metadata) {
        if (CommonCapabilities.HAS_SIGN_BACK_TEXT) {
            // >= 1.20
            return lines.front().checkDifferentThanSignUpdate(metadata, "front_text") ||
                   lines.back().checkDifferentThanSignUpdate(metadata, "back_text");
        } else {
            // <= 1.19.4, only check front lines
            SignSideLines frontLines = this.lines.front();
            if (frontLines.isSameAsSign()) {
                return false;
            }

            for (int i = 0; i < VirtualLines.LINE_COUNT; i++) {
                Line line = frontLines.getLine(i);
                if (!line.sameAsSign && !line.toNBT().equals(metadata.get(SIGN_META_LINE_KEYS_PRE_1_20[i]))) {
                    return true;
                }
            }
        }
        return false;
    }

    public void applyToSignMetadata(CommonTagCompound metadata) {
        if (CommonCapabilities.HAS_SIGN_BACK_TEXT) {
            // >= 1.20
            lines.front().applyLinesToSignUpdate(metadata, "front_text");
            lines.back().applyLinesToSignUpdate(metadata, "back_text");
        } else {
            // <= 1.19.4
            SignSideLines frontLines = this.lines.front();
            if (frontLines.isSameAsSign()) {
                return;
            }

            for (int i = 0; i < VirtualLines.LINE_COUNT; i++) {
                Line line = frontLines.getLine(i);
                if (!line.sameAsSign) {
                    metadata.put(SIGN_META_LINE_KEYS_PRE_1_20[i], line.toNBT());
                }
            }
        }
    }

    @Deprecated
    public void set(int index, String value) {
        set(SignSide.FRONT, index, value);
    }

    @Deprecated
    public String get(int index) {
        return get(SignSide.FRONT, index);
    }

    @Deprecated
    public String[] get() {
        return get(SignSide.FRONT);
    }

    public void set(SignSide side, int index, String value) {
        if (this.lines.side(side).setText(index, value)) {
            this.changed = true;
        }
    }

    public void set(SignSide side, int index, Line line) {
        if (this.lines.side(side).setLine(index, line)) {
            this.changed = true;
        }
    }

    public String get(SignSide side, int index) {
        return lines.side(side).getText(index);
    }

    public String[] get(SignSide side) {
        return lines.side(side).getAllText();
    }

    public boolean hasChanged() {
        return this.changed;
    }

    public void setChanged() {
        setChanged(true);
    }

    public void setChanged(boolean changed) {
        this.changed = changed;
    }

    @Override
    public String toString() {
        String[] frontLines = lines.front().getAllText();
        String[] backLines = lines.back().getAllText();
        return "{front=[" + frontLines[0] + " " + frontLines[1] + " " + frontLines[2] + " " + frontLines[3] + "]" +
                ", back=" + backLines[0] + " " + backLines[1] + " " + backLines[2] + " " + backLines[3] + "]}";
    }

    /**
     * Tracks the text contents of a line, the json representation of it,
     * and whether this text was changed from the text on the physical sign
     */
    public static final class Line {
        public static final Line UNSET = new Line(ChatText.empty(), true);

        public final String text;
        public final boolean sameAsSign;
        private volatile CommonTag cachedNBT;
        private volatile Supplier<CommonTag> toNBTFunc;

        public CommonTag toNBT() {
            CommonTag nbt;
            if ((nbt = cachedNBT) == null) {
                // Extra async guard. Json func is set to null once initialized. So recheck.
                Supplier<CommonTag> toNBTFunc = this.toNBTFunc;
                if ((nbt = cachedNBT) == null) {
                    cachedNBT = nbt = toNBTFunc.get();
                    this.toNBTFunc = null;
                }
            }
            return nbt;
        }

        public Line(ChatText text, boolean sameAsSign) {
            this(text.getMessage(), sameAsSign, text::getNBT);
        }

        public Line(String text, boolean sameAsSign) {
            this(text, sameAsSign, () -> ChatText.fromMessage(text).getNBT());
        }

        private Line(String text, boolean sameAsSign, Supplier<CommonTag> toNBTFunc) {
            this.text = text;
            this.sameAsSign = sameAsSign;
            this.toNBTFunc = toNBTFunc;
        }
    }

    /**
     * Stores 4 lines of text. Also caches an array of strings that represent
     * the same lines, as this return is used for a lot of APIs.
     */
    public static class SignSideLines implements Cloneable {
        // Used for back lines when signs don't support back lines on the running mc version
        public static final SignSideLines UNSUPPORTED = new SignSideLines(
                new Line[] { Line.UNSET, Line.UNSET, Line.UNSET, Line.UNSET }
        ) {
            @Override
            public boolean setLine(int index, Line line) {
                throw new UnsupportedOperationException("Read-only lines constant");
            }

            @Override
            public SignSideLines clone() {
                return this;
            }
        };
        private static final SignTextGetter TEXT_GETTER = createSignTextGetter();

        private final Line[] lines;
        private final String[] textLines;
        private boolean sameAsSign;

        public SignSideLines(Line[] lines) {
            if (lines.length < LINE_COUNT) {
                throw new IllegalArgumentException("Input line count invalid: " + lines.length);
            }
            this.lines = lines;
            this.textLines = new String[lines.length];
            this.sameAsSign = true;
            for (int i = 0; i < lines.length; i++) {
                Line line = lines[i];
                this.sameAsSign &= line.sameAsSign;
                this.textLines[i] = line.text;
            }
        }

        public SignSideLines(String[] textLines) {
            if (textLines.length < LINE_COUNT) {
                throw new IllegalArgumentException("Input line count invalid: " + textLines.length);
            }
            this.lines = new Line[textLines.length];
            for (int i = 0; i < textLines.length; i++) {
                this.lines[i] = new Line(textLines[i], true);
            }
            this.textLines = textLines.clone();
            this.sameAsSign = true;
        }

        private SignSideLines(Line[] lines, String[] textLines, boolean sameAsSign) {
            this.lines = lines;
            this.textLines = textLines.clone();
            this.sameAsSign = sameAsSign;
        }

        public Line getLine(int index) {
            return lines[index];
        }

        public boolean setLine(int index, Line line) {
            Line prevLine = this.lines[index];
            boolean changed = !prevLine.toNBT().equals(line.toNBT());
            this.lines[index] = line;
            this.textLines[index] = line.text;

            if (!line.sameAsSign) {
                this.sameAsSign = false;
            } else if (!prevLine.sameAsSign) {
                this.sameAsSign = true;
                for (Line existingLine : this.lines) {
                    sameAsSign &= existingLine.sameAsSign;
                }
            }

            return changed;
        }

        public String getText(int index) {
            return textLines[index];
        }

        public boolean setText(int index, String text) {
            if (text.equals(this.textLines[index])) {
                return false;
            } else {
                this.lines[index] = new Line(text, false);
                this.textLines[index] = text;
                this.sameAsSign =false;
                return true;
            }
        }

        public String[] getAllText() {
            return textLines;
        }

        /**
         * Whether all the lines shown on this side of the sign are unchanged from the text
         * that is on the sign.
         *
         * @return True if all lines are identical to the text on the sign
         */
        public boolean isSameAsSign() {
            return sameAsSign;
        }

        /**
         * Checks whether these lines differ from the line contents in a sign update packet.
         * Only for 1.20+
         *
         * @param metadata Metadata of the sign
         * @param keyName Key name where the lines are stored
         * @return True if different
         */
        public boolean checkDifferentThanSignUpdate(CommonTagCompound metadata, String keyName) {
            if (isSameAsSign()) {
                return false;
            }

            CommonTagCompound sign_text = metadata.get(keyName, CommonTagCompound.class);
            if (sign_text == null) {
                return false;
            }

            CommonTagList messages = sign_text.get("messages", CommonTagList.class);
            if (messages.size() < VirtualLines.LINE_COUNT) {
                return false; // Bad packet?
            }

            for (int i = 0; i < VirtualLines.LINE_COUNT; i++) {
                Line line = getLine(i);
                if (!line.sameAsSign && !line.toNBT().equals(messages.get(i))) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Applies the lines that changed to a sign update packet's metadata.
         * Only for 1.20+
         *
         * @param metadata Metadata of the sign update packet
         * @param keyName Key name where lines are stored
         */
        public void applyLinesToSignUpdate(CommonTagCompound metadata, String keyName) {
            // Skip even looking at stuff if we have no changes for this sign
            if (isSameAsSign()) {
                return;
            }

            CommonTagCompound sign_text = metadata.createCompound(keyName);
            CommonTagList messages = sign_text.createList("messages");
            if (messages.size() == VirtualLines.LINE_COUNT) {
                // Set lines
                for (int i = 0; i < VirtualLines.LINE_COUNT; i++) {
                    Line line = getLine(i);
                    if (!line.sameAsSign) {
                        messages.set(i, line.toNBT());
                    }
                }
            } else {
                // Clear and re-add
                messages.clear();
                for (int i = 0; i < VirtualLines.LINE_COUNT; i++) {
                    messages.add(getLine(i).toNBT());
                }
            }
        }

        @Override
        public SignSideLines clone() {
            return new SignSideLines(this.lines.clone(), this.textLines.clone(), this.sameAsSign);
        }

        public static SignSideLines getLines(SignChangeTracker tracker, SignSide side) {
            return side.isFront() ? getFrontLines(tracker) : getBackLines(tracker);
        }

        public static SignSideLines getFrontLines(SignChangeTracker tracker) {
            return TEXT_GETTER.getFrontLines(tracker);
        }

        public static SignSideLines getBackLines(SignChangeTracker tracker) {
            return TEXT_GETTER.getBackLines(tracker);
        }

        private static SignTextGetter createSignTextGetter() {
            try {
                return new ModernSignTextGetter();
            } catch (Throwable t) {
                SignLink.plugin.getLogger().log(Level.SEVERE, "Failed to identify suitable sign text getter, using fallback", t);
                return new FallbackSignTextGetter();
            }
        }

        private interface SignTextGetter {
            SignSideLines getFrontLines(SignChangeTracker tracker);
            SignSideLines getBackLines(SignChangeTracker tracker);
        }

        private static class ModernSignTextGetter implements SignTextGetter {

            public static SignSideLines createLines(String[] textLines, ChatText[] formattedLines) {
                Line[] lines = new Line[textLines.length];
                for (int i = 0; i < textLines.length; i++) {
                    final ChatText formattedLine = formattedLines[i];
                    lines[i] = new Line(textLines[i], true, formattedLine::getNBT);
                }
                return new SignSideLines(lines, textLines, true);
            }

            @Override
            public SignSideLines getFrontLines(SignChangeTracker tracker) {
                return createLines(tracker.getFrontLines(), tracker.getFormattedFrontLines());
            }

            @Override
            public SignSideLines getBackLines(SignChangeTracker tracker) {
                if (CommonCapabilities.HAS_SIGN_BACK_TEXT) {
                    return createLines(tracker.getBackLines(), tracker.getFormattedBackLines());
                } else {
                    return UNSUPPORTED;
                }
            }
        }

        private static class FallbackSignTextGetter implements SignTextGetter {
            @Override
            public SignSideLines getFrontLines(SignChangeTracker tracker) {
                return new SignSideLines(tracker.getFrontLines());
            }

            @Override
            public SignSideLines getBackLines(SignChangeTracker tracker) {
                if (CommonCapabilities.HAS_SIGN_BACK_TEXT) {
                    return new SignSideLines(tracker.getBackLines());
                } else {
                    return UNSUPPORTED;
                }
            }
        }
    }
}
