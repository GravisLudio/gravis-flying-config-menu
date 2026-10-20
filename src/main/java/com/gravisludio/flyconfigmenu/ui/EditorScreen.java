package com.gravisludio.flyconfigmenu.ui;

import com.gravisludio.flyconfigmenu.ConfigMenu;
import com.gravisludio.flyconfigmenu.config.ConfigComment;
import com.gravisludio.flyconfigmenu.config.ConfigNode;
import com.gravisludio.flyconfigmenu.config.ConfigRegistry;
import com.gravisludio.flyconfigmenu.config.ConfigTrees;
import com.gravisludio.flyconfigmenu.config.GroupNode;
import com.gravisludio.flyconfigmenu.config.LeafNode;
import com.gravisludio.flyconfigmenu.config.WorldState;
import com.mojang.blaze3d.platform.InputConstants;
import com.zurrtum.create.client.catnip.gui.widget.AbstractSimiWidget;
import com.zurrtum.create.client.catnip.lang.FontHelper;
import com.zurrtum.create.client.foundation.gui.AllIcons;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * One config file of one mod, laid out like Create's original config editor: the options of the current page in a list,
 * four icon buttons (save, discard, reset all, back), the search box at the bottom and the path of the page on top.
 *
 * <p>Edits are <b>staged</b>: they live in {@link #changes()} until the player presses save. Nothing is written to the JSON
 * file before that, and leaving with staged edits asks what to do with them. The {@code server} file is read-only while the
 * player is connected to someone else's server, because Create Fly then holds the remote server's values in memory and
 * saving would put them into the local file.
 */
public class EditorScreen extends ConfigScreenBase {
    /** An edit waiting for save. */
    public record Staged(LeafNode leaf, Object value) {
    }

    private final Screen parent;
    private final ConfigRegistry.Entry entry;
    private final boolean mutable;
    private final WorldState world;
    private final ConfigTrees.Tree tree;
    private final List<GroupNode> trail = new ArrayList<>();
    private final Map<String, Staged> changes = new LinkedHashMap<>();
    private EntryList list;
    private EditBox search;
    private String searchText = "";
    private IconBox saveButton;
    private IconBox discardButton;

    public EditorScreen(Screen parent, ConfigRegistry.Entry entry) {
        super(Component.literal(ConfigMenu.displayName(entry.modId()) + " " + entry.type()));
        this.parent = parent;
        this.entry = entry;
        this.world = WorldState.current();
        boolean server = entry.type().equals("server");
        this.mutable = !(server && !world.canEditServerConfig());

        ConfigTrees.Tree read;
        try {
            if (!mutable) {
                read = ConfigTrees.read(entry);
            } else {
                if (server && world == WorldState.NO_WORLD) {
                    // Outside a world the in-memory server config can still be the one a remote server sent: go back to the file.
                    ConfigTrees.rereadFromDisk(entry);
                }
                read = ConfigTrees.readForEditing(entry);
            }
        } catch (RuntimeException e) {
            ConfigMenu.LOGGER.error("Could not read the {} config of {}", entry.type(), entry.modId(), e);
            read = new ConfigTrees.Tree(new GroupNode(entry.type(), ConfigComment.EMPTY, List.of()), 0, 0);
        }
        this.tree = read;
        trail.add(tree.root());
    }

    // ---- what the rows ask ----------------------------------------------------------------------------------------

    public boolean isMutable() {
        return mutable;
    }

    public GroupNode currentGroup() {
        return trail.getLast();
    }

    /** Path of the current page: empty for the file itself, {@code /group/subgroup} below it. */
    public String currentPath() {
        StringBuilder path = new StringBuilder();
        for (int i = 1; i < trail.size(); i++) {
            path.append('/').append(trail.get(i).name());
        }
        return path.toString();
    }

    public Object valueOf(String path, LeafNode leaf) {
        Staged staged = changes.get(path);
        return staged != null ? staged.value() : leaf.get();
    }

    public void stage(String path, LeafNode leaf, Object value) {
        if (!mutable) {
            return;
        }
        if (Objects.equals(value, leaf.get())) {
            changes.remove(path);
        } else {
            changes.put(path, new Staged(leaf, value));
        }
    }

    public boolean isDirty(String path) {
        return changes.containsKey(path);
    }

    public Map<String, Staged> changes() {
        return Collections.unmodifiableMap(changes);
    }

    public EntryList list() {
        return list;
    }

    public ConfigRegistry.Entry entry() {
        return entry;
    }

    public ConfigTrees.Tree tree() {
        return tree;
    }

    /** Opens a group of the current page as the new page. */
    public void open(GroupNode group) {
        trail.add(group);
        rebuildWidgets();
    }

    // ---- actions --------------------------------------------------------------------------------------------------

    /** Writes every staged edit to the JSON file. */
    public void saveChanges() {
        for (Staged staged : new ArrayList<>(changes.values())) {
            try {
                staged.leaf().set(staged.value());
            } catch (RuntimeException e) {
                ConfigMenu.LOGGER.error("Could not save option {}", staged.leaf().name(), e);
            }
        }
        changes.clear();
        if (list != null) {
            list.refreshValues();
        }
    }

    public void clearChanges() {
        changes.clear();
        if (list != null) {
            list.refreshValues();
        }
    }

    /** Stages the default of every option of the file (nothing is written until save). */
    public void stageDefaults() {
        stageDefaults(tree.root(), "");
        if (list != null) {
            list.refreshValues();
        }
    }

    private void stageDefaults(GroupNode group, String path) {
        for (ConfigNode node : group.children()) {
            if (node instanceof GroupNode sub) {
                stageDefaults(sub, path + "/" + sub.name());
            } else if (node instanceof LeafNode leaf) {
                stage(path + "/" + leaf.name(), leaf, leaf.defaultValue());
            }
        }
    }

    // ---- screen ---------------------------------------------------------------------------------------------------

    @Override
    protected void init() {
        super.init();
        int listWidth = Math.min(width - 80, 300);
        int yCenter = height / 2;
        int listL = width / 2 - listWidth / 2;
        int listR = width / 2 + listWidth / 2;

        IconBox resetAll = iconButton(listR + 10, yCenter - 25, AllIcons.I_CONFIG_RESET, "reset_all", () -> confirm(
            Component.translatable("create_fly_config_menu.ui.resetting_message", Component.translatable("create_fly_config_menu.type." + entry.type())),
            false, response -> {
                if (response == ConfirmationDialog.Response.CONFIRM) {
                    stageDefaults();
                }
            }));
        resetAll.active = mutable;
        resetAll.updateGradientFromState();

        saveButton = iconButton(listL - 30, yCenter - 25, AllIcons.I_CONFIG_SAVE, "save", () -> {
            if (changes.isEmpty()) {
                return;
            }
            confirm(Component.translatable("create_fly_config_menu.ui.saving_message", changes.size()), false, response -> {
                if (response == ConfirmationDialog.Response.CONFIRM) {
                    saveChanges();
                }
            });
        });

        discardButton = iconButton(listL - 30, yCenter + 5, AllIcons.I_CONFIG_DISCARD, "discard", () -> {
            if (changes.isEmpty()) {
                return;
            }
            confirm(Component.translatable("create_fly_config_menu.ui.discarding_message", changes.size()), false, response -> {
                if (response == ConfirmationDialog.Response.CONFIRM) {
                    clearChanges();
                }
            });
        });

        iconButton(listL - 30, yCenter + 65, AllIcons.I_CONFIG_BACK, "go_back", this::attemptBackstep);

        search = new EditBox(font, width / 2 - listWidth / 2, height - 35, listWidth, 20, Component.translatable("create_fly_config_menu.search"));
        search.setValue(searchText);
        search.setResponder(query -> {
            searchText = query.toLowerCase(Locale.ROOT);
            if (list != null) {
                list.updateFilter(searchText);
            }
        });
        search.setHint(Component.translatable("create_fly_config_menu.search"));
        search.moveCursorToStart(false);
        addRenderableWidget(search);

        list = new EntryList(this, listWidth, height - 80, 35, 40, searchText);
        list.setX(width / 2 - list.getWidth() / 2);
        list.updateSizeAndPosition(listWidth, height - 80, width / 2 - listWidth / 2, 35);
        addRenderableWidget(list);

        if (!mutable) {
            IconBox locked = new IconBox(listR + 10, yCenter + 5, 20, 20, AllIcons.I_CONFIG_LOCKED);
            locked.withPadding(2, 2);
            locked.withBorderColors(AbstractSimiWidget.COLOR_FAIL);
            locked.setTint(AbstractSimiWidget.COLOR_FAIL);
            locked.getToolTip().add(Component.translatable("create_fly_config_menu.ui.server_locked"));
            locked.getToolTip().addAll(FontHelper.cutTextComponent(Component.translatable("create_fly_config_menu.ui.server_locked.tooltip"), FontHelper.Palette.ALL_GRAY));
            addRenderableWidget(locked);
        }
        updateActionButtons();
    }

    private IconBox iconButton(int x, int y, AllIcons icon, String key, Runnable callback) {
        IconBox button = new IconBox(x, y, 20, 20, icon);
        button.withPadding(2, 2);
        button.withCallback(callback);
        button.getToolTip().add(Component.translatable("create_fly_config_menu.ui." + key));
        button.getToolTip().addAll(FontHelper.cutTextComponent(Component.translatable("create_fly_config_menu.ui." + key + ".tooltip"), FontHelper.Palette.ALL_GRAY));
        addRenderableWidget(button);
        return button;
    }

    private void updateActionButtons() {
        boolean any = mutable && !changes.isEmpty();
        for (IconBox button : new IconBox[]{saveButton, discardButton}) {
            if (button != null && button.active != any) {
                button.active = any;
                button.animateGradientFromState();
            }
        }
    }

    private void confirm(Component message, boolean threeWay, java.util.function.Consumer<ConfirmationDialog.Response> action) {
        minecraft.gui.setScreen(new ConfirmationDialog(this, message, threeWay, action));
    }

    @Override
    public void tick() {
        super.tick();
        updateActionButtons();
        if (list != null) {
            list.tickRows();
        }
    }

    @Override
    protected void renderWindow(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        graphics.centeredText(font, breadcrumbs(), width / 2, 15, ConfigUi.TEXT);
    }

    private String breadcrumbs() {
        // The mod id, like Create's menu ("create > client > ..."), not its display name.
        StringBuilder crumbs = new StringBuilder(entry.modId()).append(" > ").append(entry.type());
        for (int i = 1; i < trail.size(); i++) {
            crumbs.append(" > ").append(trail.get(i).name());
        }
        return crumbs.toString();
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
            attemptBackstep();
            return true;
        }
        return false;
    }

    /** One page up; from the top page, back to the previous screen (asking first when there are staged edits). */
    public void attemptBackstep() {
        if (trail.size() > 1) {
            trail.removeLast();
            rebuildWidgets();
            return;
        }
        if (changes.isEmpty()) {
            minecraft.gui.setScreen(parent);
            return;
        }
        confirm(Component.translatable("create_fly_config_menu.ui.leaving_message", changes.size()), true, response -> {
            if (response == ConfirmationDialog.Response.CANCEL) {
                return;
            }
            if (response == ConfirmationDialog.Response.CONFIRM) {
                saveChanges();
            }
            changes.clear();
            minecraft.gui.setScreen(parent);
        });
    }

    @Override
    public void onClose() {
        attemptBackstep();
    }

}
