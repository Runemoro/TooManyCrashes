package org.dimdev.toomanycrashes;

import java.util.Set;
import net.fabricmc.loader.api.metadata.ModMetadata;

public interface PatchedCrashReport {
    Set<ModMetadata> getSuspectedMods();

    interface Element {
        String invokeGetName();

        String invokeGetDetail();
    }
}
