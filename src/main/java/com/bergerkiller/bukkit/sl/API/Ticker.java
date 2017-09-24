package com.bergerkiller.bukkit.sl.API;

import java.util.ArrayList;
import java.util.List;

import com.bergerkiller.bukkit.common.ToggledState;
import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.sl.StyledCharacter;
import com.bergerkiller.bukkit.sl.StyledString;

/**
 * Ticks text on a per-tick basis to allow textual animations
 */
public class Ticker {
    //These two are used to check if it is updated
    protected String[] players = null;
    protected ToggledState checked = new ToggledState();
    private final StyledString valueElements = new StyledString();
    private String value = "";
    private ArrayList<Pause> pauses = new ArrayList<Pause>();
    private int pauseindex = 0;
    public long interval = 1;
    private long counter = 0;
    public TickMode mode = TickMode.NONE;

    public Ticker(String initialvalue, String player) {
        this(initialvalue, new String[] {player});
    }

    public Ticker(String initialvalue, String[] players) {
        this(initialvalue);
        this.players = players;
    }

    public Ticker(String initialvalue) {
        this.value = initialvalue;
        this.valueElements.setToString(initialvalue);
    }

    /**
     * Gets whether this Ticker is actively ticking (not NONE)
     * 
     * @return True if the mode is not NONE
     */
    public boolean isTicking() {
        return mode != TickMode.NONE;
    }

    /**
     * Gets whether this ticker (in the current mode) wraps text around on signs
     * 
     * @return True if text should be wrapped around
     */
    public boolean hasWrapAround() {
        return mode == TickMode.LEFT || mode == TickMode.RIGHT;
    }

     boolean isShared() {
         return this.players == null || this.players.length != 1;
     }
 
    private boolean isPaused() {
        Pause p = getNextPause();
        return p != null && p.active;
    }

    private Pause getNextPause() {
        if (pauses.isEmpty()) {
            return null;
        }
        Pause p = pauses.get(pauseindex);

        //Play the next part as long possible
        if (p.currentdelay >= p.delay) {
            //Not playing anymore
            p.active = true;
            if (p.currentduration > p.duration) {
                //reset and go to next pause
                p.currentdelay = 0;
                p.currentduration = 0;
                pauseindex++;
                if (pauseindex > pauses.size() - 1) {
                    pauseindex = 0;
                }
            } else {
                p.currentduration++;
            }
        } else {
            p.active = false;
            p.currentdelay++;
        }
        return p;
    }

    /**
     * Adds a pause to this ticker
     * 
     * @param delay in ticks before the pause occurs
     * @param duration in ticks of the pause
     */
    public void addPause(int delay, int duration) {
        Pause p = new Pause();
        p.delay = delay;
        p.duration = duration;
        this.pauses.add(p);
    }

    /**
     * Clears all the ticker pauses
     */
    public void clearPauses() {
        this.pauses.clear();
    }

     private boolean countNext() {
        this.counter++;
        if (this.counter >= this.interval) {
            this.counter = 0;
            return true;
        } else {
            return false;
        }
    }
 
    boolean update() {
        if (!this.checked.set() || !this.countNext() || this.isPaused() || !this.isTicking()) {
            return false;
        }
        this.nextText();
        return true;
    }

     /**
      * Gets the next ticker value depending on the settings
      * 
      * @return Next ticker value
      */
     public String next() {
         if (countNext() && !isPaused()) {
             return this.nextText();
         } else {
             return this.current();
         }
     }
 
    /**
     * Resets the settings of this ticker
     * 
     * @param value to set the text to
     */
     public void reset(String value) {
         this.pauseindex = 0;
         this.counter = 0;
         this.valueElements.setToString(value);
         this.value = value;
         for (Pause p : this.pauses) {
             p.currentdelay = 0;
             p.currentduration = 0;
             p.active = false;
         }
     }
  
    private String nextText() {
        if (this.mode == TickMode.LEFT) {
            return this.left();
        } else if (this.mode == TickMode.RIGHT) {
            return this.right();
        } else if (this.mode == TickMode.BLINK) {
            return this.blink();
        } else {
            return this.current();
        }
    }

    /**
     * Gets the next value when blinking on and off
     * 
     * @return Next value
     */
    public String blink() {
        for (int i = 0; i < this.value.length(); i++) {
            if (this.value.charAt(i) != ' ') {
                // Now we go with 'off' - all spaces
                this.value = StringUtil.getFilledString(" ", this.value.length());
                return this.value;
            }
        }
        // Now we go with 'on' - show text
        this.value = this.valueElements.toString();
        return this.value;
    }

    /**
     * Gets the next value when ticking the text to the left
     * 
     * @return Next value
     */
    public String left() {
        // Translate elements one to the left
        if (this.valueElements.size() >= 2) {
            StyledCharacter first = this.valueElements.remove(0);
            this.valueElements.add(first);
            // Update
            this.value = this.valueElements.toString();
        }
        return this.value;
    }

    /**
     * Gets the next value when ticking the text to the right
     * 
     * @return Next value
     */
    public String right() {
        // Translate elements one to the right
        if (this.valueElements.size() >= 2) {
            StyledCharacter last = this.valueElements.remove(this.valueElements.size() - 1);
            this.valueElements.add(0, last);
            // Update
            this.value = this.valueElements.toString();
        }
        return this.value;
    }

     /**
      * Gets the current value of this ticker
      * 
      * @return Current ticker value
      */
    public String current() {
        return this.value;
    }

     /**
      * Loads ticker information
      * 
      * @param node to load from
      */
     public void load(ConfigurationNode node) {
         this.mode = node.get("ticker", TickMode.NONE);
         this.interval = (long) node.get("tickerInterval", (int) this.interval);
         if (node.contains("tickerInterval")) {
             List<Integer> delays = node.getList("pauseDelays", Integer.class);
             List<Integer> durations = node.getList("pauseDurations", Integer.class);
             if (delays.size() == durations.size()) {
                 for (int i = 0; i < delays.size(); i++) {
                     int delay = delays.get(i);
                     int duration = durations.get(i);
                     this.addPause(delay, duration);
                 }
             }
         }
     }

     /**
      * Saves this ticker
      * 
      * @param node to save in
      */
     public void save(ConfigurationNode node) {
         node.set("ticker", this.mode == TickMode.NONE ? null : this.mode);
         node.set("tickerInterval", this.interval == -1 ? null : (int) this.interval);
         List<Integer> delays = null;
         List<Integer> durations = null;
         if (this.pauses.size() > 0) {
             delays = new ArrayList<Integer>();
             durations = new ArrayList<Integer>();
              for (Pause p : this.pauses) {
                  delays.add(p.delay);
                  durations.add(p.duration);
              }
         }
         node.set("pauseDelays", delays);
         node.set("pauseDurations", durations);
     }
 
    @Override
    public Ticker clone() {
        Ticker t = new Ticker(this.value, this.players);
        t.pauseindex = this.pauseindex;
        t.interval = this.interval;
        t.counter = this.counter;
        t.mode = this.mode;
        for (Pause p : this.pauses) {
            t.pauses.add(p.clone());
        }
        return t;
    }

    /**
     * Represents a pause between updating text
     */
    private static class Pause {
        public int currentdelay = 0;
        public int delay;
        public int currentduration = 0;
        public int duration;
        public boolean active = false;
        @Override
        public Pause clone() {
            Pause p = new Pause();
            p.currentdelay = this.currentdelay;
            p.delay = this.delay;
            p.currentduration = this.duration;
            p.duration = this.duration;
            p.active = this.active;
            return p;
        }
    }

}
