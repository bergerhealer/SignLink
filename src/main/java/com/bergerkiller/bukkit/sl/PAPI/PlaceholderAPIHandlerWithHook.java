package com.bergerkiller.bukkit.sl.PAPI;

import java.lang.reflect.Method;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import com.bergerkiller.bukkit.common.TickTracker;
import com.bergerkiller.bukkit.sl.API.Variable;
import com.bergerkiller.bukkit.sl.API.Variables;
import com.bergerkiller.mountiplex.reflection.ClassInterceptor;
import com.bergerkiller.mountiplex.reflection.declarations.Template;
import com.bergerkiller.mountiplex.reflection.resolver.Resolver;
import com.bergerkiller.mountiplex.reflection.util.fast.Invoker;

/**
 * Legacy 1.x PlaceholderAPI that uses the PlaceholderHook to register placeholders.
 * Superseeded by the expansions api.
 */
public final class PlaceholderAPIHandlerWithHook implements PlaceholderAPIHandler {
    private final PlaceholderAPIHandle handle;
    private final JavaPlugin plugin;
    private final Object hook;
    private boolean registeredSLHook;
    private Map<String, Object> pluginHooks;
    private final TickTracker pluginHooksTracker = new TickTracker();
    private boolean show_on_signs;

    public PlaceholderAPIHandlerWithHook(JavaPlugin plugin) {
        this.plugin = plugin;
        this.registeredSLHook = false;

        // Find PlaceholderHook class type, must exist
        // Use Resolver, makes sure SignLink initializes the class in the global cache
        // Prevents warnings showing up when BKCommonLib tries to do it later
        ClassLoader loader = this.getClass().getClassLoader();
        Resolver.loadClass("me.clip.placeholderapi.PlaceholderAPI", true, loader);
        final Class<?> placeholderHookType = Resolver.loadClass("me.clip.placeholderapi.PlaceholderHook", true, loader);
        if (placeholderHookType == null) {
            throw new IllegalStateException("Placeholder API could not be hooked into, Hook Class does not exist");
        }

        // Initialize handle, must succeed
        try {
            this.handle = Template.Class.create(PlaceholderAPIHandle.class);
            this.handle.forceInitialization();
        } catch (Throwable t) {
            throw new IllegalStateException("Placeholder API could not be hooked into", t);
        }

        // Generate a hook class for Legacy PlaceHolderAPI
        ClassInterceptor interceptor = new ClassInterceptor() {
            @Override
            protected Invoker<?> getCallback(Method method) {
                if (method.getName().equals("onPlaceholderRequest")) {
                    return (instance, args) -> {
                        Player player = (Player) args[0];
                        String name = (String) args[1];

                        Variable variable = Variables.getIfExists(name);
                        if (variable == null) {
                            return null;
                        }
                        return variable.get(player.getName());
                    };
                }

                return null;
            }
        };
        this.hook = interceptor.constructInstance(placeholderHookType, new Class[0], new Object[0]);

        // Reload placeholders once every tick at most
        this.pluginHooksTracker.setRunnable(() -> pluginHooks = handle.getPlaceholders());
    }

    public void enable() {
        this.handle.registerPlaceholderHook(this.plugin, this.hook);
        if (!this.handle.getPlaceholders().containsKey("sl")) {
            this.handle.registerPlaceholderHookName("sl", this.hook);
            this.registeredSLHook = true;
        }
    }

    public void disable() {
        this.handle.unregisterPlaceholderHook(this.plugin);
        if (this.registeredSLHook) {
            this.handle.unregisterPlaceholderHookName("sl");
            this.registeredSLHook = false;
        }
    }

    public void setShowOnSigns(boolean showOnSigns) {
        this.show_on_signs = showOnSigns;
    }

    /**
     * Refreshes the PAPI variables displayed to a particular Player
     */
    public void refreshVariables(Player player) {
        if (!this.show_on_signs) {
            return;
        }
        for (Variable var : Variables.getAll()) {
            if (!isHookedVariable(var.getName())) {
                continue;
            }

            String value = this.getVariableValue(player, var.getName());
            if (value != null) {
                var.forPlayer(player).set(value);
            }
        }
    }

    public void refreshVariableForAll(Variable var) {
        if (!this.show_on_signs) {
            return;
        }
        if (isHookedVariable(var.getName())) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                String value = this.getVariableValue(player, var.getName());
                if (value != null) {
                    var.forPlayer(player).set(value);
                }
            }
        }
    }

    /**
     * Checks whether a particular variable is a potential PlaceholderAPI hooked variable name
     * 
     * @param variableName to check
     * @return True of a PAPI variable
     */
    public boolean isHookedVariable(String variableName) {
        int pluginIdx = variableName.indexOf('_');
        if (pluginIdx == -1) {
            return false;
        }

        String pluginName = variableName.substring(0, pluginIdx);
        this.pluginHooksTracker.update();
        return this.pluginHooks.containsKey(pluginName);
    }

    /**
     * Obtains the value of a variable according to PlaceholderAPI.
     * If the variable is not in the PAPI format or does not exist, null is returned.
     * 
     * @param player to get the value of the variable for
     * @param variableName to get (identifier_varname format)
     * @return PlaceholderAPI variable name
     */
    public String getVariableValue(Player player, String variableName) {
        int pluginIdx = variableName.indexOf('_');
        if (pluginIdx == -1) {
            return null;
        }

        this.pluginHooksTracker.update();
        String pluginName = variableName.substring(0, pluginIdx);
        Object hook = this.pluginHooks.get(pluginName);
        if (hook == null) {
            return null;
        }

        String name = variableName.substring(pluginIdx + 1);
        try {
            return this.handle.makePlaceholderRequest(hook, player, name);
        } catch (Throwable t) {
            t.printStackTrace();
            return null;
        }
    }

    @Template.Optional
    @Template.InstanceType("me.clip.placeholderapi.PlaceholderAPI")
    public static abstract class PlaceholderAPIHandle extends Template.Class<Template.Handle> {
        @Template.Generated("public static boolean registerPlaceholderHook(org.bukkit.plugin.Plugin plugin, PlaceholderHook hook)")
        public abstract boolean registerPlaceholderHook(Plugin plugin, Object hook);

        @Template.Generated("public static boolean registerPlaceholderHook(String identifier, PlaceholderHook hook)")
        public abstract boolean registerPlaceholderHookName(String identifier, Object hook);

        @Template.Generated("public static boolean unregisterPlaceholderHook(org.bukkit.plugin.Plugin plugin)")
        public abstract boolean unregisterPlaceholderHook(Plugin plugin);

        @Template.Generated("public static boolean unregisterPlaceholderHook(String identifier)")
        public abstract boolean unregisterPlaceholderHookName(String identifier);

        @Template.Generated("public static Map<String, PlaceholderHook> getPlaceholders()")
        public abstract Map<String, Object> getPlaceholders();

        @Template.Generated("public static String makePlaceholderRequest(PlaceholderHook hook, org.bukkit.entity.Player player, String name) {\n" +
                            "    return hook.onPlaceholderRequest(player, name);\n" +
                            "}")
        public abstract String makePlaceholderRequest(Object hook, Player player, String name);
    }
}
