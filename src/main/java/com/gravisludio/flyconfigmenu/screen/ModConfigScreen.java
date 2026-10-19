package com.gravisludio.flyconfigmenu.screen;

import com.gravisludio.flyconfigmenu.ConfigMenu;
import com.gravisludio.flyconfigmenu.config.ConfigNode;
import com.gravisludio.flyconfigmenu.config.ConfigRegistry;
import com.gravisludio.flyconfigmenu.config.ConfigTrees;
import com.gravisludio.flyconfigmenu.config.GroupNode;
import com.gravisludio.flyconfigmenu.config.LeafNode;
import com.gravisludio.flyconfigmenu.config.WorldState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The editor for one mod: a tab per config file (client, common, server), a search box and the tree of options.
 *
 * <p>Edits are written to the JSON file as soon as the value is valid. The {@code server} tab is read-only while the
 * player is connected to someone else's server, because Create Fly replaces the in-memory server config with the
 * remote one and saving would put the remote values into the local file.
 */
public class ModConfigScreen extends Screen {
    private static final List<String> TYPE_ORDER = List.of("client", "common", "server");

    private final Screen parent;
    private final String modId;
    private final List<ConfigRegistry.Entry> entries;
    private final Map<String, Button> tabs = new LinkedHashMap<>();
    private final Map<String, Set<String>> expanded = new LinkedHashMap<>();
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this, 84, 33);
    private ConfigRegistry.Entry selected;
    private ConfigTrees.Tree tree;
    private WorldState world = WorldState.current();
    private boolean readOnly;
    private String query = "";
    private ConfigList list;
    private StringWidget status;
    private EditBox search;

    public ModConfigScreen(Screen parent, String modId) {
        super(Component.literal(ConfigMenu.displayName(modId)));
        this.parent = parent;
        this.modId = modId;
        this.entries = ConfigRegistry.forMod(modId).stream()
            .sorted(Comparator.comparingInt(e -> TYPE_ORDER.indexOf(e.type()) < 0 ? TYPE_ORDER.size() : TYPE_ORDER.indexOf(e.type())))
            .toList();
        this.selected = entries.isEmpty() ? null : entries.getFirst();
    }

    /** Which config file this screen is showing, for tests. */
    public ConfigRegistry.Entry selectedEntry() {
        return selected;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public ConfigTrees.Tree tree() {
        return tree;
    }

    public ConfigList list() {
        return list;
    }

    @Override
    protected void init() {
        LinearLayout header = layout.addToHeader(LinearLayout.vertical().spacing(4));
        header.addChild(new StringWidget(title, font), s -> s.alignHorizontallyCenter());

        if (selected == null) {
            layout.addToContents(new StringWidget(Component.translatable("create_fly_config_menu.screen.empty"), font));
        } else {
            LinearLayout tabRow = header.addChild(LinearLayout.horizontal().spacing(4), s -> s.alignHorizontallyCenter());
            for (ConfigRegistry.Entry entry : entries) {
                Button tab = Button.builder(Component.translatable("create_fly_config_menu.type." + entry.type()), b -> select(entry)).width(80).build();
                tabs.put(entry.type(), tab);
                tabRow.addChild(tab);
            }

            search = new EditBox(font, 200, 20, Component.translatable("create_fly_config_menu.search"));
            search.setHint(Component.translatable("create_fly_config_menu.search"));
            search.setResponder(text -> {
                query = text.strip().toLowerCase();
                refreshRows();
            });
            header.addChild(search, s -> s.alignHorizontallyCenter());

            status = header.addChild(new StringWidget(Component.empty(), font), s -> s.alignHorizontallyCenter());

            list = layout.addToContents(new ConfigList(minecraft, width, layout.getContentHeight(), layout.getHeaderHeight()));
            select(selected);
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

    /** Opens a tab: re-reads what has to be re-read, builds the tree and the rows. */
    public void select(ConfigRegistry.Entry entry) {
        selected = entry;
        world = WorldState.current();
        boolean server = entry.type().equals("server");
        readOnly = server && !world.canEditServerConfig();
        try {
            if (readOnly) {
                tree = ConfigTrees.read(entry);
            } else {
                if (server && world == WorldState.NO_WORLD) {
                    // Outside a world the in-memory server config can still be the one a remote server sent: go back to the file.
                    ConfigTrees.rereadFromDisk(entry);
                }
                tree = ConfigTrees.readForEditing(entry);
            }
            if (tree.reachable() < tree.bound()) {
                ConfigMenu.LOGGER.warn("{} {}: only {} of {} options found in the JSON tree", modId, entry.type(), tree.reachable(), tree.bound());
            }
        } catch (RuntimeException e) {
            ConfigMenu.LOGGER.error("Could not read the {} config of {}", entry.type(), modId, e);
            tree = new ConfigTrees.Tree(new GroupNode(entry.type(), com.gravisludio.flyconfigmenu.config.ConfigComment.EMPTY, List.of()), 0, 0);
        }
        tabs.forEach((type, tab) -> tab.active = !type.equals(entry.type()));
        if (status != null) {
            status.setMessage(statusFor(entry.type()));
            status.setMaxWidth(Math.max(120, width - 40));
        }
        refreshRows();
        repositionElements();
    }

    private Component statusFor(String type) {
        if (readOnly) {
            return Component.translatable("create_fly_config_menu.status.readonly").withStyle(ChatFormatting.GOLD);
        }
        String key = switch (type) {
            case "server" -> world == WorldState.SINGLEPLAYER ? "create_fly_config_menu.status.server.world" : "create_fly_config_menu.status.server.menu";
            case "common" -> "create_fly_config_menu.status.common";
            default -> "create_fly_config_menu.status.client";
        };
        return Component.translatable(key).withStyle(ChatFormatting.GRAY);
    }

    private void refreshRows() {
        if (list == null || tree == null) {
            return;
        }
        List<ConfigList.Row> rows = new ArrayList<>();
        addRows(tree.root(), "", 0, rows);
        list.setRows(rows);
    }

    private void addRows(GroupNode group, String path, int depth, List<ConfigList.Row> out) {
        Set<String> open = expanded.computeIfAbsent(selected.type(), k -> new HashSet<>());
        boolean searching = !query.isEmpty();
        for (ConfigNode child : group.children()) {
            if (child instanceof LeafNode leaf) {
                if (!searching || matches(leaf)) {
                    out.add(new ConfigList.ValueRow(font, leaf, depth, !readOnly, () -> { }));
                }
            } else if (child instanceof GroupNode sub) {
                String subPath = path + "/" + sub.name();
                // A search opens every group and hides the ones with no match inside.
                boolean isOpen = searching || open.contains(subPath);
                List<ConfigList.Row> inner = new ArrayList<>();
                if (isOpen) {
                    addRows(sub, subPath, depth + 1, inner);
                }
                if (searching && inner.isEmpty()) {
                    continue;
                }
                out.add(new ConfigList.GroupRow(sub, depth, isOpen, () -> toggle(subPath)));
                out.addAll(inner);
            }
        }
    }

    private boolean matches(LeafNode leaf) {
        return leaf.name().toLowerCase().contains(query) || leaf.comment().matches(query);
    }

    private void toggle(String path) {
        Set<String> open = expanded.computeIfAbsent(selected.type(), k -> new HashSet<>());
        if (!open.remove(path)) {
            open.add(path);
        }
        refreshRows();
    }

    /** Types into the search box, as the player would. */
    public void setQuery(String text) {
        if (search != null) {
            search.setValue(text);
        }
    }

    /** Opens every group of the current tab. */
    public void expandAll() {
        if (tree == null) {
            return;
        }
        collectPaths(tree.root(), "", expanded.computeIfAbsent(selected.type(), k -> new HashSet<>()));
        refreshRows();
    }

    private static void collectPaths(GroupNode group, String path, Set<String> out) {
        for (ConfigNode child : group.children()) {
            if (child instanceof GroupNode sub) {
                String subPath = path + "/" + sub.name();
                out.add(subPath);
                collectPaths(sub, subPath, out);
            }
        }
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
