package com.gravisludio.flyconfigmenu.screen;

import com.gravisludio.flyconfigmenu.ConfigMenu;
import com.gravisludio.flyconfigmenu.config.ConfigRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Every mod whose config the mixin found, one button each; opens {@link ModConfigScreen}. */
public class ConfigOverviewScreen extends Screen {
    private final Screen parent;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    private ModList list;

    public ConfigOverviewScreen(Screen parent) {
        super(Component.translatable("create_fly_config_menu.screen.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        layout.addTitleHeader(title, font);
        List<String> modIds = ConfigRegistry.modIds();
        if (modIds.isEmpty()) {
            layout.addToContents(new StringWidget(Component.translatable("create_fly_config_menu.screen.empty"), font));
        } else {
            list = layout.addToContents(new ModList(minecraft, width, layout.getContentHeight(), layout.getHeaderHeight()));
            list.fill(modIds);
        }
        layout.addToFooter(Button.builder(CommonComponents.GUI_DONE, button -> onClose()).width(200).build());
        layout.visitWidgets(this::addRenderableWidget);
        repositionElements();
    }

    @Override
    protected void repositionElements() {
        layout.arrangeElements();
        if (list != null) {
            list.updateSize(width, layout);
        }
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    private final class ModList extends ContainerObjectSelectionList<ModList.Row> {
        ModList(Minecraft minecraft, int width, int height, int y) {
            super(minecraft, width, height, y, 24);
            this.centerListVertically = false;
        }

        @Override
        public int getRowWidth() {
            return 310;
        }

        void fill(List<String> modIds) {
            for (String modId : modIds) {
                addEntry(new Row(modId));
            }
        }

        private final class Row extends ContainerObjectSelectionList.Entry<Row> {
            private final Button button;

            Row(String modId) {
                String types = String.join(", ", ConfigRegistry.forMod(modId).stream().map(ConfigRegistry.Entry::type).toList());
                this.button = Button.builder(
                    Component.literal(ConfigMenu.displayName(modId)).append(Component.literal("  (" + types + ")").withStyle(net.minecraft.ChatFormatting.GRAY)),
                    b -> minecraft.gui.setScreen(new ModConfigScreen(ConfigOverviewScreen.this, modId))
                ).width(310).build();
            }

            @Override
            public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
                button.setPosition(getContentX(), getContentY());
                button.setWidth(getContentWidth());
                button.extractRenderState(graphics, mouseX, mouseY, a);
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
