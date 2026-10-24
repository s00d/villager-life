package com.vnap.village.life;

/** One villager's switch: while a life task runs, the gated vanilla strolls neither start nor keep running. */
public final class LifeGate {
    boolean busy;

    public boolean busy() {
        return busy;
    }
}
