package com.gravisludio.flyconfigmenu.ui;

import com.zurrtum.create.client.catnip.gui.widget.BoxWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Create's flat bordered button with a centered text that takes the color of the border's gradient. */
public class LabelBox extends BoxWidget {
    private Component label;

    public LabelBox(int x, int y, int width, int height, Component label) {
        super(x, y, width, height);
        this.label = label;
    }

    public void setLabel(Component label) {
        this.label = label;
    }

    @Override
    public void doRender(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        super.doRender(graphics, mouseX, mouseY, partialTicks);
        Font font = Minecraft.getInstance().font;
        graphics.centeredText(font, label, getX() + width / 2, getY() + (height - font.lineHeight) / 2 + 1, ConfigUi.rgb(gradientColor.getFirst()));
    }
}
