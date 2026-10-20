package com.gravisludio.flyconfigmenu.ui.menu;

import com.zurrtum.create.client.infrastructure.config.AllConfigs;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Create's small button with the goggles next to a title-screen or pause-menu button; it opens {@link CreateMenuScreen}.
 * Where it goes is read from Create Fly's own client settings, which Create Fly kept even though it dropped the button:
 * {@code mainMenuConfigButtonRow/OffsetX} and {@code ingameMenuConfigButtonRow/OffsetX} (row 0 hides it, a negative offset
 * puts it on the left of that row's button).
 */
public final class GogglesButton extends Button {
    /** Left and right button of each row, as translation keys, like Create's MenuRows (with Fabric's Mod Menu in row 3). */
    private static final List<String[]> MAIN_MENU = List.of(
        new String[]{"menu.singleplayer", "menu.singleplayer"},
        new String[]{"menu.multiplayer", "menu.multiplayer"},
        new String[]{"modmenu.title", "menu.online"},
        new String[]{"options.language", "options.accessibility"}
    );
    private static final List<String[]> INGAME_MENU = List.of(
        new String[]{"menu.returnToGame", "menu.returnToGame"},
        new String[]{"gui.advancements", "gui.stats"},
        new String[]{"menu.sendFeedback", "menu.reportBugs"},
        new String[]{"menu.options", "menu.shareToLan"},
        new String[]{"menu.returnToMenu", "menu.returnToMenu"}
    );

    private static final Identifier GOGGLES_TEXTURE = Identifier.fromNamespaceAndPath("create", "textures/item/goggles.png");

    public GogglesButton(int x, int y) {
        // The label is never drawn (the goggles are); it names the button for narration and for tests.
        super(x, y, 20, 20, Component.translatable("gravis_flying_config_menu.goggles"), b -> open(), DEFAULT_NARRATION);
    }

    private static void open() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.gui.setScreen(new CreateMenuScreen(minecraft.gui.screen()));
    }

    /**
     * Draws the goggles' item sprite straight from Create's texture. An ItemStack cannot be used here: in 26.2 item
     * components are only bound once a world is loaded, so at the title screen creating one throws ("Components not bound
     * yet") and the item would not draw. The goggles are a flat item, so the sprite looks the same as the item.
     */
    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        extractDefaultSprite(graphics);
        graphics.blit(RenderPipelines.GUI_TEXTURED, GOGGLES_TEXTURE, getX() + 2, getY() + 2, 0f, 0f, 16, 16, 16, 16);
    }

    /** Adds the button to a title or pause screen that was just built (Fabric's ScreenEvents.AFTER_INIT). */
    public static void addTo(Screen screen) {
        List<String[]> rows;
        int row;
        int offsetX;
        if (screen instanceof TitleScreen) {
            rows = MAIN_MENU;
            row = AllConfigs.client().mainMenuConfigButtonRow.get();
            offsetX = AllConfigs.client().mainMenuConfigButtonOffsetX.get();
        } else if (screen instanceof PauseScreen pause && pause.showsPauseMenu()) {
            rows = INGAME_MENU;
            row = AllConfigs.client().ingameMenuConfigButtonRow.get();
            offsetX = AllConfigs.client().ingameMenuConfigButtonOffsetX.get();
        } else {
            return;
        }
        if (row <= 0 || row > rows.size()) {
            return;
        }
        boolean onLeft = offsetX < 0;
        String target = I18n.get(rows.get(row - 1)[onLeft ? 0 : 1]);
        List<AbstractWidget> buttons = Screens.getWidgets(screen);
        for (AbstractWidget widget : buttons) {
            if (widget.getMessage().getString().equals(target)) {
                buttons.add(new GogglesButton(widget.getX() + offsetX + (onLeft ? -20 : widget.getWidth()), widget.getY()));
                return;
            }
        }
    }
}
