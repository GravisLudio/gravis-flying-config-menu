package com.gravisludio.flyconfigmenu.screen;

import com.gravisludio.flyconfigmenu.ConfigMenu;
import com.gravisludio.flyconfigmenu.config.ConfigComment;
import com.gravisludio.flyconfigmenu.config.GroupNode;
import com.gravisludio.flyconfigmenu.config.LeafNode;
import com.gravisludio.flyconfigmenu.config.ValueCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Arrays;
import java.util.List;

/** The scrolling list of groups and options. Rows are rebuilt by the screen; they hold no state of their own beyond their widgets. */
public final class ConfigList extends ContainerObjectSelectionList<ConfigList.Row> {
    private static final int ROW_HEIGHT = 24;
    private static final int INDENT = 12;
    private static final int RESET_WIDTH = 20;
    private static final int GAP = 4;
    private static final int TEXT_OK = 0xFFE0E0E0;
    private static final int TEXT_BAD = 0xFFFF5555;
    private static final int TEXT_DISABLED = 0xFF909090;

    public ConfigList(Minecraft minecraft, int width, int height, int y) {
        super(minecraft, width, height, y, ROW_HEIGHT);
        this.centerListVertically = false;
    }

    @Override
    public int getRowWidth() {
        return Math.min(this.width - 24, 460);
    }

    /** Replaces every row, keeping the scroll position as far as the new content allows. */
    public void setRows(List<Row> rows) {
        double scroll = scrollAmount();
        clearEntries();
        for (Row row : rows) {
            addEntry(row);
        }
        setScrollAmount(Math.min(scroll, maxScrollAmount()));
    }

    public abstract static class Row extends ContainerObjectSelectionList.Entry<Row> {
    }

    /** A group header; clicking it opens or closes the group. */
    public static final class GroupRow extends Row {
        private final Button button;
        private final int depth;

