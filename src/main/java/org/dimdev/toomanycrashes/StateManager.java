package org.dimdev.toomanycrashes;

import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Allows registering objects to be reset after a crash. Objects registered
 * use WeakReferences, so they will be garbage-collected despite still being
 * registered here.
 */
public class StateManager {
    private static final Set<WeakReference<Resettable>> RESETTABLES = new HashSet<>();

    public static void resetStates() {
        Iterator<WeakReference<Resettable>> iterator = RESETTABLES.iterator();
        while (iterator.hasNext()) {
            WeakReference<Resettable> ref = iterator.next();
            if (ref.get() != null) {
                ref.get().resetState();
            } else {
                iterator.remove();
            }
        }
    }

    public interface Resettable {
        default void register() {
            RESETTABLES.add(new WeakReference<>(this));
        }

        void resetState();
    }
}
