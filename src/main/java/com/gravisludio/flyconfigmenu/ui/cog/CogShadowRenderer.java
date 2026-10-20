package com.gravisludio.flyconfigmenu.ui.cog;

import com.gravisludio.flyconfigmenu.ConfigMenu;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Draws a block into its own texture (Minecraft's picture-in-picture path, registered through Fabric API) and pastes that
 * texture onto the screen tinted translucent black, so only its silhouette shows: the "shadow" cog of Create's original
 * config menu.
 */
public final class CogShadowRenderer extends PictureInPictureRenderer<CogShadowRenderState> {
    private static final Field TEXTURE_VIEW = textureViewField();
    private static final int[] NO_TINTS = new int[0];

    private final RandomSource random = RandomSource.create();
    private final List<BlockStateModelPart> parts = new ArrayList<>();

    private static Field textureViewField() {
        try {
            Field field = PictureInPictureRenderer.class.getDeclaredField("textureView");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException e) {
            ConfigMenu.LOGGER.warn("The config menu cog cannot be drawn: {}", e.toString());
            return null;
        }
    }

    @Override
    public Class<CogShadowRenderState> getRenderStateClass() {
        return CogShadowRenderState.class;
    }

    /** Puts the origin in the middle of the texture instead of at its bottom (that is for entities standing on the ground). */
    @Override
    protected float getTranslateY(int height, int guiScale) {
        return height / 2f;
    }

    @Override
    protected void renderToTexture(CogShadowRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.ZP.rotationDegrees(state.zRot()));
        poseStack.mulPose(Axis.XP.rotationDegrees(state.xRot()));
        poseStack.mulPose(Axis.YP.rotationDegrees(state.yRot()));
        poseStack.translate(-0.5f, -0.5f, -0.5f);
        // Minecraft's own block-model path: unlike Create Fly's CachedBuffers (which need Flywheel's state from a loaded
        // world and crash at the title screen), this works on any screen. Light and color do not matter: only the
        // silhouette survives the black tint of the blit.
        try {
            BlockStateModel model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state.block());
            parts.clear();
            random.setSeed(42L);
            model.collectParts(random, parts);
            if (!parts.isEmpty()) {
                collector.submitBlockModel(poseStack, Sheets.cutoutBlockItemSheet(), new ArrayList<>(parts), NO_TINTS,
                    LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
            }
        } catch (RuntimeException e) {
            // A decoration must never take the game down: switch it off for the session.
            ConfigMenu.LOGGER.warn("The config menu cog could not be drawn and is now off", e);
            CogShadow.disable();
        }
        poseStack.popPose();
    }

    @Override
    protected void blitTexture(CogShadowRenderState state, GuiRenderState guiRenderState) {
        GpuTextureView view = textureView();
        if (view == null) {
            return;
        }
        guiRenderState.addBlitToCurrentLayer(new BlitRenderState(
            RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA,
            TextureSetup.singleTexture(view, RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST)),
            state.pose(),
            state.x0(),
            state.y0(),
            state.x1(),
            state.y1(),
            0.0F,
            1.0F,
            1.0F,
            0.0F,
            state.tint(),
            state.scissorArea(),
            null
        ));
    }

    private GpuTextureView textureView() {
        if (TEXTURE_VIEW == null) {
            return null;
        }
        try {
            return (GpuTextureView) TEXTURE_VIEW.get(this);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    @Override
    protected String getTextureLabel() {
        return "config menu cog";
    }
}
