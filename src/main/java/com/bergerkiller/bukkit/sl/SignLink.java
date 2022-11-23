package com.bergerkiller.bukkit.sl;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.TimeZone;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bergerkiller.bukkit.common.Common;
import com.bergerkiller.bukkit.common.MessageBuilder;
import com.bergerkiller.bukkit.common.PluginBase;
import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.metrics.MyDependingPluginsGraph;
import com.bergerkiller.bukkit.common.protocol.PacketBlockStateChangeListener;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.sl.API.GroupVariable;
import com.bergerkiller.bukkit.sl.API.PlayerVariable;
import com.bergerkiller.bukkit.sl.API.Ticker;
import com.bergerkiller.bukkit.sl.API.Variable;
import com.bergerkiller.bukkit.sl.API.VariableValue;
import com.bergerkiller.bukkit.sl.API.Variables;
import com.bergerkiller.bukkit.sl.PAPI.PlaceholderAPIHandler;
import com.bergerkiller.bukkit.sl.PAPI.PlaceholderAPIHandlerWithExpansions;
import com.bergerkiller.bukkit.sl.PAPI.PlaceholderAPIHandlerWithHook;
import com.bergerkiller.bukkit.sl.impl.VariableMap;
import com.bergerkiller.bukkit.sl.impl.format.FormatCommandInjector;

public class SignLink extends PluginBase {
    public static SignLink plugin;
    public static boolean updateSigns = false;
    private SimpleDateFormat dateFormat;
    private SimpleDateFormat timeFormat;
    private TimeZone timeZone;
    private Task updatetask;
    private Task updateordertask;
    private Task timetask;
    public PlaceholderAPIHandler papi = null;
    private boolean papi_enabled = false;
    private boolean papi_show_on_signs = false;
    private boolean discover_sign_changes = false;
    private List<String> papi_auto_variables = Collections.emptyList();
    private Task papi_auto_task = null;
    private final SLListener listener = new SLListener();

    @Override
    public int getMinimumLibVersion() {
        return Common.VERSION;
    }

    /**
     * Gets the player by name, case-insensitive. The Bukkit getPlayer() has some
     * (performance) flaws, and on older versions of Minecraft the exact getter
     * wasn't case-insensitive at all.
     *
     * @param name Player name, must be all-lowercase
     * @return Player matching this name, or null if not online right now
     */
    public Player getPlayerByLowercase(String name) {
        return this.listener.getPlayerByLowercase(name);
    }

    /**
     * Whether all signs on the server are routinely checked for sign text changes.
     * When a sign suddenly has a variable displayed on it, this will make it change that
     * into a variable.
     * 
     * @return True when sign changes are automatically discovered
     */
    public boolean discoverSignChanges() {
        return discover_sign_changes;
    }

