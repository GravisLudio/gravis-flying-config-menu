package com.gravisludio.flyconfigmenu.client;

import com.gravisludio.flyconfigmenu.ui.cog.CogShadowRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;

/**
 * Client entrypoint. The configs are found by the {@code Builder.create} mixin; here we only register the renderer of the
 * cog that turns behind the screens.
 */
public class ConfigMenuClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        PictureInPictureRendererRegistry.register(context -> new CogShadowRenderer());
    }
}
