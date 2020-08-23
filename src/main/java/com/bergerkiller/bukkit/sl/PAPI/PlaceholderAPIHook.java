package com.bergerkiller.bukkit.sl.PAPI;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.sl.API.Variable;
import com.bergerkiller.bukkit.sl.API.Variables;

import me.clip.placeholderapi.PlaceholderHook;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public class PlaceholderAPIHook extends PlaceholderExpansion{

    private final JavaPlugin plugin;
    
    public PlaceholderAPIHook(JavaPlugin plugin){
        this.plugin = plugin;
    }
    
    @Override
    public @NotNull String getIdentifier(){
        return "sl";
    }
    
    @Override
    public @NotNull String getAuthor(){
        return String.join(", ", plugin.getDescription().getAuthors());
    }
    
    @Override
    public @NotNull String getVersion(){
        return plugin.getDescription().getVersion();
    }
    
    @Override
    public String onRequest(OfflinePlayer player, @NotNull String name) {
        Variable variable = Variables.getIfExists(name);
        if (variable == null) {
            return null;
        }
        return variable.get(player.getName());
    }
}
