package com.gravisludio.flyconfigmenu.ui;

import com.gravisludio.flyconfigmenu.config.ConfigComment;
import com.gravisludio.flyconfigmenu.config.ConfigNode;
import com.gravisludio.flyconfigmenu.config.GroupNode;
import com.gravisludio.flyconfigmenu.config.LeafNode;
import com.gravisludio.flyconfigmenu.config.ValueCodec;
import com.zurrtum.create.catnip.animation.LerpedFloat;
import com.zurrtum.create.catnip.theme.Color;
import com.zurrtum.create.client.catnip.gui.UIRenderHelper;
import com.zurrtum.create.client.catnip.gui.TickableGuiEventListener;
import com.zurrtum.create.client.catnip.gui.element.BoxElement;
import com.zurrtum.create.client.catnip.gui.widget.AbstractSimiWidget;
import com.zurrtum.create.client.catnip.lang.FontHelper;
import com.zurrtum.create.client.foundation.gui.AllIcons;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The scrolling list of one page of a config: groups to open and options to edit. Layout, streaks and widgets follow
 * Create's original config menu (Catnip, MIT, Copyright (c) 2022 The Create Team); the data comes from this mod's own
 * {@link GroupNode}/{@link LeafNode} model. Edits are staged in the {@link EditorScreen}, not written here.
 */
public final class EntryList extends ContainerObjectSelectionList<EntryList.Row> {
    private static final float LABEL_WIDTH_MULT = 0.4f;
    private static final int RESET_WIDTH = 28;

    private final EditorScreen screen;
    private String filter;

    public EntryList(EditorScreen screen, int width, int height, int top, int elementHeight, String filter) {
        super(Minecraft.getInstance(), width, height, top, elementHeight);
        this.screen = screen;
        this.filter = filter;
        fill();
    }

    public void updateFilter(String filter) {
        if (!this.filter.equals(filter)) {
            this.filter = filter;
            fill();
        }
    }

    private void fill() {
        clearEntries();
        GroupNode group = screen.currentGroup();
        String path = screen.currentPath();
        for (ConfigNode node : group.children()) {
            if (!accepts(node)) {
                continue;
            }
            if (node instanceof GroupNode sub) {
                addEntry(new NestedRow(this, sub));
            } else if (node instanceof LeafNode leaf) {
                addEntry(createValueRow(path + "/" + leaf.name(), leaf));
            }
        }
    }

    private boolean accepts(ConfigNode node) {
        if (filter.isEmpty()) {
            return true;
        }
        if (node instanceof GroupNode group) {
            return group.name().toLowerCase(Locale.ROOT).contains(filter) || group.children().stream().anyMatch(this::accepts);
        }
        return node.name().toLowerCase(Locale.ROOT).contains(filter)
            || ConfigUi.humanize(node.name()).toLowerCase(Locale.ROOT).contains(filter)
            || node.comment().matches(filter);
    }

    private ValueRow createValueRow(String path, LeafNode leaf) {
        return switch (leaf.kind()) {
            case BOOLEAN -> new BooleanRow(this, path, leaf);
            case INT, FLOAT -> new NumberRow(this, path, leaf);
            case STRING -> new StringRow(this, path, leaf);
            case ENUM -> new EnumRow(this, path, leaf);
            case UNSUPPORTED -> new UnsupportedRow(this, path, leaf);
        };
    }

    /** Called by the screen when values changed behind the rows' backs (discard, save, reset all). */
    public void refreshValues() {
        for (Row row : children()) {
            row.refresh();
        }
    }

    public void tickRows() {
        for (Row row : children()) {
            row.tick();
        }
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractWidgetRenderState(graphics, mouseX, mouseY, a);
        Color shade = new Color(0x60_000000, true);
        UIRenderHelper.angledGradient(graphics, 90, getX() + width / 2, getY(), width, 5, shade, Color.TRANSPARENT_BLACK);
        UIRenderHelper.angledGradient(graphics, -90, getX() + width / 2, getBottom(), width, 5, shade, Color.TRANSPARENT_BLACK);
        UIRenderHelper.angledGradient(graphics, 0, getX(), getY() + height / 2, height, 5, shade, Color.TRANSPARENT_BLACK);
        UIRenderHelper.angledGradient(graphics, 180, getRight(), getY() + height / 2, height, 5, shade, Color.TRANSPARENT_BLACK);
    }

