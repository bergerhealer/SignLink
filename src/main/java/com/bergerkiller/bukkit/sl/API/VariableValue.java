package com.bergerkiller.bukkit.sl.API;

/**
 * Contains the basic variable changing methods for changing a Variable value
 */
public interface VariableValue {
    /**
     * Gets the Variable assigned in the back-end to handle the value changes
     * 
     * @return backing Variable
     */
    Variable getVariable();

    /**
     * Updates the text on all the signs
     * 
     * @deprecated This method does nothing and does not need to be called
     */
    @Deprecated
    void updateAll();

    /**
     * Clears the settings, changing it to the default value
     */
    void clear();

    /**
     * Changes the value, can be cancelled by another plugin
     * 
     * @param newValue to set to
     */
    void set(String newValue);

    /**
     * Gets the current value
     * 
     * @return current value
     */
    String get();

    /**
     * Gets the ticker assigned to change the value on a tick interval
     * 
     * @return Assigned ticker
     */
    Ticker getTicker();
}
