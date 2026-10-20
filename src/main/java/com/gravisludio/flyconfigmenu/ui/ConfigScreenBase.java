package com.gravisludio.flyconfigmenu.ui;

import com.gravisludio.flyconfigmenu.ui.cog.CogShadow;
import com.zurrtum.create.client.catnip.gui.AbstractSimiScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * What every config screen shares with Create's original menu: its backdrop, the turning cog behind the widgets, and the
 * cog's reaction to scrolling.
 *
 * <p>The backdrop is Create's, not Minecraft 26.2's: no blur and no menu texture, only a blue-grey tint over the world
 * ({@code 0xB0282C34}) or over the title panorama ({@code 0x90282C34}). Minecraft draws its own blurred, darkened
 * background before the screen and {@link AbstractSimiScreen} adds the menu texture again; both are replaced here, which
 * is why the original looks lighter and shows the cog better.
 */
public abstract class ConfigScreenBase extends AbstractSimiScreen {
    private static final int IN_WORLD_TINT = 0xB0_282C34;
    private static final int MENU_TINT = 0x90_282C34;

    protected ConfigScreenBase(Component title) {
        super(title);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        if (minecraft.level != null) {
            graphics.fill(0, 0, width, height, IN_WORLD_TINT);
        } else {
            extractPanorama(graphics, a);
            graphics.fill(0, 0, width, height, MENU_TINT);
        }
    }

    /** Only the cog: the backdrop is already drawn by {@link #extractBackground}. */
    @Override
    protected void renderWindowBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        CogShadow.render(graphics, width, height, partialTicks);
    }

    @Override
    public void tick() {
        super.tick();
        CogShadow.tick();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        CogShadow.bump((float) (-scrollY * 5));
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
