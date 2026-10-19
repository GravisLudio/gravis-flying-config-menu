package com.gravisludio.flyconfigmenu.ui;

import com.zurrtum.create.catnip.data.Couple;
import com.zurrtum.create.catnip.theme.Color;
import com.zurrtum.create.client.catnip.gui.widget.BoxWidget;
import com.zurrtum.create.client.foundation.gui.AllIcons;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.Nullable;

/** Create's flat bordered button showing one of Create's own icons, tinted with the border's gradient or a fixed color. */
public class IconBox extends BoxWidget {
    private AllIcons icon;
    private int iconOffsetX;
    @Nullable
    private Couple<Color> tint;

    public IconBox(int x, int y, int width, int height, AllIcons icon) {
        super(x, y, width, height);
        this.icon = icon;
    }

    public void setIcon(AllIcons icon) {
        this.icon = icon;
    }

    /** Colors the icon with these colors while the button is active (disabled buttons stay gray); {@code null} follows the border. */
    public void setTint(@Nullable Couple<Color> tint) {
        this.tint = tint;
    }

    /** Moves the icon sideways from the centre, for the wide open/confirm buttons. */
    public void setIconOffsetX(int offset) {
        this.iconOffsetX = offset;
    }

    @Override
    public void doRender(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        super.doRender(graphics, mouseX, mouseY, partialTicks);
        Color color = tint != null && active ? tint.getFirst() : gradientColor.getFirst();
        icon.render(graphics, getX() + (width - 16) / 2 + iconOffsetX, getY() + (height - 16) / 2, ConfigUi.rgb(color));
    }
}
