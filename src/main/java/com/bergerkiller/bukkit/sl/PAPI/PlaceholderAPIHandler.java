package com.bergerkiller.bukkit.sl.PAPI;

import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.sl.API.Variable;

public interface PlaceholderAPIHandler {

    void enable();

    void disable();

    void setShowOnSigns(boolean showOnSigns);

    /**
     * Refreshes the PAPI variables displayed to a particular Player
     */
    void refreshVariables(Player player);

    void refreshVariableForAll(Variable var);

    /**
     * Checks whether a particular variable is a potential PlaceholderAPI hooked variable name
     * 
     * @param variableName to check
     * @return True if a PAPI variable
     */
    boolean isHookedVariable(String variableName);

    /**
     * Obtains the value of a variable according to PlaceholderAPI.
     * If the variable is not in the PAPI format or does not exist, null is returned.
     * 
     * @param player to get the value of the variable for
     * @param variableName to get (identifier_varname format)
     * @return PlaceholderAPI variable name, null if not managed by PAPI
     */
    String getVariableValue(Player player, String variableName);
}
