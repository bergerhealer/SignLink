package com.bergerkiller.bukkit.sl.impl.format;

import com.bergerkiller.bukkit.sl.impl.VariableValueMap;

/**
 * Listener for changes in the declared variables in
 * a format string
 */
public interface FormatChangeListener {

    /**
     * Called when a new variable is declared that was not before
     *
     * @param entry Entry of the variable that was declared
     */
    void onVariableDeclared(VariableValueMap.Entry entry);

    /**
     * Called when a variable that was declared, is no longer declared
     *
     * @param entry Entry of the variable that is not longer declared
     */
    void onVariableUndeclared(VariableValueMap.Entry entry);

    /**
     * Detects the changes in declared variables between an old and new formatted
     * variable value. For every change, the listener callback is called.
     *
     * @param oldValue Old value, use null to only declare new variables
     * @param newValue New value, use null to only undeclare old variables
     * @param listener The listener to call callbacks on
     * @return True if there were changes, false if not
     */
    public static boolean detectChanges(
            final FormattedVariableValue oldValue,
            final FormattedVariableValue newValue,
            final FormatChangeListener listener
    ) {
        // Check identical
        if (oldValue == newValue) {
            return false;
        }

        final FormattedVariableValue.DeclaredVariable oldVars = (oldValue == null) ? null : oldValue.getVariables();
        final FormattedVariableValue.DeclaredVariable newVars = (newValue == null) ? null : newValue.getVariables();

        // First verify both lists of variables have elements
        if (oldVars == null) {
            if (newVars != null) {
                // New has variables, so they were all added
                FormattedVariableValue.DeclaredVariable curr;
                for (curr = newVars; curr != null; curr = curr.next()) {
                    listener.onVariableDeclared(curr.getEntry());
                }
                return true;
            } else {
                // Both empty, no changes
                return false;
            }
        } else if (newVars == null) {
            // Only has old variables, so they were all removed
            FormattedVariableValue.DeclaredVariable curr;
            for (curr = oldVars; curr != null; curr = curr.next()) {
                listener.onVariableUndeclared(curr.getEntry());
            }
            return true;
        }

        // Efficient first pass: check if exactly equal in same order
        // The oldVars and newVars are guaranteed to be non-null by the earlier check
        // If we find two entries that are different, we must do a complicated
        // contains check further down below.
        {
            FormattedVariableValue.DeclaredVariable currOld = oldVars, currNew = newVars;
            while (currOld.getEntry() == currNew.getEntry()) {
                currOld = currOld.next();
                currNew = currNew.next();
                if (currOld == null) {
                    if (currNew == null) {
                        // Both reached end of the list at the same time
                        // Exactly the same variables are declared in both
                        return false;
                    } else {
                        // End of the old list reached, all other variables are new
                        do {
                            listener.onVariableDeclared(currNew.getEntry());
                        } while ((currNew = currNew.next()) != null);
                        return true;
                    }
                } else if (currNew == null) {
                    // End of the new list reached, all other variables were removed
                    do {
                        listener.onVariableUndeclared(currOld.getEntry());
                    } while ((currOld = currOld.next()) != null);
                    return true;
                }
            }
        }

        // Check for every element in the old list that it exists in the
        // new list and vice-versa. If we find differences, report them.
        boolean hasChanges = false;

        FormattedVariableValue.DeclaredVariable currOld;
        for (currOld = oldVars; currOld != null; currOld = currOld.next()) {
            VariableValueMap.Entry oldEntry = currOld.getEntry();
            if (!newVars.find(oldEntry)) {
                listener.onVariableUndeclared(oldEntry);
                hasChanges = true;
            }
        }

        FormattedVariableValue.DeclaredVariable currNew;
        for (currNew = newVars; currNew != null; currNew = currNew.next()) {
            VariableValueMap.Entry newEntry = currNew.getEntry();
            if (!oldVars.find(newEntry)) {
                listener.onVariableDeclared(newEntry);
                hasChanges = true;
            }
        }

        return hasChanges;
    }
}
