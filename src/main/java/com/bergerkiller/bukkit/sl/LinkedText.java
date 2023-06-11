package com.bergerkiller.bukkit.sl;

import com.bergerkiller.bukkit.common.block.SignSide;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Object used to build the text displayed on multiple linked signs
 */
public class LinkedText {
    private int signCount = 0;
    private List<VirtualSign> signs = Collections.emptyList();
    private SignDirection direction = SignDirection.NONE;
    private final SignSide side;
    private final int line;
    private boolean wrapAround = false;
    private boolean isCentred = false;
    private int[] remainingWidths = new int[0];
    private StyledString[] parts = new StyledString[0];
    private final StyledString tmpString = new StyledString();

    private final StyledString prefixChars = new StyledString();
    private final StyledString postfixChars = new StyledString();
    private final StyledString characters = new StyledString();

    public LinkedText(SignSide side, int line) {
        this.side = side;
        this.line = line;
    }

    public SignSide getSide() {
        return side;
    }

    public void setSigns(List<VirtualSign> signs) {
        this.signs = signs;
        if (this.signCount != signs.size()) {
            this.signCount = signs.size();
            this.remainingWidths = new int[signs.size()];
            this.parts = new StyledString[signs.size()];
            for (int i = 0; i < this.parts.length; i++) {
                this.parts[i] = new StyledString();
            }
        }
    }

    public void setDirection(SignDirection direction) {
        this.direction = direction;
    }

    public void setWrapAround(boolean wrapAround) {
        this.wrapAround = wrapAround;
    }

    // handles a variable wrapping around itself cyclically to fill the available space fully
    private void handleWrapAround() {
        int remainingSigns = this.signCount;

        // Add all current characters to the signs
        int width = 0;
        for (StyledCharacter character : this.characters) {
            width += character.width;
            if (width > VirtualLines.LINE_WIDTH_LIMIT) {
                width = character.width;
                remainingSigns--;
                if (remainingSigns == 0) {
                    width -= character.width;
                    break;
                }
            }
        }

        // Fill remaining space by wrapping around the characters
        if (remainingSigns > 0) {
            this.tmpString.clear();
            this.tmpString.addAll(this.characters);
            int wrappedCharIdx = 0;
            while (true) {
                if (wrappedCharIdx == this.tmpString.size()) {
                    wrappedCharIdx = 0;
                }
                StyledCharacter sc = this.tmpString.get(wrappedCharIdx++);
                width += sc.width;
                if (width > VirtualLines.LINE_WIDTH_LIMIT) {
                    width = sc.width;
                    if (--remainingSigns == 0) {
                        break;
                    }
                }
                this.characters.add(sc);
            }
        }
    }

    // Fill the entire width of the signs with characters to make sure padding is correct
    // Add spaces to the left, right, or alternating depending on sign direction
    private void handleMultiSign() {
        // Add spaces left and/or right of the characters array until we run out
        int leftPadding = this.prefixChars.getTotalWidth();
        int rightPadding = this.postfixChars.getTotalWidth();
        StyledCharacter space_first = this.characters.getFirst().asSpace();
        StyledCharacter space_last = this.characters.getLast().asSpace();
        SignDirection space_dir = this.direction;
        boolean alternator = false;
        while (true) {
            if (this.isCentred) {
                alternator = !alternator;
                space_dir = alternator ? SignDirection.LEFT : SignDirection.RIGHT;
            }
            if (space_dir == SignDirection.LEFT) {
                this.characters.add(0, space_first);
                if (this.characters.getSignCount(leftPadding, rightPadding) > this.signCount) {
                    this.characters.remove(0);
                    break;
                }
            } else {
                this.characters.add(space_last);
                if (this.characters.getSignCount(leftPadding, rightPadding) > this.signCount) {
                    this.characters.remove(this.characters.size() - 1);
                    break;
                }
            }
        }
    }

    // takes the prefix, postfix and value characters and combines them into parts displayed on each sign
    private void createParts() {
        // Clear old parts
        for (StyledString part : this.parts) {
            part.clear();
        }

        // This temporary buffer is used while building the String
        this.tmpString.clear();

        // Next, add characters to the sign lines until we run out of space on the sign, then reset and move on
        int signIndex = 0;
        int currentLineWidth = 0;
        Iterator<StyledCharacter> charactersIter = this.characters.iterator();
        while (true) {
            boolean hasNext = charactersIter.hasNext();
            StyledCharacter sc = hasNext ? charactersIter.next() : null;
            if (hasNext) {
                currentLineWidth += sc.width;
            }

            // Try to add the character width to the next sign until it is full
            if (currentLineWidth > remainingWidths[signIndex] || !hasNext) {
                // Limit exceeded, this sign can not add this new character
                // Reset the width to only include the new character
                currentLineWidth = hasNext ? sc.width : 0;

                // Compile all styled characters found thus far into a single String-formatted line
                // Handle prefix and postfix as well
                StyledString part = this.parts[signIndex];
                if (signIndex == 0 && !prefixChars.isEmpty()) {
                    part.addAll(prefixChars);
                }
                part.addAll(this.tmpString);
                this.tmpString.clear();
                if (signIndex == this.signCount - 1 && !postfixChars.isEmpty()) {
                    part.addAll(postfixChars);
                }

                // End of characters
                if (!hasNext) {
                    break;
                }

                // Next sign
                if (++signIndex >= this.signCount) {
                    break; // done! No more sign space.
                }
            }

            // Add the new character
            if (hasNext) {
                this.tmpString.add(sc);
            } else {
                break;
            }
        }
    }

