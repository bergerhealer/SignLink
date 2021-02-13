package com.bergerkiller.bukkit.sl.impl;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.bergerkiller.bukkit.common.collections.ImplicitlySharedList;
import com.bergerkiller.bukkit.sl.LinkedSign;
import com.bergerkiller.bukkit.sl.VariableTextPlayerFilter;
import com.bergerkiller.bukkit.sl.API.PlayerVariable;
import com.bergerkiller.bukkit.sl.API.Ticker;
import com.bergerkiller.bukkit.sl.impl.format.FormatChangeListener;
import com.bergerkiller.bukkit.sl.impl.format.FormattedVariableValue;

/**
 * Tracks internal metadata about what variable value and ticker
 * information is viewed by what player.
 */
public class VariableValueMap {
    private final VariableImpl variable;
    private final Entry defaultEntry;
    private Map<String, Entry> byPlayer;
    private int numTickedPlayerEntries;

    public VariableValueMap(VariableImpl variable) {
        this.variable = variable;
        this.defaultEntry = new Entry();
        this.byPlayer = Collections.emptyMap();
        this.numTickedPlayerEntries = 0;
    }

    /**
     * Gets the name of the variable managed by this map
     *
     * @return variable name
     */
    public String getVariableName() {
        return this.variable.getName();
    }

    /**
     * Gets the variable associated with this map
     *
     * @return variable
     */
    public VariableImpl getVariable() {
        return this.variable;
    }

    /**
     * Gets all the player-specific text entries
     *
     * @return player entries
     */
    public Collection<Entry> getPlayerEntries() {
        return this.byPlayer.values();
    }

    /**
     * Gets all the player variables that have a custom variable
     * value or ticker set
     *
     * @return player variables
     */
    public Collection<PlayerVariable> getPlayersWithData() {
        return this.byPlayer.values().stream()
                .filter(Entry::hasCustomValue)
                .map(e -> new PlayerVariableImpl(e))
                .collect(Collectors.toList());
    }

