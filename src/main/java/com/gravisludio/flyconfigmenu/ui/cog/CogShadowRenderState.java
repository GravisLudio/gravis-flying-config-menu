package com.gravisludio.flyconfigmenu.ui.cog;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * One frame of the cog behind the config screens.
 *
 * @param spin  rotation around the cog's own axis, in degrees
 * @param scale GUI pixels per block
 */
public record CogShadowRenderState(
    BlockState block,
    float spin,
    int x0,
    int y0,
    int x1,
    int y1,
    float scale,
    @Nullable ScreenRectangle scissorArea,
    @Nullable ScreenRectangle bounds
) implements PictureInPictureRenderState {
}
