package com.bergerkiller.bukkit.sl;

import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;
import com.bergerkiller.bukkit.common.wrappers.ChatText;

public class VirtualLines {
    //public static final int MAX_LINE_LENGTH = 15; /* Legacy no longer applies */
    private static final String[] SIGN_META_LINE_KEYS = new String[] { "Text1", "Text2", "Text3", "Text4" };
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
        for (int i = 0; i < VirtualLines.LINE_COUNT; i++) {
            String text = ChatText.fromJson(metadata.getValue(SIGN_META_LINE_KEYS[i], "")).getMessage();
            if (!get(i).equals(text)) {
                return true;
            }
        }
        return false;
    }

    public void applyToSignMetadata(CommonTagCompound metadata) {
        for (int i = 0; i < VirtualLines.LINE_COUNT; i++) {
            String text = ChatText.fromMessage(get(i)).getJson();
            metadata.putValue(SIGN_META_LINE_KEYS[i], text);
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
