package com.gravisludio.flyconfigmenu.ui.menu;

import com.gravisludio.flyconfigmenu.ConfigMenu;
import com.gravisludio.flyconfigmenu.ui.RootScreen;
import com.gravisludio.flyconfigmenu.ui.cog.CogShadow;
import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.catnip.theme.Color;
import com.zurrtum.create.client.catnip.gui.AbstractSimiScreen;
import com.zurrtum.create.client.catnip.gui.ScreenOpener;
import com.zurrtum.create.client.catnip.gui.element.BoxElement;
import com.zurrtum.create.client.foundation.gui.AllGuiTextures;
import com.zurrtum.create.client.ponder.foundation.ui.PonderTagIndexScreen;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The screen behind Create's goggles button, which Create Fly dropped: the logo between two turning cogs, the name and
 * version, and Configure / Ponder Index / store pages / Support / Report Issues. Layout and texts follow Create's
 * {@code CreateMainMenuScreen}; the store and issue links point to Create Fly's own pages, the support link to the
 * Create team's.
 *
 * <p>One difference: Create showed its own panorama behind it. Minecraft 26.2 draws the title panorama itself from a
 * fixed cube map, so this shows the vanilla one.
 */
public class CreateMenuScreen extends AbstractSimiScreen {
    public static final String CURSEFORGE_LINK = "https://www.curseforge.com/minecraft/mc-mods/create-fly";
    public static final String MODRINTH_LINK = "https://modrinth.com/mod/create-fly";
    public static final String ISSUE_TRACKER_LINK = "https://github.com/ZurrTum/Create-Fly/issues";
    public static final String SUPPORT_LINK = "https://github.com/Creators-of-Create/Create/wiki/Supporting-the-Project";

    private final Screen parent;
    private long firstRenderTime;
    private Button ponderIndex;
    private BlockState largeCog;
    private BlockState smallCog;

    public CreateMenuScreen(Screen parent) {
        super(Component.literal(ConfigMenu.displayName("create")));
        this.parent = parent;
    }

    /** The configure button, for tests. */
    private Button configure;

    public Button configureButton() {
        return configure;
    }

    @Override
    protected void init() {
        super.init();
        int yStart = height / 4 + 40;
        int center = width / 2;

        addRenderableWidget(Button.builder(Component.translatable("create.menu.return"), b -> onClose())
            .bounds(center - 100, yStart + 92, 200, 20).build());
        configure = addRenderableWidget(Button.builder(Component.translatable("create.menu.configure"),
                b -> minecraft.gui.setScreen(new RootScreen(this, "create")))
            .bounds(center - 100, yStart + 8, 200, 20).build());

        ponderIndex = Button.builder(Component.translatable("create.menu.ponder_index"), b -> ScreenOpener.open(new PonderTagIndexScreen()))
            .bounds(center + 2, yStart + 32, 98, 20).build();
        ponderIndex.active = minecraft.level != null;
        if (!ponderIndex.active) {
            ponderIndex.setTooltip(Tooltip.create(Component.translatable("create.menu.only_ingame")));
        }
        addRenderableWidget(ponderIndex);

        addRenderableWidget(new PlatformButton(center - 100, yStart + 32, 49, 20, AllGuiTextures.CURSEFORGE_LOGO, 0.085f,
            b -> ConfirmLinkScreen.confirmLinkNow(this, CURSEFORGE_LINK),
            Component.literal("CurseForge").withStyle(s -> s.withColor(0xFC785C).withBold(true))));
        addRenderableWidget(new PlatformButton(center - 50, yStart + 32, 49, 20, AllGuiTextures.MODRINTH_LOGO, 0.0575f,
            b -> ConfirmLinkScreen.confirmLinkNow(this, MODRINTH_LINK),
            Component.literal("Modrinth").withStyle(s -> s.withColor(0x3FD32B).withBold(true))));

        addRenderableWidget(Button.builder(Component.translatable("create.menu.support"), b -> ConfirmLinkScreen.confirmLinkNow(this, SUPPORT_LINK))
            .bounds(center - 100, yStart + 68, 98, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("create.menu.report_bugs"), b -> ConfirmLinkScreen.confirmLinkNow(this, ISSUE_TRACKER_LINK))
            .bounds(center + 2, yStart + 68, 98, 20).build());
    }

    /** No blur, no menu texture: the panorama at the title screen, the dimmed world in game. */
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        if (minecraft.level == null) {
            extractPanorama(graphics, a);
        } else {
            graphics.fill(0, 0, width, height, 0xC0_101010);
        }
    }

    @Override
    protected void renderWindowBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
    }

    @Override
    protected void renderWindow(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        if (firstRenderTime == 0L) {
            firstRenderTime = Util.getMillis();
        }
        float alpha = Mth.clamp((Util.getMillis() - firstRenderTime) / 1000f, 0f, 1f);
        float slide = alpha * alpha / 2f + .5f;
        float time = Util.getMillis();
        if (largeCog == null) {
            largeCog = AllBlocks.LARGE_COGWHEEL.defaultBlockState();
            smallCog = AllBlocks.COGWHEEL.defaultBlockState();
        }

        // A large cog on each side of the logo, sliding outwards as the screen opens, with a small one meshing above it.
        for (int side : new int[]{1, -1}) {
            int cx = (int) (width / 2f - side * 42 * slide);
            CogShadow.renderBlock(graphics, largeCog, cx, 66, 80, 24, 45, time / 32f * side, 0, -1);
            CogShadow.renderBlock(graphics, smallCog, cx - side * 24, 49, 48, 24, 45, time / -16f * side + 22.5f, 0, -1);
        }

        graphics.pose().pushMatrix();
        graphics.pose().translate(width / 2f - 32, 32);
        graphics.pose().scale(0.25f, 0.25f);
        AllGuiTextures.LOGO.render(graphics, 0, 0);
        graphics.pose().popMatrix();

        new BoxElement()
            .withBackground(0x88_000000)
            .flatBorder(new Color(0x01_000000, true))
            .at(width / 2f - 64, 88)
            .withBounds(128, 11)
            .render(graphics);

        String version = FabricLoader.getInstance().getModContainer("create")
            .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
        graphics.centeredText(font,
            Component.literal(ConfigMenu.displayName("create")).withStyle(ChatFormatting.BOLD)
                .append(Component.literal(" v" + version).withStyle(ChatFormatting.BOLD, ChatFormatting.WHITE)),
            width / 2, 89, 0xFF_E4BB67);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    /** A plain button showing a store logo instead of a label. */
    private static final class PlatformButton extends Button {
        private final AllGuiTextures icon;
        private final float scale;

        PlatformButton(int x, int y, int width, int height, AllGuiTextures icon, float scale, OnPress onPress, Component tooltip) {
            super(x, y, width, height, tooltip, onPress, DEFAULT_NARRATION);
            this.icon = icon;
            this.scale = scale;
            setTooltip(Tooltip.create(tooltip));
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            extractDefaultSprite(graphics);
            float size = 256 * scale;
            graphics.pose().pushMatrix();
            graphics.pose().translate(getX() + (width - size) / 2f, getY() + (height - size) / 2f);
            graphics.pose().scale(scale, scale);
            icon.render(graphics, 0, 0);
            graphics.pose().popMatrix();
        }
    }
}
