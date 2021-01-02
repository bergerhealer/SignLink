package com.bergerkiller.bukkit.sl.impl.format;

import com.bergerkiller.bukkit.sl.impl.VariableMap;
import com.bergerkiller.bukkit.sl.impl.VariableValueMap;
import com.bergerkiller.bukkit.sl.impl.VariableValueMap.Entry;

/**
 * Stores the sequence of tokens making up the text value of a variable.
 * This can be just one token, or multiple if escaped %-signs or variables
 * that are embedded inside. All this information is cached for efficient
 * formatting of the variable text value displayed to players.<br>
 * <br>
 * Information about what variables are declared is made accessible, too.
 */
public class FormattedVariableValue {
    private final Token root;
    private final VariableToken firstVariable;
    private final String format;
    private int lastLength;

    private FormattedVariableValue(String variableName) {
        this.root = new ConstantToken("%" + variableName + "%");
        this.firstVariable = null;
        this.format = "%%" + variableName + "%%";
        this.lastLength = variableName.length() + 2;
    }

    private FormattedVariableValue(Token root, VariableToken firstVariable, String format, int lastLength) {
        this.root = root;
        this.firstVariable = firstVariable;
        this.format = format;
        this.lastLength = lastLength;
    }

    /**
     * Gets the variable value format that is currently set
     *
     * @return format String
     */
    public String getFormat() {
        return this.format;
    }

    /**
     * Checks whether this formatted value uses the given variable entry
     * in its display
     *
     * @param entry The entry to check
     * @return True if the entry is used for display, False if not
     */
    public boolean usesVariable(VariableValueMap.Entry entry) {
        return this.firstVariable != null && this.firstVariable.find(entry);
    }

    /**
     * Gets a linked list of variables declared in the format. May return
     * null if no variables are declared. Use {@link DeclaredVariable#next()}
     * to iterate all the entries.
     *
     * @return linked list of declared variables
     */
    public DeclaredVariable getVariables() {
        return this.firstVariable;
    }

    /**
     * Clones this formatted variable value specifically for the display
     * to a given player. Returns the same instance if it finds that
     * the variables that are declared and used stay identical. This is
     * the case when cloning for the same player, or when only the default
     * values are used in the variable.
     *
     * @param owner Owner entry this variable value is stored for
     * @return clone specifically for that player, or the same instance
     *         if no or identical variable entries are used.
     */
    public FormattedVariableValue cloneFor(VariableValueMap.Entry owner) {
        // If no variables are used, there is no reason to even clone at all
        if (this.firstVariable == null) {
            return this;
        }

        // Rebuild with possibly new variable entries
        Builder builder = new Builder(owner);
        for (Token curr = this.root; curr != null; curr = curr.next) {
            builder.onClone(curr);
        }

        // Check variables have changed at all. If not, don't keep the clone.
        if (DeclaredVariable.isIdentical(this.firstVariable, builder.rootVariable.nextVariable)) {
            return this;
        }

        // Different, create a new instance
        return builder.build(this.format);
    }

    /**
     * Calculates an up-to-date displayed text representation of
     * the format set
     *
     * @return text
     */
    public String computeText() {
        StringBuilder builder = new StringBuilder(this.lastLength + 10);
        for (Token curr = root; curr != null; curr = curr.next) {
            builder.append(curr.getText());
        }
        this.lastLength = builder.length();
        return builder.toString();
    }

    @Override
    public String toString() {
        return this.getFormat();
    }

    /**
     * Creates the default value for a variable, which is used before
     * the variable is assigned a new value
     *
     * @param variableName
     * @return default variable value
     */
    public static FormattedVariableValue createDefaultValue(String variableName) {
        return new FormattedVariableValue(variableName);
    }

    /**
     * Decodes a format text String for the variable value owner specified
     *
     * @param owner The owner of the value, which sets what player-specific text
     *              values to use in place of variable names
     * @param format Input format to decode
     * @return formatted variable value
     */
    public static FormattedVariableValue decode(VariableValueMap.Entry owner, String format) {
        Builder builder = new Builder(owner);
        builder.match(format);
        return builder.build(format);
    }

    private static abstract class Token {
        public Token next = null;

        /**
         * Gets the text displayed by this token
         *
         * @return text
         */
        public abstract String getText();

        /**
         * Creates a new clone of this token, but for
         * a different player
         *
         * @param playerName name of the Player, all-lowercase
         * @return token clone
         */
        public abstract Token cloneForPlayer(String playerName);
    }

