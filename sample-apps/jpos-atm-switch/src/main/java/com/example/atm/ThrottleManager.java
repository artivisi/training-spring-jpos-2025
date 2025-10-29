package com.example.atm;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Shared throttle state manager.
 * Used by QueueMonitor to signal throttle state and by RequestListener to check it.
 */
public class ThrottleManager {
    private static final AtomicBoolean throttled = new AtomicBoolean(false);

    public static void setThrottled(boolean value) {
        throttled.set(value);
    }

    public static boolean isThrottled() {
        return throttled.get();
    }
}