    @Override
    public void enable() {
        plugin = this;

        this.register((Listener) this.listener);
        this.register(new SLBlockStateChangeListener(), PacketBlockStateChangeListener.LISTENED_TYPES);
        this.register("togglesignupdate", "reloadsignlink", "variable");

        FileConfiguration config = new FileConfiguration(this);
        config.load();
        config.setHeader("timeFormat", "Time format used when representing time in the %time% default variable");
        config.setHeader("dateFormat", "Date format used when representing time in the %date% default variable");
        config.setHeader("timeZone", "Time zone used for the %time% and %date% default variables");
        config.addHeader("timeZone", "Use 'system' for the system default (JVM) time zone");
        config.addHeader("timeZone", "A list of timezone id's can be found here: https://garygregory.wordpress.com/2013/06/18/what-are-the-java-timezone-ids/");
        String timeFormat = config.get("timeFormat", "H:mm:ss");
        String dateFormat = config.get("dateFormat", "yyyy.MM.dd");
        String timeZone = config.get("timeZone", "system");
        try {
            this.timeFormat = new SimpleDateFormat(timeFormat);
        } catch (IllegalArgumentException ex) {
            log(Level.WARNING, "Time format: " + timeFormat + " has not been recognized!");
            timeFormat = "H:mm:ss";
            this.timeFormat = new SimpleDateFormat(timeFormat);
        }
        try {
            this.dateFormat = new SimpleDateFormat(dateFormat);
        } catch (IllegalArgumentException ex) {
            log(Level.WARNING, "Date format: " + dateFormat + " has not been recognized!");
            dateFormat = "yyyy.MM.dd";
            this.dateFormat = new SimpleDateFormat(dateFormat);
        }
        if (timeZone.equals("default") || timeZone.equals("system")) {
            this.timeZone = TimeZone.getDefault();
        } else {
            this.timeZone = TimeZone.getTimeZone(timeZone);
            if (!this.timeZone.getID().equalsIgnoreCase(timeZone)) {
                log(Level.WARNING, "Time zone: " + timeZone + " has not been recognized!");
                this.timeZone = TimeZone.getDefault();
                timeZone = this.timeZone.getID();
            }
        }
        this.timeFormat.setTimeZone(this.timeZone);
        this.dateFormat.setTimeZone(this.timeZone);

        config.setHeader("discoverSignChanges", "Whether all signs on the server are routinely checked for changes in text");
        config.addHeader("discoverSignChanges", "When they suddenly display a variable, this variable is swapped out");
        config.addHeader("discoverSignChanges", "Enabling this may have a negative effect on server tick rate");
        this.discover_sign_changes = config.get("discoverSignChanges", false);

        // PlaceholderAPI
        config.setHeader("PlaceholderAPI", "Sets the settings for the PlaceholderAPI plugin. Only applies when detected.");
        ConfigurationNode papiConfig = config.getNode("PlaceholderAPI");
        papiConfig.setHeader("enabled", "Sets whether PlaceholderAPI variables are handled by SignLink");
        this.papi_enabled = papiConfig.get("enabled", true);
        papiConfig.setHeader("showOnSigns", "Whether PlaceholderAPI variables are detected and displayed on signs");
        papiConfig.addHeader("showOnSigns", "When disabled, only SignLink variables are made available within PlaceholderAPI");
        this.papi_show_on_signs = papiConfig.get("showOnSigns", true);
        papiConfig.setHeader("autoUpdateVariables", "Names of PlaceholderAPI variables that will be automatically updated every tick");
        papiConfig.addHeader("autoUpdateVariables", "Note that this induces a potential overhead for some plugin-defined variables");
        this.papi_auto_variables = papiConfig.getList("autoUpdateVariables", String.class);
        config.save();

        VirtualSign.init();

        // Adds signs from offline file storage. No longer used.
        /*
        //Load sign locations from file
        config = new FileConfiguration(this, "linkedsigns.yml");
        config.load();
        // Initialize the linked signs, disable updates while doing so to avoid unneeded text processing
        updateSigns = false;
        for (String node : config.getKeys()) {
            Variable currvar = Variables.get(node);
            for (String textline : config.getList(node, String.class)) {
                try {
                    String[] bits = textline.split("_");
                    SignDirection direction = ParseUtil.parseEnum(bits[bits.length - 1], SignDirection.NONE);
                    int line = Integer.parseInt(bits[bits.length - 2]);
                    int x = Integer.parseInt(bits[bits.length - 5]);
                    int y = Integer.parseInt(bits[bits.length - 4]);
                    int z = Integer.parseInt(bits[bits.length - 3]);
                    StringBuilder worldnameb = new StringBuilder();
                    for (int i = 0; i <= bits.length - 6; i++) {
                        if (i > 0) worldnameb.append('_');
                        worldnameb.append(bits[i]);
                    }
                    currvar.addLocation(worldnameb.toString(), x, y, z, line, direction);
                } catch (Exception ex) {
                    log(Level.WARNING, "Failed to parse line: " + textline);
                    ex.printStackTrace();
                }
            }
        }
        */

        updateSigns = true;

        //General %time% and %date% update thread
        timetask = new TimeUpdateTask(this).start(5, 5);
        
        loadValues();

        //Start updating
        updateordertask = new SignUpdateOrderTask(this).start(1, 1);
        updatetask = new SignUpdateTextTask(this).start(1, 1);

        // Load all signs in all worlds already loaded right now
        this.loadSigns();

        // Metrics
        if (this.hasMetrics()) {
            this.getMetrics().addGraph(new MyDependingPluginsGraph());
        }
    }