    /**
     * Gets whether all players see the same default variable
     * notation. This does not mean they all see the same text,
     * if the variable declares variables different for each player.
     *
     * @return True if all players see the same variable value
     */
    public boolean isAllDefaultValue() {
        for (Entry e : this.byPlayer.values()) {
            if (e.hasCustomValue()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Gets the variable value assigned for the given player
     *
     * @param playerName
     * @return variable value
     */
    public String getValue(String playerName) {
        return this.byPlayer.getOrDefault(playerName, defaultEntry).value.getFormat();
    }

    /**
     * Gets the text displayed to a given player
     *
     * @param playerName Name of the player, all lower-case
     * @return text displayed to this player
     */
    public String getText(String playerName) {
        return this.byPlayer.getOrDefault(playerName, defaultEntry).getText();
    }

    /**
     * Resets the value and ticker to the default and removes all
     * player-specific values and tickers.
     */
    public void reset() {
        // Reset tickers
        this.defaultEntry.ticker = new DefaultTicker(this.defaultEntry);
        this.setSharedTicker();

        // Resets the variables to the variable name
        this.setAll(FormattedVariableValue.createDefaultValue(this.getVariableName()));
    }

    /**
     * Sets the default and all player-specific entries to show the same
     * variable value
     *
     * @param value New value
     */
    public void setAll(String value) {
        setAll(FormattedVariableValue.decode(this.defaultEntry, value));
    }

    /**
     * Sets the default and all player-specific entries to show the same
     * variable value
     *
     * @param value New formatted value, created using the default entry
     */
    public void setAll(FormattedVariableValue value) {
        // Apply the new format to the default entry
        // Variables that are declared will notify the (default) entry owners,
        // who will then order us to create additional entries for players
        // as needed.
        this.defaultEntry.setValue(value);

        // For all player-specific entries, if they don't already use the
        // default value, reset to the default value instead
        for (Entry e : this.byPlayer.values()) {
            if (e.hasCustomValue()) {
                e.setValue(value.cloneFor(e));
                e.hasCustomValue = false;
            }
        }
    }

    /**
     * Sets the default value, and updates the text for all players
     *
     * @param value New value
     */
    public void setDefault(String value) {
        this.defaultEntry.setValue(value);
    }

    /**
     * Updates signs with all player-specific variable values
     * it knows about
     *
     * @param signs Signs to update
     */
    public void updateSigns(Collection<LinkedSign> signs) {
        this.defaultEntry.apply(signs);
        for (Entry e : this.byPlayer.values()) {
            e.apply(signs);
        }
    }

    protected void notifyPlayerEntryTickedChanged(SinglePlayerTicker ticker, Entry entry) {
        // Make sure it still exists at all, as things could break otherwise!
        // Do not count the default entry
        if (entry == this.defaultEntry
                || this.byPlayer.get(entry.playerName) != entry
                || entry.ticker != ticker)
        {
            return;
        }

        // Update counter
        if (entry.ticker.isTicking()) {
            this.numTickedPlayerEntries++;
        } else {
            this.numTickedPlayerEntries--;
        }
    }

    /**
     * Updates the text based on any configured tickers
     */
    public void updateTickers() {
        if (this.defaultEntry.ticker.updateText(this.defaultEntry.text)) {
            this.defaultEntry.computePlayerText(false);
        }
        if (this.numTickedPlayerEntries > 0) {
            for (Entry e : this.byPlayer.values()) {
                if (e.ticker.updateText(e.text)) {
                    e.computePlayerText(false);
                }
            }
        }
    }

    /**
     * Gets the entry used for all players no specific value is
     * configured
     *
     * @return default ticker
     */
    public Entry getDefault() {
        return this.defaultEntry;
    }

    /**
     * Sets all player-specific values to use the same ticker instance
     *
     * @return shared ticker
     */
    public Ticker setSharedTicker() {
        for (Entry e : this.byPlayer.values()) {
            e.ticker = this.defaultEntry.ticker;
        }
        this.numTickedPlayerEntries = 0;
        return this.defaultEntry.ticker;
    }

    /**
     * Gets a unique entry for a player, where changes made to it
     * will only affect the text displayed to that one player.
     * When a new entry is created, it will initially have the
     * default variable value.
     *
     * @param playerName Name of the player, all lower-case
     * @return unique entry for this player
     */
    public Entry getPlayerEntry(String playerName) {
        if (this.byPlayer.isEmpty()) {
            // Initialize a new HashMap to store it
            this.byPlayer = new HashMap<String, Entry>();
        } else {
            // Try to find an existing entry in the map
            Entry e = this.byPlayer.get(playerName);
            if (e != null) {
                return e;
            }
        }

        // Create a new entry and store it
        Entry e = new Entry(playerName);
        this.byPlayer.put(playerName, e);

        // Clone the default value
        // Initialize value of the entry (AFTER adding to the map!)
        // If different, the variable text will need to be re-calculated
        FormattedVariableValue newValue = e.value.cloneFor(e);
        boolean changed = (e.value != newValue);
        e.value = newValue;
        e.hasCustomValue = false;

        // Register the declared variables of this entry
        // This might cause other entries to be modified as well
        FormatChangeListener.detectChanges(null, e.value, e);

        // If changed (and during callbacks it didn't change again), recalculate
        // Normally setValue() handles it, but we do not call it
        if (changed && e.value == newValue) {
            e.computePlayerText(true);
        }

        // Anyone that declares this same variable but has registered the default
        // entry, must also create an entry for this same player. This operation is
        // recursive. This is why it's important we store the entry in the map
        // before doing this.
        if (!this.defaultEntry.declaring.isEmpty()) {
            Set<VariableValueMap> declaringMaps = this.defaultEntry.declaring.stream()
                    .map(Entry::getValueMap)
                    .collect(Collectors.toSet());
            for (VariableValueMap map : declaringMaps) {
                map.getPlayerEntry(e.playerName);
            }
        }

        return e;
    }

    /**
     * Gets a unique entry for a player if one is stored, or
     * otherwise returns the default entry
     *
     * @param playerName
     * @return unique entry for this player, or default if none exists
     */
    public Entry getPlayerEntryOrDefault(String playerName) {
        return this.byPlayer.getOrDefault(playerName, this.defaultEntry);
    }

    /**
     * A single unique entry, which pairs the set format for the
     * variable with the actual displayed text for a given player.
     */
    public final class Entry implements FormatChangeListener {
        // Name of the player for who this entry is, all-lowercase
        // Is null for the default entry
        public final String playerName;
        // Whether the current value is set unique for this player
        private boolean hasCustomValue;
        // Formatted variable value for this player, input for text
        private FormattedVariableValue value;
        // Text displayed for this player, output of data
        public final TickerText text;
        // Ticker that updates the value of this entry
        public TickerBaseImpl ticker;
        // What other entries display the text of this entry
        public Set<Entry> declaring;
        // Whether this entry's text value is currently being computed
        private boolean isComputing;

        // Initializes the default entry for a variable
        private Entry() {
            String variableName = getVariableName();

            this.playerName = null;
            this.hasCustomValue = false;
            this.value = FormattedVariableValue.createDefaultValue(variableName);
            this.text = TickerText.createDefaultValue(variableName);
            this.ticker = new DefaultTicker(this);
            this.declaring = Collections.emptySet();
            this.isComputing = false;
        }

        // Only used to initialize an entry, reads from the default value
        // Note that no events fire during construction, which should be
        // done by the caller
        private Entry(String playerName) {
            this.playerName = playerName;
            this.hasCustomValue = false;
            this.value = defaultEntry.value;
            this.text = defaultEntry.text.clone();
            this.ticker = defaultEntry.ticker;
            this.declaring = Collections.emptySet();
            this.isComputing = false;
        }

        /**
         * Gets the variable value map in which this entry sits
         *
         * @return map owner
         */
        public VariableValueMap getValueMap() {
            return VariableValueMap.this;
        }

        /**
         * Gets the variable this entry is part of
         *
         * @return variable
         */
        public VariableImpl getVariable() {
            return VariableValueMap.this.getVariable();
        }

        /**
         * Gets the name of the variable this entry is representing
         *
         * @return variable name
         */
        public String getVariableName() {
            return VariableValueMap.this.getVariable().getName();
        }

        /**
         * Gets the currently assigned value format for this entry
         *
         * @return current format value
         */
        public String getValue() {
            return this.value.getFormat();
        }

        /**
         * Whether this entry has a unique value assigned specifically
         * for this player. When false, the default value is used instead.
         *
         * @return True if a custom value was set
         */
        public boolean hasCustomValue() {
            return this.hasCustomValue;
        }

        /**
         * Sets a new value for this entry.
         * Internally refreshes what variables are declared for this entry.
         *
         * @param newValue New value
         */
        public void setValue(String newValue) {
            this.setValue(FormattedVariableValue.decode(this, newValue));
        }

        /**
         * Sets a new formatted value for this entry.
         * Internally refreshes what variables are declared for this entry.
         *
         * @param value New formatted value
         */
        public void setValue(FormattedVariableValue value) {
            // Dunno, might happen
            if (this.value == value) {
                return;
            }

            // Detect changes in declared variables for this entry
            FormattedVariableValue oldValue = this.value;
            this.value = value;
            FormatChangeListener.detectChanges(oldValue, value, this);

            if (this.isDefaultEntry()) {
                // If this is the default entry, also update other player
                // entries that show the default value
                for (Entry e : VariableValueMap.this.byPlayer.values()) {
                    if (!e.hasCustomValue) {
                        e.setValue(value.cloneFor(e));
                        e.hasCustomValue = false;
                    }
                }
            } else {
                // No longer the default value now a value has been set
                this.hasCustomValue = true;
            }

            // Recalculate text
            this.computePlayerText(true);
        }

        /**
         * Gets a unique ticker instance for this entry and this entry alone
         *
         * @return player-specific ticker
         */
        public TickerBaseImpl getPlayerTicker() {
            if (this.ticker instanceof DefaultTicker) {
                this.ticker = new SinglePlayerTicker(this.ticker, this);

                // Track the number of player entries that uses tickers
                if (this.ticker.isTicking()) {
                    this.getValueMap().notifyPlayerEntryTickedChanged(
                            (SinglePlayerTicker) this.ticker, this);
                }
            }
            return this.ticker;
        }

        /**
         * Gets the current text value of this entry for the player.
         * If currently being calculated, a default placeholder is returned
         * instead.
         *
         * @return text
         */
        public String getText() {
            return this.isComputing ? ("%" + getVariableName() + "%") : this.text.get();
        }

        /**
         * Gets whether this is the default entry. The default entry
         * is what is displayed to players when no player-specific text
         * is used.
         *
         * @return True if this is the default entry
         */
        public boolean isDefaultEntry() {
            return playerName == null;
        }

        /**
         * Creates a new player variable that manages this entry
         *
         * @return new player variable
         */
        public PlayerVariable toPlayerVariable() {
            return new PlayerVariableImpl(this);
        }

        /**
         * Computes an up-to-date text value for this entry,
         * automatically resolving variables declared in the
         * variable value. This value is calculated for this
         * player alone.<br>
         * <br>
         * If infinite recursion is detected,
         * the variable value computed is set to the default value.
         * 
         * @param computeSelf Whether to recalculate the text value of
         *        this variable, or to only update variables that declare
         *        this one
         */
        public void computePlayerText(boolean computeSelf) {
            // Tells this entry and all entries that depend on this entry's
            // text value that the new value is being computed right now.
            this.startComputing();

            if (computeSelf) {
                // Compute them all, which sets computing back to false
                this.compute();
            } else {
                // Skip the text of itself, only do declaring ones
                this.isComputing = false;
                for (Entry e : this.declaring) {
                    e.compute();
                }

                // We didn't re-compute ourselves, but the text did change
                // Make sure to send another update
                this.applyToAll();
            }
        }

        // Marks the entry as in the state of being computed, recursively
        private void startComputing() {
            if (!this.isComputing) {
                this.isComputing = true;

                // Recurse
                for (Entry e : this.declaring) {
                    e.startComputing();
                }
            }
        }

        // Performs the text re-computation recursively
        private void compute() {
            if (this.isComputing) {
                // Calculate the new text value from the variable data value
                // If while mapping variable name to value, the entry for it
                // is being computed, uses a placeholder default value instead.
                this.text.setTo(this.value.computeText());

                // Done! Set computing back to false to prevent weird loops
                this.isComputing = false;

                // Update all signs showing this variable
                this.applyToAll();

                // Refresh all variables that declare this variable
                for (Entry e : this.declaring) {
                    e.compute();
                }
            }
        }

        // Update all signs showing this variable
        private void applyToAll() {
            try (ImplicitlySharedList<LinkedSign> signs = getVariable().getBoundTo().clone()) {
                this.apply(signs);
            }
        }

        // Applies (updated) text to the signs that show it
        private void apply(Collection<LinkedSign> signs) {
            if (signs.isEmpty()) {
                return;
            }

            VariableTextPlayerFilter filter;
            if (this.playerName != null) {
                // Only for one player
                filter = VariableTextPlayerFilter.only(this.playerName);
            } else {
                // Only for all players that do not have their own entry
                filter = VariableTextPlayerFilter.allExcept(VariableValueMap.this.byPlayer.keySet());
            }
            for (LinkedSign sign : signs) {
                sign.setText(this.text.get(), this.ticker.hasWrapAround(), filter);
            }
        }

        @Override
        public void onVariableDeclared(Entry entry) {
            // If this is the default entry and it shows an entry
            // with player-specific values, create player-specific
            // entries for ourselves for every player-specific value
            // that exists.
            if (this.isDefaultEntry()) {
                for (Entry e : entry.getValueMap().getPlayerEntries()) {
                    VariableValueMap.this.getPlayerEntry(e.playerName);
                }
            }

            // Update the declaring set of the entry this entry declares
            if (entry.declaring.isEmpty()) {
                entry.declaring = new HashSet<Entry>();
            }
            entry.declaring.add(this);
        }

        @Override
        public void onVariableUndeclared(Entry entry) {
            // Update the declaring set of the entry this entry declares
            if (!entry.declaring.isEmpty()
                    && entry.declaring.remove(this)
                    && entry.declaring.isEmpty())
            {
                entry.declaring = Collections.emptySet();
            }
        }
    }
}
