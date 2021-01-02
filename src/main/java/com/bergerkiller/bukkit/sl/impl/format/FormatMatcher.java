package com.bergerkiller.bukkit.sl.impl.format;

/**
 * Decodes a format input String and calls the callbacks
 * with every token discovered
 */
public abstract class FormatMatcher {

    /**
     * Called when a text constant is encountered
     *
     * @param constant Text constant
     */
    public abstract void onTextConstant(String constant);

    /**
     * Called when a variable value is encountered
     *
     * @param variableName Name of the variable
     */
    public abstract void onVariable(String variableName);

    /**
     * Decodes the input format and calls the callback methods
     * with every element found within
     *
     * @param format
     */
    public void match(String format) {
        // Shortcut
        int start = format.indexOf('%');
        int len = format.length();
        if (start == -1 || start >= (len-1)) {
            this.onTextConstant(format);
            return;
        }

        // If the character following it is also %, go to matchAndModify
        // right away. (Optimization)
        if (format.charAt(start + 1) == '%') {
            StringBuilder builder = new StringBuilder(len-1);
            builder.append(format, 0, start);
            builder.append(format, start + 1, len);
            this.matchAndModify(builder, 1);
            return;
        }

        // Analyze the text that follows, to see if it is a variable
        // If we find a non-variable character, then we ignore this %
        int curr = start;
        int matched_pos = 0;
        while (true) {
            // End of the text, all that remains is just text
            if (++curr >= len) {
                if (matched_pos <= len) {
                    this.onTextConstant(format.substring(matched_pos));
                }
                return;
            }

            char c = format.charAt(curr);
            boolean findNext = false;
            if (c == '%') {
                if (curr == (start+1)) {
                    // Escaped sequence, requires builder to modify things
                    StringBuilder builder = new StringBuilder();
                    builder.append(format, matched_pos, start);
                    builder.append(format, curr, len);
                    this.matchAndModify(builder, 1);
                    return;
                } else {
                    // Constant before variable
                    if (start > matched_pos) {
                        this.onTextConstant(format.substring(matched_pos, start));
                    }

                    // Variable
                    this.onVariable(format.substring(start + 1, curr));

                    // Try to find the next %-sign efficiently, unless end of text
                    if (++curr >= len) {
                        return;
                    }
                    matched_pos = curr;
                    findNext = true;
                }
            } else if (!isValidVariableNameChar(c)) {
                // Not a variable after all, find a possible other start
                findNext = true;
            }

            // Find the next following %-sign, if it doesn't exist, we're done
            if (findNext) {
                if ((start = format.indexOf('%', curr + 1)) == -1) {
                    if (matched_pos <= len) {
                        this.onTextConstant(format.substring(matched_pos));
                    }
                    return;
                }

                curr = start;
            }
        }
    }

    /**
     * Matches text constants and variable expressions in a StringBuilder
     * holding the format, and un-escapes double-% escaped parts of the format
     *
     * @param format Format StringBuilder to process
     */
    public void matchAndUnescape(StringBuilder format) {
        if (format.length() == 0) {
            this.onTextConstant("");
        } else {
            this.matchAndModify(format, 0);
        }
    }

    /**
     * This is called by match() to unescape double-% signs
     * while reading the format
     *
     * @param format Builder
     * @param searchStart Search for a %-character from this position
     */
    private void matchAndModify(StringBuilder format, int searchStart) {
        int len = format.length();
        if (len == 0) {
            return;
        }

        int curr = searchStart;
        int matched_pos = 0;
        while (true) {
            // Find next potential start of a variable really quickly
            // If we encounter the end of the string, then we complete
            // what we got so far in the builder as a constant.
            int start;
            while (true) {
                if (curr >= len) {
                    this.onTextConstant(format.substring(matched_pos));
                    return;
                } else if (format.charAt(curr) == '%') {
                    start = curr;

                    // % at the very end of the format
                    if (++curr >= len) {
                        continue;
                    }

                    char c = format.charAt(curr);
                    if (c == '%') {
                        // Escaped, delete second % from builder
                        format.deleteCharAt(curr);
                        len--;
                        continue;
                    } else if (!isValidVariableNameChar(c)) {
                        // Character after makes it not possible to be the
                        // start of a new variable
                        continue;
                    } else {
                        // Start search for the next variable
                        // Skip the character we know is part of the name
                        ++curr;
                        break;
                    }
                } else {
                    ++curr;
                }
            }

            // Look for the closing %-character. If we run into an invalid
            // character along the way, consider the text to be constant instead
            while (true) {
                if (curr >= len) {
                    this.onTextConstant(format.substring(matched_pos));
                    return;
                }

                char c = format.charAt(curr);
                if (c == '%') {
                    // Variable found! Match text prior, the variable, then look
                    // for more variable start positions
                    if (start > matched_pos) {
                        this.onTextConstant(format.substring(matched_pos, start));
                    }
                    this.onVariable(format.substring(start + 1, curr));
                    if (++curr >= len) {
                        return;
                    }

                    matched_pos = curr;
                    break;
                } else if (!isValidVariableNameChar(c)) {
                    // Can't be a variable, treat it as a constant and look for more
                    // variable start positions
                    ++curr;
                    break;
                } else {
                    // Next character part of the variable name
                    ++curr;
                }
            }
        }
    }

    
    
    private static boolean isValidVariableNameChar(char c) {
        return c != ' ';
    }
}