    // Displays a constant snippet of text
    private static final class ConstantToken extends Token {
        public final String text;

        public ConstantToken(String text) {
            this.text = text;
        }

        @Override
        public String getText() {
            return this.text;
        }

        @Override
        public Token cloneForPlayer(String playerName) {
            return new ConstantToken(this.text);
        }
    }

    /**
     * A variable declared in the format. Forms a linked list
     * of unique variable names.
     */
    public static interface DeclaredVariable {
        /**
         * Gets the next declared variable in the linked list.
         * If the end is reached, null is returned.
         *
         * @return next declared variable, or null if the end
         *         of the list is reached.
         */
        DeclaredVariable next();

        /**
         * Gets the entry used to display the variable value
         *
         * @return entry
         */
        VariableValueMap.Entry getEntry();

        /**
         * Attempts to find an entry in this linked list
         *
         * @param entry Entry to find
         * @return True if found, False otherwise
         */
        boolean find(VariableValueMap.Entry entry);

        /**
         * Gets whether this linked list of variables is
         * identical to another. That is, they both declare
         * the same variable entries in the same order.
         *
         * @param a Linked list one
         * @param b Linked list two
         * @return True if both linked lists are identical
         */
        public static boolean isIdentical(final DeclaredVariable a, final DeclaredVariable b) {
            DeclaredVariable curr_a = a;
            DeclaredVariable curr_b = b;
            while (true) {
                // Check end of list
                if (curr_a == null) {
                    return (curr_b == null);
                } else if (curr_b == null) {
                    return false;
                }

                // Check identical
                if (curr_a.getEntry() != curr_b.getEntry()) {
                    return false;
                }

                // Next
                curr_a = curr_a.next();
                curr_b = curr_b.next();
            }
        }
    }

    // Displays the value of a variable
    private static final class VariableToken extends Token implements DeclaredVariable {
        public final VariableValueMap.Entry entry;
        public VariableToken nextVariable = null;

        public VariableToken(VariableValueMap.Entry entry) {
            this.entry = entry;
        }

        @Override
        public String getText() {
            return this.entry.getText();
        }

        @Override
        public Token cloneForPlayer(String playerName) {
            VariableValueMap.Entry newEntry;
            if (playerName != null && playerName.equals(this.entry.playerName)) {
                newEntry = this.entry;
            } else {
                newEntry = this.entry.getValueMap().getPlayerEntryOrDefault(playerName);
            }
            return new VariableToken(newEntry);
        }

        @Override
        public DeclaredVariable next() {
            return this.nextVariable;
        }

        @Override
        public Entry getEntry() {
            return this.entry;
        }

        @Override
        public boolean find(Entry entry) {
            VariableToken curr = this;
            do {
                if (curr.entry == entry) {
                    return true;
                }
            } while ((curr = curr.nextVariable) != null);

            return false;
        }
    }

    // Builds the entries by parsing the input format String (or other means),
    // then creates a FormattedVariableValue or updates an existing one.
    private static final class Builder extends FormatMatcher {
        private final VariableValueMap.Entry owner;
        private final VariableMap variables;
        private final Token root = new ConstantToken("");
        private final VariableToken rootVariable = new VariableToken(null);
        private Token current = root;
        private VariableToken currentVariable = rootVariable;

        public Builder(VariableValueMap.Entry owner) {
            this.owner = owner;
            this.variables = owner.getVariable().getVariableMap();
        }

        public FormattedVariableValue build(String format) {
            return new FormattedVariableValue(this.root.next, this.rootVariable.nextVariable,
                    format, format.length());
        }

        public void onClone(Token token) {
            store(token.cloneForPlayer(this.owner.playerName));
        }

        @Override
        public void onTextConstant(String constant) {
            store(new ConstantToken(constant));
        }

        @Override
        public void onVariable(String variableName) {
            VariableValueMap valueMap = variables.get(variableName).getValueMap();
            VariableValueMap.Entry entry = valueMap.getPlayerEntryOrDefault(this.owner.playerName);
            store(new VariableToken(entry));
        }

        private void store(Token token) {
            current.next = token;
            current = token;

            if (token instanceof VariableToken) {
                VariableToken variable = (VariableToken) token;
                if (rootVariable.nextVariable == null || !rootVariable.nextVariable.find(variable.entry)) {
                    currentVariable.nextVariable = variable;
                    currentVariable = variable;
                }
            }
        }
    }
}
