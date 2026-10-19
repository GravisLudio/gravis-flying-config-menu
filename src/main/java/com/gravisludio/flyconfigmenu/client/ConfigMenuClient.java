package com.gravisludio.flyconfigmenu.client;

import net.fabricmc.api.ClientModInitializer;

/**
 * Client entrypoint. The mod does its work through the {@code Builder.create} mixin and the screens it opens, so there
 * is nothing to set up here yet; the entrypoint exists so Fabric treats this as a client mod with a real initializer.
 */
public class ConfigMenuClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
    }
}