    @Override
    public void disable() {
        Task.stop(timetask);
        Task.stop(updatetask);
        Task.stop(updateordertask);
        Task.stop(papi_auto_task);

        // Save sign locations to file
        // No longer used.
        /*
        FileConfiguration config = new FileConfiguration(this, "linkedsigns.yml");
        for (String varname : Variables.getNames()) {
            List<String> nodes = config.getList(varname, String.class);
            for (LinkedSign sign : Variables.get(varname).getSigns()) {
                StringBuilder builder = new StringBuilder(40);
                builder.append(sign.location.world).append('_').append(sign.location.x);
                builder.append('_').append(sign.location.y);
                builder.append('_').append(sign.location.z);
                builder.append('_').append(sign.line);
                builder.append('_').append(sign.direction.toString());
                nodes.add(builder.toString());
            }
            if (nodes.isEmpty()) {
                config.remove(varname);
            }
        }
        config.save();
        */

        //Save variable values and tickers to file
        this.saveValues();

        VariableMap.INSTANCE.deinit();
        VirtualSign.deinit();
    }

    public void loadSigns() {
        for (World world : WorldUtil.getWorlds()) {
            this.listener.loadSigns(WorldUtil.getBlockStates(world));
        }
    }

    public void loadValues() {
        FileConfiguration values = new FileConfiguration(this, "values.yml");
        if (!values.exists()) {            
            values.set("test.ticker", "LEFT");
            values.set("test.tickerInterval", 3);
            values.set("test.value", "This is a test message being ticked from right to left. ");
            values.set("sign.ticker", "NONE");
            values.set("sign.value", "This is a regular message you can set and is updated only once.");
            values.save();
        }
        values.load();
        for (ConfigurationNode node : values.getNodes()) {
            Variable var = Variables.get(node.getName());
            var.setDefault(node.get("value", Variable.createDefaultValue(var.getName())));
            var.getDefaultTicker().load(node);
            for (ConfigurationNode forplayer : node.getNode("forPlayers").getNodes()) {
                String value = forplayer.get("value", String.class, null);
                PlayerVariable pvar = var.forPlayer(forplayer.getName());
                if (value != null) {
                    pvar.set(value);
                }
                pvar.getTicker().load(forplayer);
            }
        }
    }

    public void saveValues() {
        FileConfiguration values = new FileConfiguration(this, "values.yml");
        for (Variable var : Variables.getAll()) {
            ConfigurationNode node = values.getNode(var.getName());
            node.set("value", var.getDefault());
            var.getDefaultTicker().save(node);
            for (PlayerVariable pvar : var.forAll()) {
                ConfigurationNode forplayer = node.getNode("forPlayers").getNode(pvar.getPlayer());
                forplayer.set("value", pvar.get());
                if (!pvar.isTickerShared()) {
                    pvar.getTicker().save(forplayer);
                }
            }
        }
        values.save();
    }

    @Override
    public void permissions() {
        this.loadPermissions(Permission.class);
    }

    private class VariableEdit {
        public VariableEdit(Variable var) {
            this.variable = var;
            this.players = new String[0];
        }
        public Variable variable;
        public String[] players;
        public boolean global() {
            return this.players.length == 0;
        }
        public GroupVariable group() {
            return this.variable.forGroup(this.players);
        }
    }

    private HashMap<String, VariableEdit> editingvars = new HashMap<String, VariableEdit>();

    /**
     * Removes a given variable from the player editing map
     * 
     * @param variable to remove
     */
    public void removeEditing(Variable variable) {
        // Get rid of all editing players for this variable
        Iterator<VariableEdit> vars = editingvars.values().iterator();
        while (vars.hasNext()) {
            if (vars.next().variable == variable) {
                vars.remove();
            }
        }
    }

    @Override
    public void updateDependency(Plugin plugin, String pluginName, boolean enabled) {
        if (pluginName.equals("PlaceholderAPI")) {
            if (enabled && this.papi_enabled) {
                boolean usePAPIExpansions = false;
                try {
                    Class.forName("me.clip.placeholderapi.expansion.manager.LocalExpansionManager");
                    usePAPIExpansions = true;
                } catch (Throwable t) {}

                try {
                    if (usePAPIExpansions) {
                        this.papi = new PlaceholderAPIHandlerWithExpansions(this);
                    } else {
                        this.papi = new PlaceholderAPIHandlerWithHook(this);
                    }
                    this.papi.setShowOnSigns(this.papi_show_on_signs);
                    this.papi.enable();
                    this.papi_auto_task = new PlaceholderAPIAutoUpdateTask(this).start(1, 1);
                } catch (Throwable t) {
                    this.handle(t);
                    this.papi = null;
                }
            } else if (this.papi != null) {
                try {
                    this.papi.disable();
                } catch (Throwable t) {
                    this.handle(t);
                }
                this.papi = null;
                Task.stop(this.papi_auto_task);
                this.papi_auto_task = null;
            }
        }
    }

