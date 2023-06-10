package com.bergerkiller.bukkit.sl;

import com.bergerkiller.bukkit.common.internal.CommonCapabilities;
import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;
import com.bergerkiller.bukkit.common.nbt.CommonTagList;
import com.bergerkiller.bukkit.common.wrappers.ChatText;

public class VirtualLines {
    //public static final int MAX_LINE_LENGTH = 15; /* Legacy no longer applies */
    private static final String[] SIGN_META_LINE_KEYS_PRE_1_20 = new String[] { "Text1", "Text2", "Text3", "Text4" };
    public static final int LINE_WIDTH_LIMIT = 90;
    public static final int LINE_COUNT = 4;
    private String[] lines = new String[LINE_COUNT];
    private boolean changed = false;

    public VirtualLines(String[] lines) {
//        System.out.println(lines);
//        System.out.println(lines[0]);
//        System.out.println(lines[1]);
//        System.out.println(lines[2]);
//        System.out.println(lines[3]);
        System.arraycopy(lines, 0, this.lines, 0, LINE_COUNT);
    }

    public boolean isDifferentThanMetadata(CommonTagCompound metadata) {
        if (CommonCapabilities.HAS_SIGN_BACK_TEXT) {
            // >= 1.20
            CommonTagCompound front_text = metadata.get("front_text", CommonTagCompound.class);
            if (front_text != null) {
                CommonTagList messages = front_text.get("messages", CommonTagList.class);
                if (messages.size() == VirtualLines.LINE_COUNT) {
                    for (int i = 0; i < VirtualLines.LINE_COUNT; i++) {
                        String text = ChatText.fromJson(messages.getValue(i, "")).getMessage();
                        if (!get(i).equals(text)) {
                            return true;
                        }
                    }
                }
            }
        } else {
            // <= 1.19.4
            for (int i = 0; i < VirtualLines.LINE_COUNT; i++) {
                String text = ChatText.fromJson(metadata.getValue(SIGN_META_LINE_KEYS_PRE_1_20[i], "")).getMessage();
                if (!get(i).equals(text)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void applyToSignMetadata(CommonTagCompound metadata) {
        if (CommonCapabilities.HAS_SIGN_BACK_TEXT) {
            // >= 1.20
            CommonTagCompound front_text = metadata.createCompound("front_text");
            CommonTagList messages = front_text.createList("messages");
            if (messages.size() == VirtualLines.LINE_COUNT) {
                // Set lines
                for (int i = 0; i < VirtualLines.LINE_COUNT; i++) {
                    String text = ChatText.fromMessage(get(i)).getJson();
                    messages.setValue(i, text);
                }
            } else {
                // Clear and re-add
                messages.clear();
                for (int i = 0; i < VirtualLines.LINE_COUNT; i++) {
                    String text = ChatText.fromMessage(get(i)).getJson();
                    messages.addValue(text);
                }
            }
        } else {
            // <= 1.19.4
            for (int i = 0; i < VirtualLines.LINE_COUNT; i++) {
                String text = ChatText.fromMessage(get(i)).getJson();
                metadata.putValue(SIGN_META_LINE_KEYS_PRE_1_20[i], text);
            }
        }
    }

    public void set(int index, String value) {
        if (!this.lines[index].equals(value)) {
            this.changed = true;
            this.lines[index] = value;
        }
    }

    public String get(int index) {
        return lines[index];
    }

    public String[] get() {
        return this.lines;
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
        return "{" + lines[0] + " " + lines[1] + " " + lines[2] + " " + lines[3] + "}";
    }
}
