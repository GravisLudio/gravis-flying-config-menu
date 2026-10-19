package com.gravisludio.flyconfigmenu.screen;

import com.gravisludio.flyconfigmenu.config.ConfigRegistry;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Skeleton screen: lists the configs the mixin found, optionally for a single mod. The editable tree comes next.
 */
public class ConfigOverviewScreen extends Screen {
    private final Screen parent;
    private final String modIdFilter;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);

    /** @param modIdFilter show only this mod's configs, or {@code null} for every discovered config */
    public ConfigOverviewScreen(Screen parent, String modIdFilter) {
        super(Component.translatable("create_fly_config_menu.screen.title"));
        this.parent = parent;
        this.modIdFilter = modIdFilter;
    }

    @Override
    protected void init() {
        layout.addTitleHeader(title, font);

        LinearLayout contents = layout.addToContents(LinearLayout.vertical().spacing(4));
        List<ConfigRegistry.Entry> entries = modIdFilter == null ? ConfigRegistry.entries() : ConfigRegistry.forMod(modIdFilter);
        if (entries.isEmpty()) {
            contents.addChild(new StringWidget(Component.translatable("create_fly_config_menu.screen.empty"), font));
        }
        for (ConfigRegistry.Entry entry : entries) {
            contents.addChild(new StringWidget(Component.literal(entry.modId() + " / " + entry.type()), font));
        }

        layout.addToFooter(Button.builder(CommonComponents.GUI_DONE, button -> onClose()).width(200).build());
        layout.visitWidgets(this::addRenderableWidget);
        repositionElements();
    }

    @Override
    protected void repositionElements() {
        layout.arrangeElements();
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
