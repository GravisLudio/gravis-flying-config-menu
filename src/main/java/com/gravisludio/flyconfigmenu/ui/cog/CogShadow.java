package com.gravisludio.flyconfigmenu.ui.cog;

import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.client.catnip.animation.Force;
import com.zurrtum.create.client.catnip.animation.PhysicalFloat;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The large cogwheel that turns slowly behind Create's config screens and spins faster when a value is flipped or the list
 * is scrolled. Same physics as Create's menu: a steady push, drag, and a speed limit. Shared by every screen so the spin
 * carries over from one screen to the next.
 */
public final class CogShadow {
    private static final PhysicalFloat SPIN = PhysicalFloat.create().withLimit(10f).withDrag(0.3).addForce(new Force.Static(.2f));
    private static BlockState block;
    private static boolean disabled;
    /** Black at 0x60 alpha, the shade Create's menu used; the blit multiplies the cog's texture by it. */
    public static final int SHADOW_TINT = 0x60_000000;
    private static int tint = SHADOW_TINT;

    private CogShadow() {
    }

    /** Once per client tick, from the open screen. */
    public static void tick() {
        SPIN.tick();
    }

    /** A push in either direction (positive turns it forward), like flipping a value in Create's menu. */
    public static void bump(float force) {
        SPIN.bump(3, force);
    }

    public static int tint() {
        return tint;
    }

    /** For tests: draw the cog with another tint (for example opaque white, to see the real model) and back. */
    public static void setTint(int newTint) {
        tint = newTint;
    }

    /** Turns the cog off for the rest of the session (after a drawing error). */
    public static void disable() {
        disabled = true;
    }

    public static boolean isDisabled() {
        return disabled;
    }

    /** Submits the cog, centred on the screen, behind whatever is drawn after it. */
    public static void render(GuiGraphicsExtractor graphics, int width, int height, float partialTicks) {
        if (disabled) {
            return;
        }
        if (block == null) {
            block = AllBlocks.LARGE_COGWHEEL.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Y);
        }
        // As tall as the screen, like Create's: the texture is a bit larger than the screen and the cog (about 1.8 blocks
        // across) fills ~80 % of it.
        int side = Math.max(32, (int) (Math.min(width, height) * 1.3f));
        int x0 = width / 2 - side / 2;
        int y0 = height / 2 - side / 2;
        float scale = side / 2.3f;
        ScreenRectangle scissor = graphics.scissorStack.peek();
        graphics.guiRenderState.addPicturesInPictureState(new CogShadowRenderState(
            block, 22.5f, SPIN.getValue(partialTicks), 22.5f, tint, x0, y0, x0 + side, y0 + side, scale, scissor,
            PictureInPictureRenderState.getBounds(x0, y0, x0 + side, y0 + side, scissor)
        ));
    }

    /**
     * Draws any block, centred on (cx, cy), in a square of {@code side} GUI pixels: for the cogs of the Create menu.
     * Nothing is drawn after a drawing error switched the renderer off.
     */
    public static void renderBlock(GuiGraphicsExtractor graphics, BlockState state, int cx, int cy, int side, float pixelsPerBlock,
                                   float xRot, float yRot, float zRot, int tint) {
        if (disabled) {
            return;
        }
        int x0 = cx - side / 2;
        int y0 = cy - side / 2;
        ScreenRectangle scissor = graphics.scissorStack.peek();
        graphics.guiRenderState.addPicturesInPictureState(new CogShadowRenderState(
            state, xRot, yRot, zRot, tint, x0, y0, x0 + side, y0 + side, pixelsPerBlock, scissor,
            PictureInPictureRenderState.getBounds(x0, y0, x0 + side, y0 + side, scissor)
        ));
    }
}