    @Override
    public boolean command(CommandSender sender, String cmdLabel, String[] args) {
        // Toggle sign updating on/off
        if (cmdLabel.equalsIgnoreCase("togglesignupdate")) {
            Permission.TOGGLEUPDATE.handle(sender);
            updateSigns = !updateSigns;
            if (updateSigns) {
                sender.sendMessage(ChatColor.GREEN + "Signs are now being updated!");
            } else {
                sender.sendMessage(ChatColor.YELLOW + "Signs are now no longer being updated!");
            }
            return true;
        }
        // Reload SignLink configuration
        if (cmdLabel.equalsIgnoreCase("reloadsignlink")) {
            Permission.RELOAD.handle(sender);
            loadValues();
            loadSigns();
            sender.sendMessage(ChatColor.GREEN + "SignLink reloaded the Variable values");
            return true;
        }
        // Remaining variable commands
        if (!cmdLabel.equalsIgnoreCase("variable") && !cmdLabel.equalsIgnoreCase("var")) {
            return false;
        } else if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Please specify a sub-command!");
            sender.sendMessage(ChatColor.YELLOW + "Use /help variable for command help");
            return true;
        }
        // All below commands are sub-commands of /variable
        // Remove the first argument - it is the sub-command
        cmdLabel = args[0];
        args = StringUtil.remove(args, 0);

        // Variable list
        if (cmdLabel.equalsIgnoreCase("list")) {
            ArrayList<String> vars = new ArrayList<String>();
            for (Variable variable : Variables.getAll()) {
                if (Permission.EDIT.has(sender, variable.getName())) {
                    vars.add(variable.getName());
                }
            }
            MessageBuilder builder = new MessageBuilder();
            if (vars.isEmpty()) {
                // None to be listed
                builder.red("There are no variables to be shown, or you are not allowed to edit them");
            } else {
                // List them
                builder.yellow("There are ").white(vars.size()).yellow(" variables you can edit:").newLine();
                builder.setIndent(2).setSeparator(ChatColor.WHITE, " / ");
                for (String name : vars) {
                    builder.append(ChatColor.GREEN, name);
                }
            }
            builder.send(sender);
            return true;
        }

        // Global variable deletion
        final boolean signcheck = cmdLabel.equalsIgnoreCase("deleteunused");
        if (cmdLabel.equalsIgnoreCase("deleteall") || signcheck) {
            Permission.GLOBALDELETE.handle(sender);
            List<Variable> allVars = Variables.getAllAsList();
            if (signcheck) {
                Iterator<Variable> var = allVars.iterator();
                while (var.hasNext()) {
                    Variable next = var.next();
                    if (next.getSigns().length > 0) {
                        var.remove();
                    }
                }
            }
            // Remove
            if (allVars.isEmpty()) {
                sender.sendMessage(ChatColor.YELLOW + "No variables were found that could be deleted");
            } else {
                for (Variable var : allVars) {
                    Variables.remove(var.getName());
                }
                if (allVars.size() == 1) {
                    sender.sendMessage(ChatColor.GREEN + "One variable was deleted: " + allVars.get(0).getName());
                } else {
                    sender.sendMessage(ChatColor.GREEN + Integer.toString(allVars.size()) + " variables have been deleted!");
                }
            }
            return true;
        }

