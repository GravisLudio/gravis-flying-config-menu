package com.gravisludio.flyconfigmenu.ui.cog;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * One block drawn in the GUI through {@link CogShadowRenderer}: the shadow cog behind the config screens and the cogs of
 * the Create menu.
 *
 * @param xRot  rotation around X, in degrees (applied after Z)
 * @param yRot  rotation around the block's own vertical axis, in degrees (the spin of a cog)
 * @param zRot  rotation around Z, in degrees (applied first)
 * @param tint  ARGB the drawn texture is multiplied by: {@code 0x60000000} gives a translucent black silhouette, {@code -1} the block as it is
 * @param scale GUI pixels per block
 */
public record CogShadowRenderState(
    BlockState block,
    float xRot,
    float yRot,
    float zRot,
    int tint,
    int x0,
    int y0,
    int x1,
    int y1,
    float scale,
    @Nullable ScreenRectangle scissorArea,
    @Nullable ScreenRectangle bounds
) implements PictureInPictureRenderState {
}
