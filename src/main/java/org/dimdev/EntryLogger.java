package org.dimdev;

import net.fabricmc.api.ClientModInitializer;

public class EntryLogger implements ClientModInitializer {

    @Override public void onInitializeClient() {
        new Throwable().printStackTrace();
    }
}
