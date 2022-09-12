package com.bergerkiller.bukkit.sl;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import com.bergerkiller.bukkit.sl.util.ConcurrentMapList;

public class ConcurrentMapListTest {

    @Test
    public void testPutAndToList() {
        ConcurrentMapList<String, String> map = new ConcurrentMapList<>();
        assertNull(map.put("a", "A"));
        assertNull(map.put("b", "B"));
        assertNull(map.put("c", "C"));
        assertEquals(Arrays.asList("A", "B", "C"), map.toListCopy());
    }

    @Test
    public void testPutRemoveAndToList() {
        ConcurrentMapList<String, String> map = new ConcurrentMapList<>();
        assertNull(map.put("a", "A"));
        assertNull(map.put("b", "B"));
        assertNull(map.put("c", "C"));
        assertEquals("B", map.remove("b"));
        assertNull(map.put("d", "D"));
        assertNull(map.put("b", "B"));
        assertEquals(Arrays.asList("A", "C", "D", "B"), map.toListCopy());
    }

    @Test
    public void testPutAndClear() {
        ConcurrentMapList<String, String> map = new ConcurrentMapList<>();
        assertNull(map.put("a", "A"));
        assertNull(map.put("b", "B"));
        assertNull(map.put("c", "C"));
        map.clear();
        assertEquals(Collections.emptyList(), map.toListCopy());
    }

    @Test
    public void testRemoveDuringForEach() {
        ConcurrentMapList<String, String> map = new ConcurrentMapList<>();
        assertNull(map.put("a", "A"));
        assertNull(map.put("b", "B"));
        assertNull(map.put("c", "C"));

        // Would normally iterate a -> b -> c
        // We remove b mid-iteration, so we should only see a -> c
        final List<String> result = new ArrayList<>();
        map.forEachValue(v -> {
            if (map.containsKey("b")) {
                assertEquals(Arrays.asList("A", "B", "C"), map.toListCopy());
            } else {
                assertEquals(Arrays.asList("A", "C", "D"), map.toListCopy());
            }

            map.remove("b");
            map.put("d", "D");

            assertEquals(Arrays.asList("A", "C", "D"), map.toListCopy());
            result.add(v);
        });

        // The D we added in the loop should not show up here
        assertEquals(Arrays.asList("A", "C"), result);
    }
}
