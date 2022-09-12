package com.bergerkiller.bukkit.sl.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Maps keys to values using an ordinary HashMap, but also tracks
 * a flat List of values for fast iteration. It is safe to iterate
 * the values while new values are put / removed.
 */
public class ConcurrentMapList<K, V> {
    private final Map<K, ValueInfo<V>> map = new HashMap<>();
    @SuppressWarnings("unchecked")
    private ValueInfo<V>[] values = new ValueInfo[16];
    private int valuesLength = 0;
    private boolean isIterating = false;

    /**
     * Gets the number of elements stored in this map
     *
     * @return element count
     */
    public int size() {
        return map.size();
    }

    /**
     * Gets whether this map is empty
     *
     * @return True if empty
     */
    public boolean isEmpty() {
        return map.isEmpty();
    }

    /**
     * Clears all the contents of this map. Anyone iterating right now will break
     * out at the very next (now-removed) value.
     */
    public void clear() {
        for (ValueInfo<V> valueInfo : map.values()) {
            valueInfo.removed = true;
        }
        map.clear();
        cleanupValuesCheck();
    }

    /**
     * Checks whether a value is mapped to a key
     *
     * @param key Key to check
     * @return True if a value is mapped to key
     */
    public boolean containsKey(Object key) {
        return map.containsKey(key);
    }

    /**
     * Gets the value mapped to a key
     *
     * @param key Key
     * @return Value mapped to key, or null if not stored
     */
    public V get(Object key) {
        ValueInfo<V> valueInfo = map.get(key);
        return (valueInfo == null) ? null : valueInfo.value;
    }

    /**
     * Maps a new value to a key. Returns the previous value.
     * Anyone currently iterating will not yet see the new value.
     * They might skip iterating over a previous value if that was
     * overwritten.
     *
     * @param key Key to map to
     * @param value Value to store
     * @return Previous value mapped to key
     */
    public V put(K key, V value) {
        ValueInfo<V> newInfo = new ValueInfo<V>(value);
        ValueInfo<V> prev = map.put(key, newInfo);
        addToValues(newInfo);
        if (prev != null) {
            prev.removed = true;
            cleanupValuesCheck();
            return prev.value;
        } else {
            return null;
        }
    }

    /**
     * Gets and removes a value mapped to a key. Anyone currently iterating
     * will no longer encounter the now-removed value, if removed.
     *
     * @param key Key to remove the value mapped to
     * @return Value mapped to key at time of removal
     */
    public V remove(Object key) {
        ValueInfo<V> valueInfo = map.remove(key);
        if (valueInfo != null) {
            valueInfo.removed = true;
            cleanupValuesCheck();
            return valueInfo.value;
        } else {
            return null;
        }
    }

    /**
     * Iterates all the values stored in this map, efficiently. Guards are in place
     * to prevent concurrent modification problems. it is safe to add new values
     * to this map while iterating, which will not be iterated over. It is also safe
     * to remove values, which will not be iterated over anymore if not yet visited.
     *
     * @param action Callback to receive all values
     */
    public void forEachValue(Consumer<? super V> action) {
        // Only handle entry removal when someone else isn't already iterating
        // This avoids weird nonsense where iteration breaks midway
        boolean isFirstiterator = !isIterating;
        isIterating = true;
        try {
            // Tracks the first value we encountered that was removed
            // Saves on some processing during later cleanup
            int offsetFirstRemoved = -1;

            // Tracks the number of values we encountered that have been removed
            int numRemoved = 0;

            // Simply iterate the values in a first pass, where we do not modify the array
            // During iteration more values might be inserted!
            {
                ValueInfo<V>[] values = this.values;
                int numValues = this.valuesLength;
                int i = -1;
                while (++i < numValues) {
                    ValueInfo<V> valueInfo = values[i];
                    if (valueInfo.removed) {
                        numRemoved++;
                        if (offsetFirstRemoved == -1) {
                            offsetFirstRemoved = i;
                        }
                    } else {
                        action.accept(valueInfo.value);
                    }
                }
            }

            if (isFirstiterator && numRemoved > 0) {
                this.cleanupValues(offsetFirstRemoved, numRemoved);
            }
        } finally {
            if (isFirstiterator) {
                isIterating = false;
            }
        }
    }

    /**
     * Uses {@link #forEachValue(Consumer)} to collect all values to a new List copy
     *
     * @return List of values (modifiable)
     */
    public List<V> toListCopy() {
        ArrayList<V> result = new ArrayList<>(map.size());
        this.forEachValue(result::add);
        return result;
    }

    private void addToValues(ValueInfo<V> valueInfo) {
        // Grow array if needed
        int numValues = this.valuesLength;
        if (numValues >= this.values.length) {
            this.values = Arrays.copyOf(this.values, ((numValues + 1) * 4) / 3);
        }
        this.values[numValues] = valueInfo;
        this.valuesLength = numValues + 1;
    }

    /**
     * Checks whether the number of removed values is significant and orders a cleanup
     * right away to free some memory. Only important when {@link #forEachValue(Consumer)}
     * isn't called often enough.
     */
    private void cleanupValuesCheck() {
        // If currently iterating, it is not safe to remove values
        if (isIterating) {
            return;
        }

        // If number of removed values is very large, perform a cleanup right away
        // In practise and proper operation, this will never really run
        int numRemovedValues = this.valuesLength - this.map.size();
        if (numRemovedValues >= 256) {
            cleanupValues(0, numRemovedValues);
        }
    }

    /**
     * Removes removed values from the values array, reducing the array size if
     * sufficient values have been removed.
     *
     * @param offsetFirstRemoved Start offset to clean up values at
     * @param numRemoved Number of values after the offset that have been removed
     */
    @SuppressWarnings("unchecked")
    private void cleanupValues(int offsetFirstRemoved, int numRemoved) {
        ValueInfo<V>[] values = this.values;
        int numValues = this.valuesLength;
        int newCapacity = ((numValues - numRemoved) * 4) / 3;
        if ((2*newCapacity) < values.length && newCapacity >= 16) {
            // There's half the amount of values than the values array can store
            // Create a completely new array with only the non-removed values kept
            // This frees up some memory
            ValueInfo<V>[] newValues = new ValueInfo[newCapacity];
            System.arraycopy(values, 0, newValues, 0, offsetFirstRemoved);

            int src = offsetFirstRemoved - 1;
            int dst = offsetFirstRemoved - 1;
            while (++src < numValues) {
                ValueInfo<V> valueInfo = values[src];
                if (!valueInfo.removed) {
                    newValues[++dst] = valueInfo;
                }
            }
            this.values = newValues;
            this.valuesLength = dst + 1;
        } else {
            // Move removed entries back, overwriting older array values
            // We can start iteration at the offset where we know a removed entry exists
            int src = offsetFirstRemoved - 1;
            int dst = offsetFirstRemoved - 1;
            while (++src < numValues) {
                ValueInfo<V> valueInfo = values[src];
                if (!valueInfo.removed) {
                    values[++dst] = valueInfo;
                }
            }

            // Null out the remaining value slots
            Arrays.fill(values, dst + 1, numValues, null);
            this.valuesLength = dst + 1;
        }
    }

    private static final class ValueInfo<V> {
        public V value;
        public boolean removed;

        public ValueInfo(V value) {
            this.value = value;
            this.removed = false;
        }
    }
}
