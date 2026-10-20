package com.gravisludio.flyconfigmenu.client;

import com.gravisludio.flyconfigmenu.ui.cog.CogShadowRenderer;
import com.gravisludio.flyconfigmenu.ui.menu.GogglesButton;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;

/**
 * Client entrypoint. The configs are found by the {@code Builder.create} mixin; here we register the renderer of the cogs
 * and put Create's goggles button back on the title screen and the pause menu.
 */
public class ConfigMenuClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        PictureInPictureRendererRegistry.register(context -> new CogShadowRenderer());
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> GogglesButton.addTo(screen));
    }
}
