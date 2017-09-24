package com.bergerkiller.bukkit.sl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * Object used to build the text displayed on multiple linked signs
 */
public class LinkedText {
    private int line = 0;
    private List<VirtualSign> signs = Collections.emptyList();
    private SignDirection direction = SignDirection.NONE;
    private boolean wrapAround = false;
    private boolean isCentred = false;
    private int[] remainingWidths = new int[0];

    private LinkedList<StyledCharacter> prefixChars;
    private LinkedList<StyledCharacter> postfixChars;
    private LinkedList<StyledCharacter> characters;

    public void setSigns(List<VirtualSign> signs) {
        this.signs = signs;
        if (signs.size() != this.remainingWidths.length) {
            this.remainingWidths = new int[signs.size()];
        }
    }

    public void setDirection(SignDirection direction) {
        this.direction = direction;
    }

    public void setLine(int line) {
        this.line = line;
    }

    public void setWrapAround(boolean wrapAround) {
        this.wrapAround = wrapAround;
    }

    // handles a variable wrapping around itself cyclically to fill the available space fully
    private void handleWrapAround() {
        int remainingSigns = signs.size();

        // Add all current characters to the signs
        int width = 0;
        for (StyledCharacter character : characters) {
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
            ArrayList<StyledCharacter> wrappedChars = new ArrayList<StyledCharacter>(characters);
            int wrappedCharIdx = 0;
            while (true) {
                if (wrappedCharIdx == wrappedChars.size()) {
                    wrappedCharIdx = 0;
                }
                StyledCharacter sc = wrappedChars.get(wrappedCharIdx++);
                width += sc.width;
                if (width > VirtualLines.LINE_WIDTH_LIMIT) {
                    width = sc.width;
                    if (--remainingSigns == 0) {
                        break;
                    }
                }
                characters.addLast(sc);
            }
        }
    }

    // Fill the entire width of the signs with characters to make sure padding is correct
    // Add spaces to the left, right, or alternating depending on sign direction
    private void handleMultiSign() {
        // Add spaces left and/or right of the characters array until we run out
        int leftPadding = StyledCharacter.getTotalWidth(this.prefixChars);
        int rightPadding = StyledCharacter.getTotalWidth(this.postfixChars);
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
                this.characters.addFirst(space_first);
                if (StyledCharacter.getSignCount(this.characters, leftPadding, rightPadding) > signs.size()) {
                    this.characters.removeFirst();
                    break;
                }
            } else {
                this.characters.addLast(space_last);
                if (StyledCharacter.getSignCount(this.characters, leftPadding, rightPadding) > signs.size()) {
                    this.characters.removeLast();
                    break;
                }
            }
        }
    }

    public void generate(String variableValue) {
        // Sometimes signs are iterated in reverse!
        int firstSignIndex = 0;
        int lastSignIndex = signs.size() - 1;
        if (this.direction == SignDirection.LEFT) {
            firstSignIndex = lastSignIndex;
            lastSignIndex = 0;
        }

        // Whether the variable text is centred in the middle
        this.isCentred = (this.direction == SignDirection.NONE);

        // Handle text before/after the variable value on the first sign
        String prefix = "";
        String postfix = "";
        String firstSignRealLine = signs.get(firstSignIndex).getRealLine(this.line);
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
        if (signs.size() > 1) {
            String lastSignRealLine = signs.get(lastSignIndex).getRealLine(this.line);
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

        // Convert text to a list of styled character tokens
        this.prefixChars = StyledCharacter.getChars(prefix);
        this.postfixChars = StyledCharacter.getChars(postfix);

        // Non-empty values
        if (!this.prefixChars.isEmpty()) {
            this.characters = StyledCharacter.getChars(this.prefixChars.getLast(), variableValue);
        } else {
            // Add the value without prefix
            this.characters= StyledCharacter.getChars(variableValue);
        }

        // Empty values: use a single space as a placeholder
        // This makes sure post-processing does not trip
        if (StyledCharacter.getTotalWidth(this.characters) == 0) {
            if (this.prefixChars.isEmpty()) {
                this.characters.add(new StyledCharacter(' '));
            } else {
                this.characters.add(this.prefixChars.getLast().asSpace());
            }
        }

        // Calculate the widths on the signs still available for characters to be displayed
        Arrays.fill(this.remainingWidths, VirtualLines.LINE_WIDTH_LIMIT);
        this.remainingWidths[firstSignIndex] -= StyledCharacter.getTotalWidth(this.prefixChars);
        this.remainingWidths[lastSignIndex] -= StyledCharacter.getTotalWidth(this.postfixChars);

        // Handle special post-processing
        if (this.wrapAround) {
            this.handleWrapAround();
        } else if (signs.size() > 1 || direction != SignDirection.NONE) {
            this.handleMultiSign();
        }
    }

    public void apply(String... forPlayers) {
        // Next, add characters to the sign lines until we run out of space on the sign, then reset and move on
        int signIndex = 0;
        VirtualSign currentSign = signs.get(0);
        LinkedList<StyledCharacter> currentSignStyledLine = new LinkedList<StyledCharacter>();
        ArrayList<StyledCharacter> tmpList = new ArrayList<StyledCharacter>();
        int currentLineWidth = 0;
        Iterator<StyledCharacter> charactersIter = characters.iterator();
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
                if (signIndex == 0 && !prefixChars.isEmpty()) {
                    tmpList.addAll(prefixChars);
                }
                tmpList.addAll(currentSignStyledLine);
                if (signIndex == signs.size() - 1 && !postfixChars.isEmpty()) {
                    tmpList.addAll(postfixChars);
                }
                currentSign.setLine(this.line, StyledCharacter.getText(tmpList), forPlayers);
                currentSignStyledLine.clear();
                tmpList.clear();

                // End of characters
                if (!hasNext) {
                    break;
                }

                // Next sign
                if (++signIndex >= signs.size()) {
                    break; // done! No more sign space.
                } else {
                    currentSign = signs.get(signIndex);
                }
            }

            // Add the new character
            if (hasNext) {
                currentSignStyledLine.addLast(sc);
            } else {
                break;
            }
        }
    }
}
