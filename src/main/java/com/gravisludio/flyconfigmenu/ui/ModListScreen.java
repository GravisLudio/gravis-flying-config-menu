package com.gravisludio.flyconfigmenu.ui;

import com.gravisludio.flyconfigmenu.ConfigMenu;
import com.gravisludio.flyconfigmenu.config.ConfigRegistry;
import com.mojang.blaze3d.platform.InputConstants;
import com.zurrtum.create.catnip.theme.Color;
import com.zurrtum.create.client.catnip.gui.AbstractSimiScreen;
import com.zurrtum.create.client.catnip.gui.UIRenderHelper;
import com.zurrtum.create.client.foundation.gui.AllIcons;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/** Every mod whose config this menu found, one row each, like Create's "other mods" list. */
public class ModListScreen extends AbstractSimiScreen {
    private final Screen parent;
    private ModList list;
    private EditBox search;
    private String filter = "";

    public ModListScreen(Screen parent) {
        super(Component.translatable("create_fly_config_menu.screen.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int listWidth = Math.min(width - 80, 300);

        IconBox goBack = new IconBox(width / 2 - listWidth / 2 - 30, height / 2 + 65, 20, 20, AllIcons.I_CONFIG_BACK);
        goBack.withPadding(2, 2);
        goBack.withCallback(() -> minecraft.gui.setScreen(parent));
        goBack.getToolTip().add(Component.translatable("create_fly_config_menu.ui.go_back"));
        addRenderableWidget(goBack);

        search = new EditBox(font, width / 2 - listWidth / 2, height - 35, listWidth, 20, Component.translatable("create_fly_config_menu.search"));
        search.setValue(filter);
        search.setResponder(query -> {
            filter = query.toLowerCase(Locale.ROOT);
            if (list != null) {
                list.fill(filter);
            }
        });
        search.setHint(Component.translatable("create_fly_config_menu.search"));
        search.moveCursorToStart(false);
        addRenderableWidget(search);

        list = new ModList(width, height - 60, 15, 40, filter);
        list.updateSizeAndPosition(listWidth, height - 60, width / 2 - listWidth / 2, 15);
        addRenderableWidget(list);
    }

    @Override
    protected void renderWindow(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        if (ConfigRegistry.modIds().isEmpty()) {
            graphics.centeredText(font, Component.translatable("create_fly_config_menu.screen.empty"), width / 2, height / 2, ConfigUi.TEXT);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (super.keyPressed(keyEvent)) {
            return true;
        }
        if (search != null && keyEvent.hasControlDown() && keyEvent.key() == InputConstants.KEY_F) {
            setFocused(search);
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

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    private final class ModList extends ContainerObjectSelectionList<ModList.Row> {
        ModList(int width, int height, int top, int rowHeight, String filter) {
            super(Minecraft.getInstance(), width, height, top, rowHeight);
            fill(filter);
        }

        void fill(String filter) {
            clearEntries();
            for (String modId : ConfigRegistry.modIds()) {
                if (filter.isEmpty() || modId.contains(filter) || ConfigMenu.displayName(modId).toLowerCase(Locale.ROOT).contains(filter)) {
                    addEntry(new Row(modId));
                }
            }
        }

        @Override
        public int getRowWidth() {
            return width - 16;
        }

        @Override
        public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            super.extractWidgetRenderState(graphics, mouseX, mouseY, a);
            Color shade = new Color(0x60_000000, true);
            UIRenderHelper.angledGradient(graphics, 90, getX() + width / 2, getY(), width, 5, shade, Color.TRANSPARENT_BLACK);
            UIRenderHelper.angledGradient(graphics, -90, getX() + width / 2, getBottom(), width, 5, shade, Color.TRANSPARENT_BLACK);
        }

        private final class Row extends ContainerObjectSelectionList.Entry<Row> {
            private final String modId;
            private final IconBox button;
            private final List<Component> tooltip;

            Row(String modId) {
                this.modId = modId;
                this.button = new IconBox(0, 0, 35, 16, AllIcons.I_CONFIG_OPEN);
                button.withCallback(() -> minecraft.gui.setScreen(new RootScreen(ModListScreen.this, modId)));
                String types = String.join(", ", ConfigRegistry.forMod(modId).stream().map(ConfigRegistry.Entry::type).toList());
                this.tooltip = List.of(
                    Component.literal(ConfigMenu.displayName(modId)).withStyle(ChatFormatting.WHITE),
                    Component.literal(types).withStyle(ChatFormatting.GRAY),
                    Component.literal(modId).withStyle(ChatFormatting.DARK_GRAY)
                );
            }

            @Override
            public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
                Color dark = new Color(0xdd_000000, true);
                UIRenderHelper.streak(graphics, 0, getX() - 10, getY() + getHeight() / 2, getHeight() - 6, getWidth() / 8 * 7, dark);
                UIRenderHelper.streak(graphics, 180, getX() + (int) (getWidth() * 1.35f) + 10, getY() + getHeight() / 2, getHeight() - 6, getWidth() / 8 * 7, dark);

                Font font = Minecraft.getInstance().font;
                graphics.text(font, ConfigMenu.displayName(modId), getX() + 10, getY() + getHeight() / 2 - 4, ConfigUi.TEXT_ACCENT, false);
                if (mouseX > getX() && mouseX < getX() + getWidth() * 0.6 && mouseY > getY() + 5 && mouseY < getY() + getHeight() - 5) {
                    graphics.setComponentTooltipForNextFrame(font, tooltip, mouseX, mouseY);
                }

                button.setX(getX() + getWidth() - 108);
                button.setY(getY() + 10);
                button.setHeight(getHeight() - 20);
                button.extractRenderState(graphics, mouseX, mouseY, partialTick);
            }

            @Override
            public List<? extends GuiEventListener> children() {
                return List.of(button);
            }

            @Override
            public List<? extends NarratableEntry> narratables() {
                return List.of(button);
            }
        }
    }
}
