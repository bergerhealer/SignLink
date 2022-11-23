package com.bergerkiller.bukkit.sl.impl.format;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Injects information known at command time, such as the name of the sender
 * or the nearest player, as specific variable names used for that purpose.
 */
public class FormatCommandInjector extends FormatMatcher {
    private final CommandSender sender;
    private final StringBuilder outputFormat = new StringBuilder();
    private Optional<Player> cachedNearestPlayer = null; // Lazy-initialized if null
    private boolean isCommandInjected = false;

    /**
     * Processes the input text and replaces registered variable names with information
     * derived from the command sender.
     *
     * @param sender Sender
     * @param input Input format string
     * @return Updated text, or input text if no command sender related variables are declared
     */
    public static String process(CommandSender sender, String input) {
        // Optimization
        if (input.indexOf('%') == -1) {
            return input;
        }

        FormatCommandInjector injector = new FormatCommandInjector(sender);
        injector.match(input);
        if (!injector.isCommandInjected) {
            return input; // Don't allocate a new string for no reason...
        }

        return injector.outputFormat.toString();
    }

    // Registered variable names provided by this injector
    private static final Map<String, Function<FormatCommandInjector, String>> providers = new HashMap<>();
    static {
        registerSenderFunction("", CommandSender::getName);
        registerSenderFunction("_name", CommandSender::getName);
        registerPlayerFunction("_display_name", Player::getDisplayName);
        registerPlayerFunction("_level", p -> Integer.toString(p.getLevel()));
    }

    private static void registerSenderFunction(String postfix, Function<CommandSender, String> provider) {
        providers.put("sender" + postfix, injector -> provider.apply(injector.sender));
        registerNearest(postfix, provider);
    }
    
    private static void registerPlayerFunction(String postfix, Function<Player, String> provider) {
        providers.put("sender" + postfix, injector -> {
            if (injector.sender instanceof Player) {
                return provider.apply((Player) injector.sender);
            } else {
                return "";
            }
        });
        registerNearest(postfix, provider);
    }

    private static void registerNearest(String postfix, Function<? super Player, String> provider) {
        providers.put("nearest_player" + postfix, injector -> injector.getNearestPlayer().map(provider).orElse(""));
    }

    private FormatCommandInjector(CommandSender sender) {
        this.sender = sender;
    }

    private Optional<Player> getNearestPlayer() {
        if (cachedNearestPlayer == null) {
            if (sender instanceof Player) {
                cachedNearestPlayer = Optional.of((Player) sender);
            } else if (sender instanceof BlockCommandSender) {
                Block at = ((BlockCommandSender) sender).getBlock();
                Iterator<Player> playersIter;
                if (at != null && (playersIter = at.getWorld().getPlayers().iterator()).hasNext()) {
                    // Command block is known and the world the command block is on has players
                    // Try to find the player nearest to the command block
                    Player nearest = playersIter.next();
                    if (playersIter.hasNext()) {
                        // More than one player. Got to check distances to the command block as well.
                        Location mid = new Location(at.getWorld(),
                                (double) at.getX() + 0.5, (double) at.getY() + 0.5, (double) at.getZ() + 0.5);
                        double minDistanceSq = nearest.getLocation().distanceSquared(mid);
                        do {
                            Player p = playersIter.next();
                            double distSq = p.getLocation().distanceSquared(mid);
                            if (distSq < minDistanceSq) {
                                minDistanceSq = distSq;
                                nearest = p;
                            }
                        } while (playersIter.hasNext());
                    }
                    cachedNearestPlayer = Optional.of(nearest);
                } else {
                    cachedNearestPlayer = Optional.empty();
                }
            }
        }
        return cachedNearestPlayer;
    }

    @Override
    public void onTextConstant(String constant) {
        outputFormat.append(constant);
    }

    @Override
    public void onVariable(String variableName) {
        Function<FormatCommandInjector, String> provider = providers.get(variableName);
        if (provider != null) {
            outputFormat.append(provider.apply(this));
            isCommandInjected = true;
        } else {
            outputFormat.append('%').append(variableName).append('%');
        }
    }

    @FunctionalInterface
    private static interface VariableProvider {
        String get(FormatCommandInjector injector);
    }
}
