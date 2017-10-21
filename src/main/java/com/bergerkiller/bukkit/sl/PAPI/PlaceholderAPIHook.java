package com.bergerkiller.bukkit.sl.PAPI;

import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.sl.API.Variable;
import com.bergerkiller.bukkit.sl.API.Variables;

import me.clip.placeholderapi.PlaceholderHook;

public class PlaceholderAPIHook extends PlaceholderHook {

    @Override
    public String onPlaceholderRequest(Player player, String name) {
        Variable variable = Variables.getIfExists(name);
        if (variable == null) {
            return null;
        }
        return variable.get(player.getName());
    }

}
