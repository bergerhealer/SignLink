package com.bergerkiller.bukkit.sl;

import com.bergerkiller.bukkit.common.block.SignSide;
import com.bergerkiller.bukkit.common.block.SignSideMap;
import com.bergerkiller.bukkit.common.internal.CommonCapabilities;
import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;
import com.bergerkiller.bukkit.common.nbt.CommonTagList;
import com.bergerkiller.bukkit.common.wrappers.ChatText;

import java.util.Arrays;

public class VirtualLines {
    private static final String[] SIGN_META_LINE_KEYS_PRE_1_20 = new String[] { "Text1", "Text2", "Text3", "Text4" };
    public static final String[] DEFAULT_EMPTY_SIGN_LINES = new String[] { "", "", "", "" };
    public static final int LINE_WIDTH_LIMIT = 90;
    public static final int LINE_COUNT = 4;
    private final SignSideMap<String[]> lines = new SignSideMap<>();
    private boolean changed = false;

    public VirtualLines(String[] frontLines, String[] backLines) {
        this.lines.setFront(Arrays.copyOf(frontLines, LINE_COUNT));
        if (CommonCapabilities.HAS_SIGN_BACK_TEXT) {
            this.lines.setBack(Arrays.copyOf(backLines, LINE_COUNT));
        } else {
            this.lines.setBack(DEFAULT_EMPTY_SIGN_LINES);
        }
    }

    public boolean isDifferentThanMetadata(CommonTagCompound metadata) {
        if (CommonCapabilities.HAS_SIGN_BACK_TEXT) {
            // >= 1.20
            return checkDifferent(metadata, "front_text", lines.front()) ||
                   checkDifferent(metadata, "back_text", lines.back());
        } else {
            // <= 1.19.4, only check front lines
            for (int i = 0; i < VirtualLines.LINE_COUNT; i++) {
                String text = ChatText.fromJson(metadata.getValue(SIGN_META_LINE_KEYS_PRE_1_20[i], "")).getMessage();
                if (!get(SignSide.FRONT, i).equals(text)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean checkDifferent(CommonTagCompound metadata, String keyName, String[] lines) {
        CommonTagCompound sign_text = metadata.get(keyName, CommonTagCompound.class);
        if (sign_text != null) {
            CommonTagList messages = sign_text.get("messages", CommonTagList.class);
            if (messages.size() == VirtualLines.LINE_COUNT) {
                for (int i = 0; i < VirtualLines.LINE_COUNT; i++) {
                    String text = ChatText.fromJson(messages.getValue(i, "")).getMessage();
                    if (!lines[i].equals(text)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public void applyToSignMetadata(CommonTagCompound metadata) {
        if (CommonCapabilities.HAS_SIGN_BACK_TEXT) {
            // >= 1.20
            applyLines(metadata, "front_text", lines.front());
            applyLines(metadata, "back_text", lines.back());
        } else {
            // <= 1.19.4
            for (int i = 0; i < VirtualLines.LINE_COUNT; i++) {
                String text = ChatText.fromMessage(get(SignSide.FRONT, i)).getJson();
                metadata.putValue(SIGN_META_LINE_KEYS_PRE_1_20[i], text);
            }
        }
    }

    private void applyLines(CommonTagCompound metadata, String keyName, String[] lines) {
        CommonTagCompound sign_text = metadata.createCompound(keyName);
        CommonTagList messages = sign_text.createList("messages");
        if (messages.size() == VirtualLines.LINE_COUNT) {
            // Set lines
            for (int i = 0; i < VirtualLines.LINE_COUNT; i++) {
                String text = ChatText.fromMessage(lines[i]).getJson();
                messages.setValue(i, text);
            }
        } else {
            // Clear and re-add
            messages.clear();
            for (int i = 0; i < VirtualLines.LINE_COUNT; i++) {
                String text = ChatText.fromMessage(lines[i]).getJson();
                messages.addValue(text);
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
        String[] lines = this.lines.side(side);
        if (!lines[index].equals(value)) {
            this.changed = true;
            lines[index] = value;
        }
    }

    public String get(SignSide side, int index) {
        return lines.side(side)[index];
    }

    public String[] get(SignSide side) {
        return lines.side(side);
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
        String[] frontLines = lines.front();
        String[] backLines = lines.back();
        return "{front=[" + frontLines[0] + " " + frontLines[1] + " " + frontLines[2] + " " + frontLines[3] + "]" +
                ", back=" + backLines[0] + " " + backLines[1] + " " + backLines[2] + " " + backLines[3] + "]}";
    }
}
