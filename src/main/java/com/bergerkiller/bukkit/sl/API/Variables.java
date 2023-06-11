package com.bergerkiller.bukkit.sl.API;

import java.util.List;

import com.bergerkiller.bukkit.common.block.SignSide;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import com.bergerkiller.bukkit.common.collections.ImplicitlySharedSet;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.sl.LinkedSign;
import com.bergerkiller.bukkit.sl.VirtualSign;
import com.bergerkiller.bukkit.sl.impl.VariableImpl;
import com.bergerkiller.bukkit.sl.impl.VariableMap;

/**
 * Stores all Variables available
 */
public class Variables {

    /**
     * Updates all the tickers of all the Variables on the server
     */
    public static synchronized void updateTickers() {
        VariableMap.INSTANCE.updateTickers();
    }

    /**
     * Gets a current copy of all the variables on the server.
     * Use java 8's try-with-resources idiom to use this collection
     * to prevent unneeded copying. Example:<br>
     * <pre> 
     * try (ImplicitlySharedSet&lt;Variable&gt; tmp = Variables.all()) {
     *     // Do stuff with the temporary copy
     *     for (Variable var : tmp) {
     *         System.out.println(var.getName());
     *     }
     * }
     * </pre>
     * 
     * @return Collection of all variables
     */
    public static ImplicitlySharedSet<Variable> all() {
        return CommonUtil.unsafeCast(VariableMap.INSTANCE.all());
    }

    /**
     * Updates the sign block orders of all signs showing variables
     */
    public static void updateSignOrder() {
        VariableMap.INSTANCE.forAll(VariableImpl::updateSignOrder);
    }

    /**
     * Updates the sign block orders of all signs showing variables on a given world
     * 
     * @param world to update
     */
    public static void updateSignOrder(final World world) {
        VariableMap.INSTANCE.forAll(var -> var.updateSignOrder(world));
    }

    /**
     * Updates the sign block orders of all signs showing variables near a block
     * 
     * @param near
     */
    public static void updateSignOrder(final Block near) {
        VariableMap.INSTANCE.forAll(var -> var.updateSignOrder(near));
    }

    /**
     * Gets a new mutual list of all variables available
     * 
     * @return Variables
     */
    public static List<Variable> getAllAsList() {
        return VariableMap.INSTANCE.getAllAsList();
    }

    /**
     * Gets an array of all variables available
     * 
     * @return Variables
     */
    public static Variable[] getAll() {
        return VariableMap.INSTANCE.getAll();
    }

    /**
     * Gets an array of all variable names
     * 
     * @return Variable names
     */
    public static String[] getNames() {
        return VariableMap.INSTANCE.getNames();
    }

    /**
     * Gets or creates a variable of the given name
     * 
     * @param name of the variable
     * @return the Variable, or null if the name is of an unsupported format
     */
    public static Variable get(String name) {
        return VariableMap.INSTANCE.get(name);
    }

    /**
     * Gets a variable of the given name. Returns null if it does not exist.
     * 
     * @param name of the variable
     * @return the Variable
     */
    public static Variable getIfExists(String name) {
        return VariableMap.INSTANCE.getIfExists(name);
    }

    @Deprecated
    public static Variable get(VirtualSign sign, int line) {
        return get(sign, SignSide.FRONT, line);
    }

    /**
     * Gets the variable displayed on a line on a sign
     * 
     * @param sign on which the variable can be found
     * @param side of the sign where the variable can be found
     * @param line on which the variable can be found
     * @return The Variable, or null if there is none
     */
    public static Variable get(VirtualSign sign, SignSide side, int line) {
        return VariableMap.INSTANCE.get(parseVariableName(sign.getRealLine(side, line)));
    }

    /**
     * Gets the variable displayed on a line on a sign
     * 
     * @param signblock on which the variable can be found
     * @param line on which the variable can be found
     * @return The Variable, or null if there is none
     */
    public static Variable get(Block signblock, int line) {
        if (MaterialUtil.ISSIGN.get(signblock)) {
            return get(VirtualSign.getOrCreate(signblock), line);
        }
        return null;
    }

    /**
     * Removes (and thus clears) a Variable from the server
     * 
     * @param name of the Variable to remove
     * @return True if the variable was removed, False if it was not found
     */
    public static boolean remove(String name) {
        return VariableMap.INSTANCE.remove(name);
    }

    /**
     * Removes a sign from all variables
     * 
     * @param signblock to remove
     * @return True if the sign was found and removed, False if not
     */
    public static boolean removeLocation(Block signblock) {
        return VirtualSign.remove(signblock);
    }

    /**
     * Attempts to find all linked signs and variables for a given Block.
     * The amount of signs and variables returned are the same, and match each other.
     * Multiple variables could be contained in the List.
     * The Linked Sign at signs index X shows the Variable at variables index X.
     * 
     * @param signs to fill with results (null to ignore)
     * @param variables to fill with results (null to ignore)
     * @param at Block to find
     * @return True if something was found, False if not
     */
    public static boolean find(List<LinkedSign> signs, List<Variable> variables, Block at) {
        return VariableMap.INSTANCE.find(signs, CommonUtil.unsafeCast(variables), at);
    }

    /**
     * Attempts to find all linked signs and variables for a given Block.
     * The amount of signs and variables returned are the same, and match each other.
     * Multiple variables could be contained in the List.
     * The Linked Sign at signs index X shows the Variable at variables index X.
     * 
     * @param signs to fill with results (null to ignore)
     * @param variables to fill with results (null to ignore)
     * @param at Block Location to find
     * @return True if something was found, False if not
     */
    public static boolean find(List<LinkedSign> signs, List<Variable> variables, Location at) {
        return VariableMap.INSTANCE.find(signs, CommonUtil.unsafeCast(variables), at);
    }

    /**
     * Parses the Variable name from a line of text (on a sign).
     * The name before, after or in between '%'-signs is obtained.
     * 
     * @param line to parse
     * @return variable name displayed on this line
     */
    public static String parseVariableName(String line) {
        int perstart = line.indexOf("%");
        if (perstart != -1) {
            int perend = line.lastIndexOf("%");
            final String varname;
            if (perend == perstart) {
                //left or right...
                if (perstart == 0) {
                    //R
                    varname = line.substring(1);
                } else if (perstart == line.length() - 1) {
                    //L
                    varname = line.substring(0, line.length() - 1);
                } else if (line.substring(perstart).contains(" ")) {
                    //L
                    varname = line.substring(0, perstart);
                } else {
                    //R
                    varname = line.substring(perstart + 1);
                }
            } else {
                //Get in between the two %
                varname = line.substring(perstart + 1, perend);
            }
            if (!varname.isEmpty() && !varname.contains(" ")) {
                return varname;
            }
        }
        return null;
    }
}