        // Reloads the variable values based on PlaceholderAPI for all players on the server
        if (cmdLabel.equalsIgnoreCase("refreshpapi")) {
            Permission.REFRESHPAPI.handle(sender);
            if (this.papi == null) {
                sender.sendMessage(ChatColor.RED + "PlaceholderAPI is not installed or enabled in SignLink");
            } else {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    this.papi.refreshVariables(player);
                }
                sender.sendMessage(ChatColor.GREEN + "All PlaceholderAPI variables have been updated!");
            }
            return true;
        }

        // Variable edit
        if (cmdLabel.equalsIgnoreCase("edit") || cmdLabel.equalsIgnoreCase("add")) {
            if (args.length >= 1) {
                Permission.EDIT.handle(sender, args[0]);
                if (args[0].contains(" ")) {
                    sender.sendMessage(ChatColor.RED + "Variable names can not contain spaces!");
                    return true;
                }
                // Add a new Variable editing slot
                VariableEdit edit = new VariableEdit(Variables.get(args[0]));
                if (sender instanceof Player) {
                    editingvars.put(((Player) sender).getName().toLowerCase(), edit);
                } else {
                    editingvars.put(null, edit);
                }
                // Handle feedback
                sender.sendMessage(ChatColor.GREEN + "You are now editing variable '" + args[0] + "'");
            } else {
                sender.sendMessage(ChatColor.RED + "Please specify a variable name!");
            }
            args = StringUtil.remove(args, 0);
            if (args.length == 0) {
                return true;
            }
            cmdLabel = args[0];
            args = StringUtil.remove(args, 0);
        }
        // Obtain the currently edited variable
        VariableEdit var;
        if (sender instanceof Player) {
            var = editingvars.get(((Player) sender).getName().toLowerCase());
        } else {
            var = editingvars.get(null);
        }
        if (var == null) {
            sender.sendMessage(ChatColor.RED + "Please edit a variable first using /variable edit!");
            return true;
        }

        // Sub-commands operating on the editing variables
        // Variable property coding here
        if (cmdLabel.equalsIgnoreCase("for") || cmdLabel.equalsIgnoreCase("forplayers")) {
            var.players = args;
            if (args.length == 0) {
                sender.sendMessage(ChatColor.GREEN + "You are now editing this variable for all players!");
            } else {
                sender.sendMessage(ChatColor.GREEN + "You are now editing this variable for the selected players!");
            }
        } else if (cmdLabel.equalsIgnoreCase("get")) {
            if (var.global()) {
                sender.sendMessage(ChatColor.YELLOW + "Current value is: " + ChatColor.BLACK + var.variable.getDefault());
            } else {
                sender.sendMessage(ChatColor.YELLOW + "Current value is: " + ChatColor.BLACK + var.variable.get(var.players[0]));
            }
        } else if (cmdLabel.equalsIgnoreCase("setdefault") || cmdLabel.equalsIgnoreCase("setdef")) {
            String value = StringUtil.ampToColor(StringUtil.join(" ", args));
            var.variable.setDefault(value);
            if (args.length == 0) {
                sender.sendMessage(ChatColor.YELLOW + "Default variable value emptied!");
            } else {
                sender.sendMessage(ChatColor.YELLOW + "Default variable value set to '" + value + ChatColor.RESET + ChatColor.YELLOW + "'!");
            }
        } else if (cmdLabel.equalsIgnoreCase("set")) {
            if (args.length == 0) {
                if (var.global()) {
                    var.variable.set("");
                } else {
                    var.group().set("");
                }
                sender.sendMessage(ChatColor.YELLOW + "Variable value emptied!");
            } else {
                String value = StringUtil.ampToColor(StringUtil.join(" ", args));
                value = FormatCommandInjector.process(sender, value);
                if (var.global()) {
                    var.variable.set(value);
                } else {
                    var.group().set(value);
                }
                sender.sendMessage(ChatColor.YELLOW + "Variable value set to '" + value + ChatColor.RESET + ChatColor.YELLOW + "'!");
            }
        } else if (cmdLabel.equalsIgnoreCase("clear")) {
            if (var.global()) {
                var.variable.clear();
            } else {
                var.group().clear();
            }
            sender.sendMessage(ChatColor.YELLOW + "Variable has been cleared!");
        } else if (cmdLabel.equals("addpause") || cmdLabel.equalsIgnoreCase("pause")) {
            if (args.length == 2) {
                try {
                    int delay = Integer.parseInt(args[0]);
                    int duration = Integer.parseInt(args[1]);
                    Ticker t;
                    if (var.global()) {
                        t = var.variable.getTicker();
                    } else {
                        t = var.group().getTicker();
                    }
                    t.addPause(delay, duration);
                    sender.sendMessage(ChatColor.GREEN + "Ticker pause added!");
                } catch (Exception ex) {
                    sender.sendMessage(ChatColor.RED + "Please specify valid pause delay and duration values!");
                }
            } else {
                sender.sendMessage(ChatColor.RED + "Please specify the delay and duration for this pause!");
            }
        } else if (cmdLabel.equalsIgnoreCase("clearpauses") || cmdLabel.equalsIgnoreCase("clearpause")) {
            Ticker t;
            if (var.global()) {
                t = var.variable.getTicker();
            } else {
                t = var.group().getTicker();
            }
            t.clearPauses();
            sender.sendMessage(ChatColor.YELLOW + "Ticker pauses cleared!");
        } else if (cmdLabel.equalsIgnoreCase("delete")) {
            if (var.variable.getSigns().length == 0 || args.length > 0 && args[0].equalsIgnoreCase("force")) {
                Variables.remove(var.variable.getName());
                sender.sendMessage(ChatColor.GREEN + "Deleted variable '" + var.variable.getName() + "'!");
            } else {
                sender.sendMessage(ChatColor.RED + "This variable still contains signs that are displaying it!");
                sender.sendMessage(ChatColor.YELLOW + "To delete it anyhow, use /variable delete force");
            }
        } else if (cmdLabel.equalsIgnoreCase("setticker")) {
            if (args.length >= 1) {
                VariableValue varValue;
                if (var.global()) {
                    varValue = var.variable;
                } else {
                    varValue = var.group();
                }
                Ticker t = varValue.getTicker();
                // Swap if reversed order
                String intervalName = "";
                String modeName = "";
                if (ParseUtil.isNumeric(args[0])) {
                    // <interval> <mode>
                    intervalName = args[0];
                    if (args.length > 1) {
                        modeName = args[1];
                    }
                } else {
                    // <mode> <interval>
                    modeName = args[0];
                    if (args.length > 1) {
                        intervalName = args[1];
                    }
                }
                // Set ticker
                if (!modeName.isEmpty()) {
                    t.setMode(ParseUtil.parseEnum(modeName, t.getMode()));
                }
                if (!intervalName.isEmpty()) {
                    t.setInterval(ParseUtil.parseLong(intervalName, t.getInterval()));
                }
                t.reset(varValue.get());
                if (!t.isTicking()) {
                    sender.sendMessage(ChatColor.GREEN + "Ticker is disabled");
                } else {
                    sender.sendMessage(ChatColor.GREEN + "You set a '" + t.getMode().toString().toLowerCase()
                            + "' ticker ticking every " + t.getInterval() + " ticks!");
                }
            } else {
                sender.sendMessage(ChatColor.RED + "Please specify the ticker direction!");
            }
        } else {
            sender.sendMessage(ChatColor.RED + "Unknown sub-command: " + ChatColor.YELLOW + cmdLabel);
            sender.sendMessage(ChatColor.YELLOW + "Use /help variable for command help");
        }
         return true;
    }

    private static class TimeUpdateTask extends Task {
        private long prevtpstime = System.currentTimeMillis();

        public TimeUpdateTask(JavaPlugin plugin) {
            super(plugin);
        }

        @Override
        public void run() {
            Date time = Calendar.getInstance(SignLink.plugin.timeZone).getTime();
            Variables.get("time").set(SignLink.plugin.timeFormat.format(time).trim());
            Variables.get("date").set(SignLink.plugin.dateFormat.format(time).trim());
            long newtime = System.currentTimeMillis();
            float ticktime = (float) (newtime - prevtpstime) / 5000;
            if (ticktime == 0) ticktime = 1;
            int per = (int) (5 / ticktime);
            if (per > 100) per = 100;
            Variables.get("tps").set(per + "%");
            prevtpstime = newtime;
        }
    }

    private static class SignUpdateOrderTask extends Task {
        public SignUpdateOrderTask(JavaPlugin plugin) {
            super(plugin);
        }

        @Override
        public void run() {
            try {
                VirtualSignStore.globalUpdateSignOrders();
            } catch (Throwable t) {
                SignLink.plugin.log(Level.SEVERE, "An error occured while updating sign order:");
                SignLink.plugin.handle(t);
            }
        }
    }

    private static class SignUpdateTextTask extends Task {
        public SignUpdateTextTask(JavaPlugin plugin) {
            super(plugin);
        }

        @Override
        public void run() {
            try {
                Variables.updateTickers();
                VirtualSignStore.forEachSign(VirtualSign::update);
            } catch (Throwable t) {
                SignLink.plugin.log(Level.SEVERE, "An error occured while updating sign text:");
                SignLink.plugin.handle(t);
            }
        }
    }

    private static class PlaceholderAPIAutoUpdateTask extends Task {

        public PlaceholderAPIAutoUpdateTask(JavaPlugin plugin) {
            super(plugin);
        }

        @Override
        public void run() {
            if (plugin.papi != null) {
                for (String varName : plugin.papi_auto_variables) {
                    plugin.papi.refreshVariableForAll(Variables.get(varName));
                }
            }
        }
    }
}
