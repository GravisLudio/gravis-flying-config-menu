package com.gravisludio.flyconfigmenu.ui;

import com.gravisludio.flyconfigmenu.ConfigMenu;
import com.gravisludio.flyconfigmenu.config.ConfigRegistry;
import com.gravisludio.flyconfigmenu.config.WorldState;
import com.mojang.blaze3d.platform.InputConstants;
import com.zurrtum.create.catnip.theme.Color;
import com.zurrtum.create.client.catnip.gui.widget.AbstractSimiWidget;
import com.zurrtum.create.client.catnip.gui.widget.BoxWidget;
import com.zurrtum.create.client.catnip.lang.FontHelper;
import com.zurrtum.create.client.foundation.gui.AllIcons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The first screen of a mod's config, like Create's own: a gradient title and one button for each config file it has
 * (client, common, server), plus a way to the other mods.
 */
public class RootScreen extends ConfigScreenBase {
    private static final List<String> TYPES = List.of("client", "common", "server");
    private static final int[] Y_OFFSETS = {-30, 0, 30};

    private final Screen parent;
    private final String modId;

    public RootScreen(Screen parent, String modId) {
        super(Component.literal(ConfigMenu.displayName(modId)));
        this.parent = parent;
        this.modId = modId;
    }

    @Override
    protected void init() {
        super.init();

        for (int i = 0; i < TYPES.size(); i++) {
            String type = TYPES.get(i);
            ConfigRegistry.Entry entry = ConfigRegistry.forMod(modId).stream().filter(e -> e.type().equals(type)).findFirst().orElse(null);

            LabelBox button = new LabelBox(width / 2 - 100, height / 2 - 15 + Y_OFFSETS[i], 200, 16, Component.translatable("create_fly_config_menu.ui." + type));
            addRenderableWidget(button);
            button.getToolTip().add(Component.translatable("create_fly_config_menu.ui." + type));
            button.getToolTip().addAll(FontHelper.cutTextComponent(Component.translatable("create_fly_config_menu.ui." + type + ".tooltip"), FontHelper.Palette.ALL_GRAY));

            if (entry != null) {
                button.withCallback(() -> minecraft.gui.setScreen(new EditorScreen(this, entry)));
                if (type.equals("server") && WorldState.current() == WorldState.REMOTE) {
                    button.getToolTip().addAll(FontHelper.cutTextComponent(Component.translatable("create_fly_config_menu.ui.server_locked.tooltip"), FontHelper.Palette.GRAY_AND_GOLD));
                }
            } else {
                button.active = false;
                button.updateGradientFromState();
                button.getToolTip().addAll(FontHelper.cutTextComponent(Component.translatable("create_fly_config_menu.ui.no_config"), FontHelper.Palette.GRAY_AND_GOLD));
            }
        }

        TitleBox title = new TitleBox(-5, height / 2 - 110, width + 10, 39, Minecraft.getInstance().font, ConfigMenu.displayName(modId));
        addRenderableWidget(title);

        IconBox goBack = new IconBox(width / 2 - 134, height / 2, 20, 20, AllIcons.I_CONFIG_BACK);
        goBack.withPadding(2, 2);
        goBack.withCallback(() -> minecraft.gui.setScreen(parent));
        goBack.getToolTip().add(Component.translatable("create_fly_config_menu.ui.go_back"));
        addRenderableWidget(goBack);

        LabelBox others = new LabelBox(width / 2 - 100, height / 2 - 15 + 90, 200, 16, Component.translatable("create_fly_config_menu.ui.other_mods"));
        others.withCallback(() -> minecraft.gui.setScreen(new ModListScreen(this)));
        addRenderableWidget(others);
    }

    @Override
    protected void renderWindow(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
    }

    /** "Access Configs for Mod:" on top of the banner, above the mod's name (drawn after the banner so it is not covered). */
    @Override
    protected void renderWindowForeground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        graphics.centeredText(font, Component.translatable("create_fly_config_menu.ui.title_prefix"), width / 2, height / 2 - 105, ConfigUi.TEXT_ACCENT);
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (super.keyPressed(keyEvent)) {
            return true;
        }
        if (keyEvent.key() == InputConstants.KEY_BACKSPACE) {
            minecraft.gui.setScreen(parent);
            return true;
        }
        return false;
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    /** The wide banner with the mod's name in Create's pastel gradient, drawn at double size. */
    private static final class TitleBox extends BoxWidget {
        private final Font font;
        private final String text;

        TitleBox(int x, int y, int width, int height, Font font, String text) {
            super(x, y, width, height);
            this.font = font;
            this.text = text;
            withBorderColors(AbstractSimiWidget.COLOR_IDLE);
        }

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            return false;
        }

        @Override
        public void doRender(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
            super.doRender(graphics, mouseX, mouseY, partialTicks);
            int total = font.width(text) * 2;
            float cursor = getX() + (width - total) / 2f;
            // Lower half of the banner: the top line holds "Access Configs for Mod:".
            float textY = getY() + height - 4 - font.lineHeight * 2;
            int count = text.length();
            graphics.pose().pushMatrix();
            graphics.pose().translate(cursor, textY);
            graphics.pose().scale(2f, 2f);
            float x = 0;
            for (int i = 0; i < count; i++) {
                float t = count == 1 ? 0 : i / (float) (count - 1);
                Color color = t < 0.5f ? Color.mixColors(ConfigUi.TITLE_A, ConfigUi.TITLE_B, t * 2) : Color.mixColors(ConfigUi.TITLE_B, ConfigUi.TITLE_C, (t - 0.5f) * 2);
                String letter = String.valueOf(text.charAt(i));
                graphics.text(font, letter, Math.round(x), 0, ConfigUi.rgb(color), false);
                x += font.width(letter);
            }
            graphics.pose().popMatrix();
        }
    }
}