    @Override
    public int getRowWidth() {
        return width - 16;
    }

    // ---- rows ---------------------------------------------------------------------------------------------------

    /** What every row has: the label on the left with its tooltip, and the dark streaks behind it. */
    public abstract static class Row extends ContainerObjectSelectionList.Entry<Row> implements TickableGuiEventListener {
        protected final EntryList list;
        protected final String label;
        private final List<Component> tooltip;

        protected Row(EntryList list, String label, String title, ConfigComment comment, List<Component> extra) {
            this.list = list;
            this.label = label;
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal(label).withStyle(ChatFormatting.WHITE));
            for (String line : comment.lines()) {
                lines.addAll(FontHelper.cutTextComponent(Component.literal(line), FontHelper.Palette.ALL_GRAY));
            }
            lines.addAll(extra);
            lines.add(Component.literal(title).withStyle(ChatFormatting.DARK_GRAY));
            this.tooltip = Collections.unmodifiableList(lines);
        }

        protected abstract void collectChildren(Consumer<GuiEventListener> output);

        @Override
        public void tick() {
        }

        /** The value shown may have changed from outside the row. */
        public void refresh() {
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
            Color dark = new Color(0xdd_000000, true);
            UIRenderHelper.streak(graphics, 0, getX() - 10, getY() + getHeight() / 2, getHeight() - 6, getWidth() / 8 * 7, dark);
            UIRenderHelper.streak(graphics, 180, getX() + (int) (getWidth() * 1.35f) + 10, getY() + getHeight() / 2, getHeight() - 6, getWidth() / 8 * 7, dark);

            Font font = Minecraft.getInstance().font;
            String shown = label;
            int maxWidth = labelWidth(getWidth()) - 10;
            if (font.width(shown) > maxWidth) {
                shown = font.plainSubstrByWidth(shown, maxWidth - 5) + "...";
            }
            graphics.text(font, shown, getX() + 10, getY() + getHeight() / 2 - labelYOffset(), ConfigUi.TEXT_ACCENT, false);

            if (mouseX > getX() && mouseX < getX() + labelWidth(getWidth()) && mouseY > getY() + 5 && mouseY < getY() + getHeight() - 5) {
                graphics.setComponentTooltipForNextFrame(font, tooltip, mouseX, mouseY);
            }
        }

        @Override
        public final List<? extends GuiEventListener> children() {
            List<GuiEventListener> children = new ArrayList<>();
            collectChildren(children::add);
            return children;
        }

        @Override
        public final List<? extends NarratableEntry> narratables() {
            List<NarratableEntry> narratables = new ArrayList<>();
            collectChildren(child -> {
                if (child instanceof NarratableEntry narratable) {
                    narratables.add(narratable);
                }
            });
            return narratables;
        }

        protected int labelYOffset() {
            return 4;
        }