        public GroupRow(GroupNode group, int depth, boolean expanded, Runnable toggle) {
            this.depth = depth;
            MutableComponent label = Component.literal((expanded ? "▼ " : "▶ ") + group.name())
                .append(Component.literal("  (" + group.leafCount() + ")").withStyle(ChatFormatting.GRAY));
            this.button = Button.builder(label, b -> toggle.run()).width(100).build();
            Component tip = commentTooltip(group.name(), group.comment());
            if (tip != null) {
                this.button.setTooltip(Tooltip.create(tip));
            }
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            int indent = depth * INDENT;
            button.setPosition(getContentX() + indent, getContentY());
            button.setWidth(getContentWidth() - indent);
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

    /** One option: name, the widget that fits its type, and a reset button. */
    public static final class ValueRow extends Row {
        private final LeafNode leaf;
        private final int depth;
        private final boolean editable;
        private final Runnable onChanged;
        private final StringWidget label;
        private final AbstractWidget widget;
        private final Button reset;
        /** Set while the row itself writes into its widget, so the widget's own listener does not write it back. */
        private boolean updating;

        public ValueRow(Font font, LeafNode leaf, int depth, boolean editable, Runnable onChanged) {
            this.leaf = leaf;
            this.depth = depth;
            this.editable = editable;
            this.onChanged = onChanged;

            ConfigComment comment = leaf.comment();
            MutableComponent name = Component.literal(leaf.name());
            if (comment.restart() != null || comment.relog()) {
                name = Component.literal("! ").withStyle(ChatFormatting.GOLD).append(name);
            }
            this.label = new StringWidget(name, font);
            this.widget = createWidget(font);
            this.reset = Button.builder(Component.literal("R"), b -> resetToDefault()).width(RESET_WIDTH).build();

            Tooltip tooltip = Tooltip.create(valueTooltip(leaf, editable));
            this.label.setTooltip(tooltip);
            this.widget.setTooltip(tooltip);
            this.reset.setTooltip(Tooltip.create(Component.translatable("create_fly_config_menu.reset", String.valueOf(leaf.defaultValue()))));
            refreshReset();
        }

        private AbstractWidget createWidget(Font font) {
            AbstractWidget created = switch (leaf.kind()) {
                case BOOLEAN -> CycleButton.onOffBuilder((Boolean) leaf.get())
                    .displayOnlyValue()
                    .create(0, 0, 100, 20, Component.empty(), (button, value) -> apply(value));
                case ENUM -> CycleButton.<Object>builder(v -> Component.literal(((Enum<?>) v).name()), leaf.get())
                    .withValues(Arrays.asList(leaf.enumConstants()))
                    .displayOnlyValue()
                    .create(0, 0, 100, 20, Component.empty(), (button, value) -> apply(value));
                case INT, FLOAT, STRING -> createTextBox(font);
                case UNSUPPORTED -> new StringWidget(Component.literal(String.valueOf(leaf.get())).withStyle(ChatFormatting.GRAY), font);
            };
            created.active = editable && leaf.kind() != LeafNode.Kind.UNSUPPORTED;
            return created;
        }

        private EditBox createTextBox(Font font) {
            EditBox box = new EditBox(font, 100, 20, Component.literal(leaf.name())) {
                @Override
                public void setFocused(boolean focused) {
                    boolean wasFocused = isFocused();
                    super.setFocused(focused);
                    // Leaving the box with half-typed text: put the real value back instead of leaving a lie on screen.
                    if (wasFocused && !isFocused() && parse(getValue()) == null) {
                        syncFromValue();
                    }
                }
            };
            box.setMaxLength(256);
            box.setEditable(editable);
            box.setValue(format(leaf.get()));
            box.setTextColor(editable ? TEXT_OK : TEXT_DISABLED);
            box.setResponder(text -> {
                if (updating) {
                    return;
                }
                Object parsed = parse(text);
                box.setTextColor(parsed != null ? TEXT_OK : TEXT_BAD);
                if (parsed != null && !parsed.equals(leaf.get())) {
                    apply(parsed);
                }
            });
            return box;
        }

        private String format(Object value) {
            return switch (leaf.kind()) {
                case INT -> ValueCodec.formatInt((Integer) value, leaf.comment().intDisplay());
                case FLOAT -> ValueCodec.formatFloat((Float) value);
                default -> String.valueOf(value);
            };
        }

        /** @return the typed value for the text, or {@code null} when it is not a valid value for this option */
        private Object parse(String text) {
            switch (leaf.kind()) {
                case INT: {
                    Integer value = ValueCodec.parseInt(text, leaf.comment().intDisplay());
                    return value != null && leaf.inRange(value) ? value : null;
                }
                case FLOAT: {
                    Float value = ValueCodec.parseFloat(text);
                    return value != null && leaf.inRange(value) ? value : null;
                }
                case STRING:
                    return text;
                default:
                    return null;
            }
        }

        private void apply(Object value) {
            try {
                leaf.set(value);
            } catch (RuntimeException e) {
                ConfigMenu.LOGGER.error("Could not save option {}", leaf.name(), e);
                syncFromValue();
                return;
            }
            refreshReset();
            onChanged.run();
        }

        private void resetToDefault() {
            apply(leaf.defaultValue());
            syncFromValue();
        }

        /** Shows the option's current value in the widget without writing anything. */
        private void syncFromValue() {
            updating = true;
            try {
                if (widget instanceof EditBox box) {
                    box.setValue(format(leaf.get()));
                    box.setTextColor(editable ? TEXT_OK : TEXT_DISABLED);
                } else if (widget instanceof CycleButton<?> cycle) {
                    setCycleValue(cycle, leaf.get());
                }
            } finally {
                updating = false;
            }
        }

        @SuppressWarnings("unchecked")
        private static void setCycleValue(CycleButton<?> cycle, Object value) {
            ((CycleButton<Object>) cycle).setValue(value);
        }

        private void refreshReset() {
            reset.active = editable && !leaf.isDefault() && leaf.kind() != LeafNode.Kind.UNSUPPORTED;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            int indent = depth * INDENT;
            int contentWidth = getContentWidth();
            int widgetWidth = Math.max(70, contentWidth * 40 / 100);
            int labelWidth = Math.max(40, contentWidth - indent - GAP - widgetWidth - GAP - RESET_WIDTH);
            int x = getContentX() + indent;
            int y = getContentY();

            label.setMaxWidth(labelWidth, StringWidget.TextOverflow.SCROLLING);
            label.setPosition(x, y + (20 - label.getHeight()) / 2);
            widget.setPosition(x + labelWidth + GAP, y);
            widget.setWidth(widgetWidth);
            reset.setPosition(getContentRight() - RESET_WIDTH, y);

            label.extractRenderState(graphics, mouseX, mouseY, a);
            widget.extractRenderState(graphics, mouseX, mouseY, a);
            reset.extractRenderState(graphics, mouseX, mouseY, a);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(widget, reset);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(widget, reset);
        }
    }

    /** Tooltip for a group or an option: the author's comment, or {@code null} when there is nothing to say. */
    static Component commentTooltip(String title, ConfigComment comment) {
        if (comment.lines().isEmpty()) {
            return null;
        }
        MutableComponent tooltip = Component.literal(title).withStyle(ChatFormatting.YELLOW);
        for (String line : comment.lines()) {
            tooltip.append("\n").append(Component.literal(line).withStyle(ChatFormatting.GRAY));
        }
        return tooltip;
    }

    private static Component valueTooltip(LeafNode leaf, boolean editable) {
        ConfigComment comment = leaf.comment();
        MutableComponent tooltip = Component.literal(leaf.name()).withStyle(ChatFormatting.YELLOW);
        for (String line : comment.lines()) {
            tooltip.append("\n").append(Component.literal(line).withStyle(ChatFormatting.GRAY));
        }
        tooltip.append("\n").append(Component.translatable("create_fly_config_menu.default", String.valueOf(leaf.defaultValue())).withStyle(ChatFormatting.DARK_AQUA));
        if ((leaf.kind() == LeafNode.Kind.INT || leaf.kind() == LeafNode.Kind.FLOAT) && Double.isFinite(leaf.min()) && Double.isFinite(leaf.max())
            && Math.abs(leaf.min()) < 1.0e9 && Math.abs(leaf.max()) < 1.0e9) {
            tooltip.append("\n").append(Component.translatable("create_fly_config_menu.range", trim(leaf.min()), trim(leaf.max())).withStyle(ChatFormatting.DARK_AQUA));
        }
        if (comment.restart() != null) {
            tooltip.append("\n").append(Component.translatable("create_fly_config_menu.restart." + comment.restart()).withStyle(ChatFormatting.GOLD));
        }
        if (comment.relog()) {
            tooltip.append("\n").append(Component.translatable("create_fly_config_menu.relog").withStyle(ChatFormatting.GOLD));
        }
        if (!editable) {
            tooltip.append("\n").append(Component.translatable("create_fly_config_menu.readonly.option").withStyle(ChatFormatting.RED));
        }
        return tooltip;
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }
}
