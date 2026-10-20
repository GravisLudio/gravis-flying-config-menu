package com.gravisludio.flyconfigmenu.ui;

import com.zurrtum.create.client.catnip.gui.element.BoxElement;
import com.zurrtum.create.client.catnip.gui.element.RenderElement;
import com.zurrtum.create.client.catnip.gui.widget.AbstractSimiWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.function.Consumer;

/** The question strip Create's config menu shows over a dimmed screen: a text and two or three buttons. */
public class ConfirmationDialog extends ConfigScreenBase {
    public enum Response {
        CONFIRM, DONT_SAVE, CANCEL
    }

    private final Screen source;
    private final Component message;
    private final boolean threeWay;
    private final Consumer<Response> action;
    private List<FormattedCharSequence> lines = List.of();
    private int textY;
    private int textHeight;
    private RenderElement background;

    /**
     * @param threeWay Save / Don't save / Cancel instead of Confirm / Cancel
     */
    public ConfirmationDialog(Screen source, Component message, boolean threeWay, Consumer<Response> action) {
        super(Component.empty());
        this.source = source;
        this.message = message;
        this.threeWay = threeWay;
        this.action = action;
    }

    @Override
    protected void init() {
        super.init();
        lines = font.split(message, 300);
        textHeight = lines.size() * (font.lineHeight + 1) + 4;
        textY = height / 2 - textHeight / 2 - 16;

        int buttons = threeWay ? 3 : 2;
        int x = width / 2 - (buttons * 70 + (buttons - 1) * 12) / 2;
        int y = textY + textHeight + 6;

        LabelBox confirm = new LabelBox(x, y, 70, 16, Component.translatable(threeWay ? "create_fly_config_menu.ui.save_label" : "create_fly_config_menu.ui.confirm_label"));
        confirm.withCallback(() -> accept(Response.CONFIRM));
        addRenderableWidget(confirm);
        x += 82;

        if (threeWay) {
            LabelBox dontSave = new LabelBox(x, y, 70, 16, Component.translatable("create_fly_config_menu.ui.dont_save_label"));
            dontSave.withCallback(() -> accept(Response.DONT_SAVE));
            addRenderableWidget(dontSave);
            x += 82;
        }

        LabelBox cancel = new LabelBox(x, y, 70, 16, Component.translatable("create_fly_config_menu.ui.cancel_label"));
        cancel.withCallback(() -> accept(Response.CANCEL));
        addRenderableWidget(cancel);

        background = new BoxElement()
            .withBackground(BoxElement.COLOR_BACKGROUND_FLAT)
            .gradientBorder(AbstractSimiWidget.COLOR_DISABLED)
            .withBounds(width + 10, textHeight + 35)
            .at(-5, textY - 5);
    }

    @Override
    protected void renderWindow(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        background.render(graphics);
        int y = textY;
        for (FormattedCharSequence line : lines) {
            graphics.text(font, line, width / 2 - font.width(line) / 2, y, 0xFFEAEAEA, false);
            y += font.lineHeight + 1;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return source.isPauseScreen();
    }

    @Override
    public void onClose() {
        accept(Response.CANCEL);
    }

    private void accept(Response response) {
        minecraft.gui.setScreen(source);
        action.accept(response);
    }
}