    /**
     * Generates the text displayed on the signs
     * 
     * @param variableValue to display
     */
    public void generate(String variableValue) {
        // Sometimes signs are iterated in reverse!
        int firstSignIndex = 0;
        int lastSignIndex = this.signCount - 1;
        if (this.direction == SignDirection.LEFT) {
            firstSignIndex = lastSignIndex;
            lastSignIndex = 0;
        }

        // Whether the variable text is centred in the middle
        this.isCentred = (this.direction == SignDirection.NONE);

        // Handle text before/after the variable value on the first sign
        String prefix = "";
        String postfix = "";
        String firstSignRealLine = signs.get(firstSignIndex).getRealLine(side, this.line);
        int index1 = firstSignRealLine.indexOf('%');
        int index2 = firstSignRealLine.lastIndexOf('%');
        if ((index2 - index1) == 1) {
            // %% centers the text
            this.isCentred = true;
            if (this.direction == SignDirection.LEFT) {
                index1 = index2;
            } else {
                index2 = index1;
            }
        }
        if (index1 == -1) {
            // Variable was not found? Something might be wrong. But we continue.
            // value = value;
        } else if (index1 == index2) {
            // Open on one side; which side? Resolve using our direction.
            if (this.direction == SignDirection.LEFT) {
                postfix = firstSignRealLine.substring(index1 + 1);
            } else {
                prefix = firstSignRealLine.substring(0, index1);
            }
        } else {
            // Closed both sides
            prefix = firstSignRealLine.substring(0, index1);
            postfix = firstSignRealLine.substring(index2 + 1);
        }

        // Handle multi-sign display so that text on the last sign is appended correctly
        if (this.signCount > 1) {
            String lastSignRealLine = signs.get(lastSignIndex).getRealLine(this.side, this.line);
            index1 = lastSignRealLine.indexOf('%');
            index2 = lastSignRealLine.lastIndexOf('%');
            if ((index2 - index1) == 1) {
                // %% centers the text
                this.isCentred = true;
                if (this.direction == SignDirection.RIGHT) {
                    index1 = index2;
                } else {
                    index2 = index1;
                }
            }

            if (index1 == -1) {
                // Variable was not found? Something might be wrong. But we continue.
                // value = value;
            } else if (index1 == index2) {
                // Open on one side; which side? Resolve using our direction.
                if (this.direction == SignDirection.RIGHT) {
                    postfix = lastSignRealLine.substring(index1 + 1);
                } else {
                    prefix = lastSignRealLine.substring(0, index1);
                }
            } else {
                // Shouldn't happen
                postfix = lastSignRealLine.substring(index2 + 1);
            }
        }

        // Convert text to StyledString
        this.prefixChars.setTo(prefix);
        this.characters.setStartStyle(this.prefixChars.getEndStyle());
        this.characters.setTo(variableValue);
        this.postfixChars.setStartStyle(this.characters.getEndStyle());
        this.postfixChars.setTo(postfix);

        // Empty values: use a single space as a placeholder
        // This makes sure post-processing does not trip
        if (this.characters.getTotalWidth() == 0) {
            if (this.prefixChars.isEmpty()) {
                this.characters.add(new StyledCharacter(' '));
            } else {
                this.characters.add(this.prefixChars.getLast().asSpace());
            }
        }

        // Calculate the widths on the signs still available for characters to be displayed
        Arrays.fill(this.remainingWidths, VirtualLines.LINE_WIDTH_LIMIT);
        this.remainingWidths[firstSignIndex] -= this.prefixChars.getTotalWidth();
        this.remainingWidths[lastSignIndex] -= this.postfixChars.getTotalWidth();

        // Handle special post-processing
        if (this.wrapAround) {
            this.handleWrapAround();
        } else if (this.signCount > 1 || direction != SignDirection.NONE) {
            this.handleMultiSign();
        }

        // Create the parts displayed on each sign
        this.createParts();
    }

    /**
     * Applies the generated results to the signs
     * 
     * @param forPlayerFilter Filters what players to apply this linked text to
     */
    public void apply(VariableTextPlayerFilter forPlayerFilter) {
        for (int i = 0; i < this.parts.length; i++) {
            this.signs.get(i).setLine(this.side, this.line, this.parts[i].toString(), forPlayerFilter);
        }
    }
}