        protected int labelWidth(int totalWidth) {
            return (int) (totalWidth * LABEL_WIDTH_MULT) + 30;
        }
    }

    /** A group: a button that opens it as its own page. */
    public static final class NestedRow extends Row {
        private final IconBox button;

        NestedRow(EntryList list, GroupNode group) {
            super(list, ConfigUi.humanize(group.name()), group.name(), group.comment(), List.of());
            this.button = new IconBox(0, 0, 35, 16, AllIcons.I_CONFIG_OPEN);
            button.withCallback(() -> list.screen.open(group));
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
            super.extractContent(graphics, mouseX, mouseY, hovered, partialTick);
            button.setX(getX() + getWidth() - 108);
            button.setY(getY() + 10);
            button.setHeight(getHeight() - 20);
            button.extractRenderState(graphics, mouseX, mouseY, partialTick);
        }

        @Override
        protected void collectChildren(Consumer<GuiEventListener> output) {
            output.accept(button);
        }

        /** For tests: opens the group as if the player clicked the button. */
        public void click() {
            button.runCallback(0, 0);
        }
    }

    /** An option. Reads and writes go through the screen, which keeps the staged (not yet saved) values. */
    public abstract static class ValueRow extends Row {
        protected final LeafNode leaf;
        protected final String path;
        protected final IconBox resetButton;
        private final LerpedFloat differenceAnimation = LerpedFloat.linear().startWithValue(0);
        private final boolean requiresRestart;
        private final boolean requiresRelog;

        ValueRow(EntryList list, String path, LeafNode leaf) {
            super(list, ConfigUi.humanize(leaf.name()), leaf.name(), leaf.comment(), extraTooltip(leaf));
            this.leaf = leaf;
            this.path = path;
            this.requiresRestart = leaf.comment().restart() != null;
            this.requiresRelog = leaf.comment().relog();

            this.resetButton = new IconBox(0, 0, RESET_WIDTH - 12, 16, AllIcons.I_CONFIG_RESET);
            resetButton.withCallback(() -> {
                setValue(leaf.defaultValue());
                refresh();
            });
            updateResetButton();
        }

        private static List<Component> extraTooltip(LeafNode leaf) {
            List<Component> extra = new ArrayList<>();
            extra.add(Component.translatable("create_fly_config_menu.default", String.valueOf(leaf.defaultValue())).withStyle(ChatFormatting.DARK_AQUA));
            if ((leaf.kind() == LeafNode.Kind.INT || leaf.kind() == LeafNode.Kind.FLOAT) && hasUsefulBounds(leaf)) {
                extra.add(Component.translatable("create_fly_config_menu.range", trim(leaf.min()), trim(leaf.max())).withStyle(ChatFormatting.DARK_AQUA));
            }
            ConfigComment comment = leaf.comment();
            if (comment.relog()) {
                extra.addAll(FontHelper.cutTextComponent(Component.translatable("create_fly_config_menu.relog"), FontHelper.Palette.GRAY_AND_GOLD));
            }
            if (comment.restart() != null) {
                extra.addAll(FontHelper.cutTextComponent(Component.translatable("create_fly_config_menu.restart." + comment.restart()), FontHelper.Palette.GRAY_AND_RED));
            }
            return extra;
        }

        static boolean hasUsefulBounds(LeafNode leaf) {
            return Math.abs(leaf.min()) < 1.0e9 || Math.abs(leaf.max()) < 1.0e9;
        }

        static String trim(double value) {
            if (Math.abs(value) >= 1.0e9) {
                return String.format(Locale.ROOT, "%.2E", value);
            }
            return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
        }

        protected final Object value() {
            return list.screen.valueOf(path, leaf);
        }

        protected final void setValue(Object value) {
            list.screen.stage(path, leaf, value);
            onValueChange(value);
        }

        protected final boolean isMutable() {
            return list.screen.isMutable();
        }

        private boolean isCurrentDefault() {
            return leaf.defaultValue().equals(value());
        }

        private void updateResetButton() {
            resetButton.active = isMutable() && !isCurrentDefault();
            resetButton.animateGradientFromState();
        }

        protected void onValueChange(Object newValue) {
            updateResetButton();
        }

        @Override
        public void refresh() {
            onValueChange(value());
        }

        @Override
        public void tick() {
            differenceAnimation.tickChaser();
        }

        @Override
        protected void collectChildren(Consumer<GuiEventListener> output) {
            output.accept(resetButton);
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
            boolean dirty = list.screen.isDirty(path);
            if (dirty && differenceAnimation.getChaseTarget() != 1) {
                differenceAnimation.chase(1, .5f, LerpedFloat.Chaser.EXP);
            } else if (!dirty && differenceAnimation.getChaseTarget() != 0) {
                differenceAnimation.chase(0, .6f, LerpedFloat.Chaser.EXP);
            }

            float difference = differenceAnimation.getValue(partialTick);
            if (difference > 0.1) {
                int offset = (int) (30 * (1 - difference));
                if (requiresRestart) {
                    UIRenderHelper.streak(graphics, 180, getX() + getWidth() + 10 + offset, getY() + getHeight() / 2, getHeight() - 6, 110, new Color(0x50_601010, true));
                } else if (requiresRelog) {
                    UIRenderHelper.streak(graphics, 180, getX() + getWidth() + 10 + offset, getY() + getHeight() / 2, getHeight() - 6, 110, new Color(0x40_eefb17, true));
                }
                UIRenderHelper.breadcrumbArrow(graphics, getX() - 10 - offset, getY() + 6, -20, 24, -18, new Color(0x70_ffffff, true), Color.TRANSPARENT_BLACK);
            }

            super.extractContent(graphics, mouseX, mouseY, hovered, partialTick);

            resetButton.setX(getX() + getWidth() - RESET_WIDTH + 6);
            resetButton.setY(getY() + 10);
            resetButton.extractRenderState(graphics, mouseX, mouseY, partialTick);
        }
    }

    public static final class BooleanRow extends ValueRow {
        private final IconBox button;

        BooleanRow(EntryList list, String path, LeafNode leaf) {
            super(list, path, leaf);
            button = new IconBox(0, 0, 35, 16, AllIcons.I_CONFIRM);
            button.withCallback(() -> setValue(!(Boolean) value()));
            button.active = isMutable();
            button.updateGradientFromState();
            showCurrentValue();
        }

        private void showCurrentValue() {
            boolean on = (Boolean) value();
            button.setIcon(on ? AllIcons.I_CONFIRM : AllIcons.I_DISABLE);
            button.setTint(on ? AbstractSimiWidget.COLOR_SUCCESS : AbstractSimiWidget.COLOR_FAIL);
        }

        @Override
        protected void onValueChange(Object newValue) {
            super.onValueChange(newValue);
            showCurrentValue();
        }

        @Override
        protected void collectChildren(Consumer<GuiEventListener> output) {
            super.collectChildren(output);
            output.accept(button);
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
            super.extractContent(graphics, mouseX, mouseY, hovered, partialTick);
            button.setX(getX() + getWidth() - 80 - RESET_WIDTH);
            button.setY(getY() + 10);
            button.setWidth(35);
            button.setHeight(getHeight() - 20);
            button.extractRenderState(graphics, mouseX, mouseY, partialTick);
        }

        /** For tests: presses the button. */
        public void click() {
            button.runCallback(0, 0);
        }
    }

    /** INT and FLOAT options: a small text field, red while the text is not a valid value in range, with the bounds drawn around it. */
    public static final class NumberRow extends ValueRow {
        private final EditBox textField;
        private final String minText;
        private final String maxText;
        private boolean updating;

        NumberRow(EntryList list, String path, LeafNode leaf) {
            super(list, path, leaf);
            Font font = Minecraft.getInstance().font;
            textField = new EditBox(font, 0, 0, 200, 20, Component.literal("Edit box for " + label));
            textField.setTextColor(ConfigUi.TEXT);
            textField.setEditable(isMutable());
            textField.setValue(format(value()));
            textField.moveCursorToStart(false);
            textField.setResponder(text -> {
                if (updating) {
                    return;
                }
                Object parsed = parse(text);
                textField.setTextColor(parsed != null ? ConfigUi.TEXT : ConfigUi.rgb(AbstractSimiWidget.COLOR_FAIL.getFirst()));
                if (parsed != null) {
                    setValue(parsed);
                }
            });

            boolean useful = hasUsefulBounds(leaf);
            boolean hasMin = useful && leaf.min() > -1.0e9;
            boolean hasMax = useful && leaf.max() < 1.0e9;
            minText = hasMin ? trim(leaf.min()) + " < " : "";
            maxText = hasMax ? " < " + trim(leaf.max()) : "";
        }

        private String format(Object value) {
            return leaf.kind() == LeafNode.Kind.INT
                ? ValueCodec.formatInt((Integer) value, leaf.comment().intDisplay())
                : ValueCodec.formatFloat((Float) value);
        }

        private Object parse(String text) {
            if (leaf.kind() == LeafNode.Kind.INT) {
                Integer parsed = ValueCodec.parseInt(text, leaf.comment().intDisplay());
                return parsed != null && leaf.inRange(parsed) ? parsed : null;
            }
            Float parsed = ValueCodec.parseFloat(text);
            return parsed != null && leaf.inRange(parsed) ? parsed : null;
        }

        @Override
        protected void onValueChange(Object newValue) {
            super.onValueChange(newValue);
            if (textField == null) {
                return;
            }
            Object current = parse(textField.getValue());
            if (current == null || !current.equals(newValue)) {
                updating = true;
                try {
                    textField.setValue(format(newValue));
                    textField.setTextColor(ConfigUi.TEXT);
                } finally {
                    updating = false;
                }
            }
        }

        @Override
        protected void collectChildren(Consumer<GuiEventListener> output) {
            super.collectChildren(output);
            output.accept(textField);
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
            super.extractContent(graphics, mouseX, mouseY, hovered, partialTick);
            Font font = Minecraft.getInstance().font;
            int minWidth = minText.isEmpty() ? 0 : font.width(minText);
            int maxWidth = maxText.isEmpty() ? 0 : font.width(maxText);
            int fieldWidth = Math.max(36, Math.min(getWidth() - labelWidth(getWidth()) - RESET_WIDTH - minWidth - maxWidth, 56));
            textField.setX(getX() + getWidth() - RESET_WIDTH - 12 - maxWidth - fieldWidth);
            textField.setY(getY() + 8);
            textField.setWidth(fieldWidth);
            textField.setHeight(20);
            textField.extractWidgetRenderState(graphics, mouseX, mouseY, partialTick);
            if (!minText.isEmpty()) {
                graphics.text(font, minText, textField.getX() - minWidth, textField.getY() + 6, ConfigUi.TEXT_DARKER, false);
            }
            if (!maxText.isEmpty()) {
                graphics.text(font, maxText, textField.getX() + textField.getWidth(), textField.getY() + 6, ConfigUi.TEXT_DARKER, false);
            }
        }

        /** For tests: the box the player types in. */
        public EditBox textField() {
            return textField;
        }
    }

    public static final class StringRow extends ValueRow {
        private final EditBox textField;
        private boolean updating;

        StringRow(EntryList list, String path, LeafNode leaf) {
            super(list, path, leaf);
            textField = new EditBox(Minecraft.getInstance().font, 0, 0, 200, 20, Component.literal("Edit box for " + label));
            textField.setMaxLength(256);
            textField.setValue((String) value());
            textField.moveCursorToStart(false);
            textField.setEditable(isMutable());
            textField.setResponder(text -> {
                if (!updating) {
                    setValue(text);
                }
            });
        }

        @Override
        protected void onValueChange(Object newValue) {
            super.onValueChange(newValue);
            if (textField != null && !textField.getValue().equals(newValue)) {
                updating = true;
                try {
                    textField.setValue((String) newValue);
                } finally {
                    updating = false;
                }
            }
        }

        @Override
        protected void collectChildren(Consumer<GuiEventListener> output) {
            super.collectChildren(output);
            output.accept(textField);
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
            super.extractContent(graphics, mouseX, mouseY, hovered, partialTick);
            textField.setX(getX() + getWidth() - 82 - RESET_WIDTH);
            textField.setY(getY() + 8);
            textField.setWidth(Math.max(40, Math.min(getWidth() - labelWidth(getWidth()) - RESET_WIDTH, 80)));
            textField.setHeight(20);
            textField.extractWidgetRenderState(graphics, mouseX, mouseY, partialTick);
        }

        public EditBox textField() {
            return textField;
        }
    }

    /** An enum: a box on each side to step through the values, the current one between them. */
    public static final class EnumRow extends ValueRow {
        private static final int CYCLE_WIDTH = 34;

        private final IconBox cycleLeft;
        private final IconBox cycleRight;
        private Component valueText;

        EnumRow(EntryList list, String path, LeafNode leaf) {
            super(list, path, leaf);
            valueText = Component.literal(format(value()));

            cycleLeft = new IconBox(0, 0, CYCLE_WIDTH + 8, 16, AllIcons.I_CONFIG_PREV);
            cycleLeft.withCustomBackground(BoxElement.COLOR_BACKGROUND_FLAT);
            cycleLeft.setIconOffsetX(-(CYCLE_WIDTH + 8 - 16) / 2 + 2);
            cycleLeft.withCallback(() -> cycleValue(-1));
            cycleLeft.active = isMutable();
            cycleLeft.updateGradientFromState();

            cycleRight = new IconBox(0, 0, CYCLE_WIDTH + 8, 16, AllIcons.I_CONFIG_NEXT);
            cycleRight.withCustomBackground(BoxElement.COLOR_BACKGROUND_FLAT);
            cycleRight.setIconOffsetX((CYCLE_WIDTH + 8 - 16) / 2 - 2);
            cycleRight.withCallback(() -> cycleValue(1));
            cycleRight.active = isMutable();
            cycleRight.updateGradientFromState();
        }

        private void cycleValue(int direction) {
            Object[] options = leaf.enumConstants();
            int index = ((Enum<?>) value()).ordinal();
            setValue(options[Math.floorMod(index + direction, options.length)]);
        }

        private static String format(Object value) {
            return ConfigUi.humanize(((Enum<?>) value).name().toLowerCase(Locale.ROOT));
        }

        @Override
        protected void onValueChange(Object newValue) {
            super.onValueChange(newValue);
            valueText = Component.literal(format(newValue));
        }

        @Override
        protected void collectChildren(Consumer<GuiEventListener> output) {
            super.collectChildren(output);
            output.accept(cycleLeft);
            output.accept(cycleRight);
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
            super.extractContent(graphics, mouseX, mouseY, hovered, partialTick);
            Font font = Minecraft.getInstance().font;

            cycleLeft.setX(getX() + labelWidth(getWidth()) + 4);
            cycleLeft.setY(getY() + 10);
            cycleLeft.extractRenderState(graphics, mouseX, mouseY, partialTick);

            cycleRight.setX(getX() + getWidth() - CYCLE_WIDTH * 2 - RESET_WIDTH + 10);
            cycleRight.setY(getY() + 10);
            cycleRight.extractRenderState(graphics, mouseX, mouseY, partialTick);

            int left = cycleLeft.getX() + CYCLE_WIDTH + 8;
            int right = cycleRight.getX();
            graphics.centeredText(font, valueText, (left + right) / 2, getY() + 14, ConfigUi.TEXT);
        }

        /** For tests: steps through the values. */
        public void click(int direction) {
            cycleValue(direction);
        }
    }

    /** An option kind this menu cannot edit: shown with its current value, read-only. */
    public static final class UnsupportedRow extends ValueRow {
        UnsupportedRow(EntryList list, String path, LeafNode leaf) {
            super(list, path, leaf);
            resetButton.active = false;
            resetButton.updateGradientFromState();
        }

        @Override
        protected void onValueChange(Object newValue) {
            resetButton.active = false;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
            super.extractContent(graphics, mouseX, mouseY, hovered, partialTick);
            Font font = Minecraft.getInstance().font;
            graphics.text(font, String.valueOf(value()), getX() + labelWidth(getWidth()) + 4, getY() + getHeight() / 2 - 4, ConfigUi.TEXT_DARKER, false);
        }
    }
}
