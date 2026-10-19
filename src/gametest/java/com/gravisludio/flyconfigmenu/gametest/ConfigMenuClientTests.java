package com.gravisludio.flyconfigmenu.gametest;

import com.google.gson.JsonObject;
import com.gravisludio.flyconfigmenu.ConfigMenu;
import com.gravisludio.flyconfigmenu.config.ConfigComment;
import com.gravisludio.flyconfigmenu.config.ConfigNode;
import com.gravisludio.flyconfigmenu.config.ConfigRegistry;
import com.gravisludio.flyconfigmenu.config.ConfigTrees;
import com.gravisludio.flyconfigmenu.config.GroupNode;
import com.gravisludio.flyconfigmenu.config.LeafNode;
import com.gravisludio.flyconfigmenu.config.ValueCodec;
import com.gravisludio.flyconfigmenu.config.WorldState;
import com.gravisludio.flyconfigmenu.ui.ConfigUi;
import com.gravisludio.flyconfigmenu.ui.ConfirmationDialog;
import com.gravisludio.flyconfigmenu.ui.EditorScreen;
import com.gravisludio.flyconfigmenu.ui.EntryList;
import com.gravisludio.flyconfigmenu.ui.ModListScreen;
import com.gravisludio.flyconfigmenu.ui.RootScreen;
import com.terraformersmc.modmenu.ModMenu;
import com.zurrtum.create.catnip.config.Builder;
import com.zurrtum.create.infrastructure.config.AllConfigs;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Real-client test of the config menu on Minecraft 26.2 with the real Create Fly. Run with {@code gradlew runClientGameTest}
 * or {@code herramientas\probar-cliente.ps1} (Minecraft opens a window, stays on the title screen, and closes). Look for
 * the {@code [CFCM-TEST]} markers: the checks are collected and the test fails at the end, so one run reports everything
 * that is wrong. Screenshots: {@code build/run/clientGameTest/screenshots}.
 *
 * <p>What this cannot check: the read-only server file on a REMOTE server (needs a second machine or a dedicated server).
 */
public class ConfigMenuClientTests implements FabricClientGameTest {
    private final List<String> failures = new ArrayList<>();

    @Override
    public void runTest(ClientGameTestContext context) {
        context.waitForScreen(TitleScreen.class);
        context.waitTicks(20);

        step("registry", this::registry);
        step("codecs", this::codecs);
        step("tree and editing", this::treeAndEditing);
        step("builder state after reloads", this::builderAfterReloads);
        step("re-read from disk", this::rereadFromDisk);
        step("screens", () -> screens(context));
        step("Mod Menu", () -> modMenu(context));

        context.runOnClient(mc -> mc.gui.setScreen(null));
        context.waitTicks(5);

        if (failures.isEmpty()) {
            log("ALL OK");
        } else {
            failures.forEach(f -> log("FAIL: " + f));
            throw new AssertionError(failures.size() + " check(s) failed: " + failures);
        }
    }

    // ---- registry -------------------------------------------------------------------------------------------------

    private void registry() {
        List<ConfigRegistry.Entry> entries = ConfigRegistry.entries();
        for (ConfigRegistry.Entry entry : entries) {
            log("found " + entry.modId() + " / " + entry.type() + " -> " + entry.root().getClass().getName());
        }
        for (String type : List.of("common", "server", "client")) {
            expect(entries.stream().anyMatch(e -> e.modId().equals("create") && e.type().equals(type)),
                "Create Fly's " + type + " config was not found by the mixin");
        }
    }

    // ---- pure functions -------------------------------------------------------------------------------------------

    private void codecs() {
        expect("#FF336699".equals(ValueCodec.formatInt(0xFF336699, "#")), "formatInt # gave " + ValueCodec.formatInt(0xFF336699, "#"));
        expect(Integer.valueOf(0xFF336699).equals(ValueCodec.parseInt("#ff336699", "#")), "parseInt of a colour with # failed");
        expect(Integer.valueOf(0xAB).equals(ValueCodec.parseInt("0xAB", "0x")), "parseInt 0xAB failed");
        expect(Integer.valueOf(5).equals(ValueCodec.parseInt("0b101", "0b")), "parseInt 0b101 failed");
        expect(Integer.valueOf(-7).equals(ValueCodec.parseInt(" -7 ", null)), "parseInt -7 failed");
        expect(ValueCodec.parseInt("abc", null) == null && ValueCodec.parseInt("", null) == null, "parseInt accepted garbage");
        expect(Float.valueOf(1.5f).equals(ValueCodec.parseFloat("1.5")), "parseFloat 1.5 failed");
        expect(ValueCodec.parseFloat("NaN") == null && ValueCodec.parseFloat("x") == null, "parseFloat accepted garbage");

        ConfigComment comment = ConfigComment.parse(List.of("Hello", "Default: 20", "Range: 5 ~ 9", "[@cui:RequiresRestart:both]", "[@cui:RequiresRelog] Rejoin", "[@cui:IntDisplay:#]"));
        expect(comment.lines().equals(List.of("Hello", "Rejoin")), "comment lines were " + comment.lines());
        expect("both".equals(comment.restart()) && comment.relog() && "#".equals(comment.intDisplay()), "comment annotations were not read: " + comment);
        expect(!WorldState.REMOTE.canEditServerConfig() && WorldState.NO_WORLD.canEditServerConfig() && WorldState.SINGLEPLAYER.canEditServerConfig(),
            "WorldState.canEditServerConfig is wrong");
        expect("Tickrate Sync Timer".equals(ConfigUi.humanize("tickrateSyncTimer")), "humanize gave " + ConfigUi.humanize("tickrateSyncTimer"));
        expect("Enable Foo".equals(ConfigUi.humanize("enable_foo")), "humanize of snake_case gave " + ConfigUi.humanize("enable_foo"));
    }

    // ---- the tree of Create's server config, edited for real --------------------------------------------------------

    private ConfigRegistry.Entry create(String type) {
        return ConfigRegistry.forMod("create").stream().filter(e -> e.type().equals(type)).findFirst().orElseThrow();
    }

    private static LeafNode find(GroupNode group, String... path) {
        ConfigNode node = group;
        for (String name : path) {
            node = ((GroupNode) node).children().stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow(() -> new AssertionError("no node " + name));
        }
        return (LeafNode) node;
    }

    private static void count(GroupNode group, Map<LeafNode.Kind, Integer> kinds) {
        for (ConfigNode child : group.children()) {
            if (child instanceof GroupNode sub) {
                count(sub, kinds);
            } else {
                kinds.merge(((LeafNode) child).kind(), 1, Integer::sum);
            }
        }
    }

    private static JsonObject readFile(ConfigRegistry.Entry entry) throws IOException {
        Builder builder = entry.builder();
        File file = new File(builder.configDir, builder.type + ".json");
        return Builder.GSON.fromJson(Files.readString(file.toPath(), StandardCharsets.UTF_8), JsonObject.class);
    }

    private static int tickrateOnDisk(ConfigRegistry.Entry entry) throws IOException {
        return readFile(entry).getAsJsonObject("infrastructure").getAsJsonObject("tickrateSyncTimer").get("value").getAsInt();
    }

    private void treeAndEditing() throws IOException {
        ConfigRegistry.Entry server = create("server");
        ConfigTrees.Tree tree = ConfigTrees.readForEditing(server);
        Map<LeafNode.Kind, Integer> kinds = new EnumMap<>(LeafNode.Kind.class);
        count(tree.root(), kinds);
        log("server tree: " + tree.bound() + " bound options, " + tree.reachable() + " reached in JSON, kinds " + kinds);
        expect(tree.bound() > 30, "the server tree has only " + tree.bound() + " options");
        expect(tree.reachable() == tree.bound(), "only " + tree.reachable() + " of " + tree.bound() + " options were found in the JSON tree");
        expect(kinds.getOrDefault(LeafNode.Kind.BOOLEAN, 0) > 0 && kinds.getOrDefault(LeafNode.Kind.INT, 0) > 0, "no boolean/int options found");
        expect(!kinds.containsKey(LeafNode.Kind.UNSUPPORTED), "unsupported option kinds in Create's server tree: " + kinds);

        LeafNode tick = find(tree.root(), "infrastructure", "tickrateSyncTimer");
        expect(tick.kind() == LeafNode.Kind.INT && tick.min() == 5 && Integer.valueOf(20).equals(tick.defaultValue()), "tickrateSyncTimer metadata is wrong");
        expect(tick.comment().lines().size() >= 1, "tickrateSyncTimer has no comment text");

        tick.set(25);
        expect(Integer.valueOf(25).equals(tick.get()), "set(25) did not change the value in memory");
        expect(tickrateOnDisk(server) == 25, "set(25) is not in server.json (" + tickrateOnDisk(server) + ")");
        expect(!tick.isDefault(), "isDefault is true after a change");
        boolean threw = false;
        try {
            tick.set(1);
        } catch (IllegalArgumentException expected) {
            threw = true;
        }
        expect(threw && tickrateOnDisk(server) == 25, "an out-of-range value was not refused");
        tick.reset();
        expect(tickrateOnDisk(server) == 20 && tick.isDefault(), "reset did not put 20 back on disk");

        // Client and common configs read too.
        for (String type : List.of("client", "common")) {
            ConfigTrees.Tree other = ConfigTrees.readForEditing(create(type));
            log(type + " tree: " + other.bound() + " bound, " + other.reachable() + " reached");
            expect(other.bound() > 0 && other.reachable() == other.bound(), type + " tree is incomplete: " + other.reachable() + "/" + other.bound());
        }
    }

    /**
     * Create Fly's own {@code CServer.reload} re-registers without saving. This runs it twice, logs the builder's state and
     * checks that both a plain {@code CValue.set} and our own path still write the right value. (An earlier suspicion that
     * the second reload leaves a stale root was NOT confirmed by this test.)
     */
    private void builderAfterReloads() throws IOException {
        ConfigRegistry.Entry server = create("server");
        log("builder before the reloads: " + ConfigTrees.describeBuilder(server));
        AllConfigs.server().reload(null);
        log("builder after one reload: " + ConfigTrees.describeBuilder(server));
        AllConfigs.server().reload(null);
        log("builder after two reloads: " + ConfigTrees.describeBuilder(server));

        AllConfigs.server().tickrateSyncTimer.set(27);
        int plain = tickrateOnDisk(server);
        log("upstream CValue.set after two reloads wrote " + plain + " to disk (27 = fine)");
        expect(plain == 27, "a plain CValue.set after two reloads wrote " + plain + " instead of 27");

        AllConfigs.server().reload(null);
        AllConfigs.server().reload(null);
        ConfigTrees.Tree tree = ConfigTrees.readForEditing(server);
        expect(tree.reachable() == tree.bound() && tree.bound() > 30, "after two reloads the tree has " + tree.reachable() + "/" + tree.bound());
        LeafNode tick = find(tree.root(), "infrastructure", "tickrateSyncTimer");
        tick.set(26);
        expect(tickrateOnDisk(server) == 26, "after two reloads our edit wrote " + tickrateOnDisk(server) + " instead of 26");
        tick.reset();
        expect(tickrateOnDisk(server) == 20, "reset after reloads left " + tickrateOnDisk(server));
    }

    private void rereadFromDisk() throws IOException {
        ConfigRegistry.Entry server = create("server");
        // Someone edits the file by hand (or a remote server's values are still in memory); we go back to the file.
        Builder builder = server.builder();
        File file = new File(builder.configDir, builder.type + ".json");
        JsonObject onDisk = readFile(server);
        onDisk.getAsJsonObject("infrastructure").getAsJsonObject("tickrateSyncTimer").addProperty("value", 33);
        Files.writeString(file.toPath(), Builder.GSON.toJson(onDisk), StandardCharsets.UTF_8);
        ConfigTrees.rereadFromDisk(server);
        LeafNode again = find(ConfigTrees.readForEditing(server).root(), "infrastructure", "tickrateSyncTimer");
        expect(Integer.valueOf(33).equals(again.get()), "rereadFromDisk did not pick up the file (value " + again.get() + ")");
        expect(Integer.valueOf(33).equals(AllConfigs.server().tickrateSyncTimer.get()), "Create's own server config does not see the re-read value");

        again.reset();
        expect(tickrateOnDisk(server) == 20, "reset after re-read left " + tickrateOnDisk(server));
    }

    // ---- screens --------------------------------------------------------------------------------------------------

    private static EditorScreen editor(ClientGameTestContext context) {
        return context.computeOnClient(mc -> mc.gui.screen() instanceof EditorScreen e ? e : null);
    }

    private static <R extends EntryList.Row> R firstRow(EditorScreen screen, Class<R> type) {
        return screen.list().children().stream().filter(type::isInstance).map(type::cast).findFirst().orElse(null);
    }

    /** The groups to open, from the file's top page, to reach a page that has an option of this kind; empty if the top page has one; null if none. */
    private static List<GroupNode> pathToKind(GroupNode group, LeafNode.Kind kind) {
        for (ConfigNode child : group.children()) {
            if (child instanceof LeafNode leaf && leaf.kind() == kind) {
                return new ArrayList<>();
            }
        }
        for (ConfigNode child : group.children()) {
            if (child instanceof GroupNode sub) {
                List<GroupNode> path = pathToKind(sub, kind);
                if (path != null) {
                    path.addFirst(sub);
                    return path;
                }
            }
        }
        return null;
    }

    private static void backToTop(EditorScreen editor) {
        while (!editor.currentGroup().name().equals(editor.entry().type())) {
            editor.attemptBackstep();
        }
    }

    private void screens(ClientGameTestContext context) throws IOException {
        // The list of mods and the root screen of Create.
        context.setScreen(() -> new ModListScreen(Minecraft.getInstance().gui.screen()));
        context.waitTicks(10);
        context.takeScreenshot("01-modlist");
        expect(context.computeOnClient(mc -> mc.gui.screen() instanceof ModListScreen), "the mod list did not open");

        context.setScreen(() -> new RootScreen(Minecraft.getInstance().gui.screen(), "create"));
        context.waitTicks(10);
        context.takeScreenshot("02-root");
        expect(context.computeOnClient(mc -> mc.gui.screen() instanceof RootScreen), "the root screen did not open");

        // The editor for Create's server file.
        ConfigRegistry.Entry serverEntry = create("server");
        context.setScreen(() -> new EditorScreen(Minecraft.getInstance().gui.screen(), serverEntry));
        context.waitTicks(10);
        EditorScreen editor = editor(context);
        expect(editor != null, "the editor did not open");
        if (editor == null) {
            return;
        }
        expect(editor.isMutable(), "the server file is read-only at the title screen");
        context.takeScreenshot("03-editor-top");
        int topRows = context.computeOnClient(mc -> editor.list().children().size());
        EntryList.NestedRow firstGroup = context.computeOnClient(mc -> firstRow(editor, EntryList.NestedRow.class));
        log("editor top page: " + topRows + " rows");
        expect(topRows > 3 && firstGroup != null, "the top page has no groups to open");

        // Open a group, type a number: it is STAGED, the file does not change until save.
        GroupNode infrastructure = (GroupNode) editor.currentGroup().children().stream().filter(n -> n.name().equals("infrastructure")).findFirst().orElseThrow();
        context.runOnClient(mc -> editor.open(infrastructure));
        context.waitTicks(5);
        expect("infrastructure".equals(editor.currentGroup().name()), "opening the group did not change the page");
        EntryList.NumberRow number = context.computeOnClient(mc -> firstRow(editor, EntryList.NumberRow.class));
        expect(number != null, "no number row in the infrastructure page");
        if (number == null) {
            return;
        }
        context.takeScreenshot("04-editor-page");
        context.runOnClient(mc -> number.textField().setValue("31"));
        context.waitTicks(10);
        expect(editor.changes().size() == 1, "typing 31 did not stage one change (" + editor.changes().size() + ")");
        expect(tickrateOnDisk(serverEntry) == 20, "a staged value reached the file before save");
        context.takeScreenshot("05-editor-staged");

        context.runOnClient(mc -> number.textField().setValue("2"));   // below the minimum of 5
        context.waitTicks(3);
        expect(editor.changes().size() == 1 && Integer.valueOf(31).equals(editor.changes().values().iterator().next().value()), "an invalid value changed the staged edit");
        context.takeScreenshot("06-editor-invalid");
        context.runOnClient(mc -> number.textField().setValue("31"));
        context.waitTicks(3);

        // Going up a page does not ask; leaving the file with staged edits does, and Cancel keeps everything.
        context.runOnClient(mc -> editor.attemptBackstep());
        context.waitTicks(3);
        expect(editor.currentGroup().name().equals("server"), "going back did not return to the top page");
        context.runOnClient(mc -> editor.attemptBackstep());
        context.waitTicks(5);
        expect(context.computeOnClient(mc -> mc.gui.screen() instanceof ConfirmationDialog), "leaving with staged edits did not ask");
        context.takeScreenshot("07-leave-dialog");
        context.runOnClient(mc -> mc.gui.screen().onClose());   // Cancel
        context.waitTicks(5);
        expect(editor(context) == editor && editor.changes().size() == 1, "cancelling the question lost the editor or the staged edit");

        // Save writes the file.
        context.runOnClient(mc -> editor.saveChanges());
        expect(editor.changes().isEmpty() && tickrateOnDisk(serverEntry) == 31, "saving did not write 31 (" + tickrateOnDisk(serverEntry) + ")");

        // Reset all stages the defaults (nothing is written), discard drops them.
        context.runOnClient(mc -> editor.stageDefaults());
        expect(editor.changes().size() == 1 && tickrateOnDisk(serverEntry) == 31, "reset all staged " + editor.changes().size() + " change(s) (expected only the tick rate)");
        context.runOnClient(mc -> editor.clearChanges());
        expect(editor.changes().isEmpty(), "discard left staged edits");
        find(ConfigTrees.readForEditing(serverEntry).root(), "infrastructure", "tickrateSyncTimer").reset();

        // Boolean and enum rows, found by walking down the tree.
        for (LeafNode.Kind kind : List.of(LeafNode.Kind.BOOLEAN, LeafNode.Kind.ENUM)) {
            List<GroupNode> path = pathToKind(editor.tree().root(), kind);
            expect(path != null, "no " + kind + " option in the server tree");
            if (path == null) {
                continue;
            }
            context.runOnClient(mc -> {
                backToTop(editor);
                path.forEach(editor::open);
            });
            context.waitTicks(5);
            if (kind == LeafNode.Kind.BOOLEAN) {
                EntryList.BooleanRow row = context.computeOnClient(mc -> firstRow(editor, EntryList.BooleanRow.class));
                expect(row != null, "no boolean row on the page");
                context.runOnClient(mc -> editor.list().setScrollAmount(Math.max(0, editor.list().children().indexOf(row) * 40 - 40)));
                context.waitTicks(3);
                context.takeScreenshot("08-editor-boolean");
                if (row != null) {
                    context.runOnClient(mc -> row.click());
                }
            } else {
                EntryList.EnumRow row = context.computeOnClient(mc -> firstRow(editor, EntryList.EnumRow.class));
                expect(row != null, "no enum row on the page");
                context.runOnClient(mc -> editor.list().setScrollAmount(Math.max(0, editor.list().children().indexOf(row) * 40 - 40)));
                context.waitTicks(3);
                context.takeScreenshot("09-editor-enum");
                if (row != null) {
                    context.runOnClient(mc -> row.click(1));
                }
            }
            expect(editor.changes().size() == 1, "changing a " + kind + " option staged " + editor.changes().size() + " change(s)");
            context.waitTicks(10);
            context.takeScreenshot("10-editor-" + kind.name().toLowerCase() + "-staged");
            context.runOnClient(mc -> editor.clearChanges());
        }

        // Search from the top page: only the group that holds the tick rate stays.
        context.runOnClient(mc -> backToTop(editor));
        context.waitTicks(3);
        context.runOnClient(mc -> editor.list().updateFilter("tickrate"));
        context.waitTicks(3);
        int searchRows = context.computeOnClient(mc -> editor.list().children().size());
        expect(searchRows == 1, "searching 'tickrate' left " + searchRows + " row(s) (expected the one group)");
        context.takeScreenshot("11-editor-search");

        // The client file looks the same way.
        context.setScreen(() -> new EditorScreen(Minecraft.getInstance().gui.screen(), create("client")));
        context.waitTicks(10);
        context.takeScreenshot("12-editor-client");
        int clientRows = context.computeOnClient(mc -> editor(context).list().children().size());
        expect(clientRows > 5, "the client page shows " + clientRows + " row(s): the single wrapping group was not flattened");

        context.runOnClient(mc -> mc.gui.setScreen(null));
        context.waitTicks(5);
    }

    private void modMenu(ClientGameTestContext context) {
        expect(ModMenu.hasConfigScreen("create"), "Mod Menu has no config button for Create");
        expect(ModMenu.hasConfigScreen(ConfigMenu.MOD_ID), "Mod Menu has no config button for our own entry");
        boolean root = context.computeOnClient(mc -> ModMenu.getConfigScreen("create", mc.gui.screen()) instanceof RootScreen);
        boolean list = context.computeOnClient(mc -> ModMenu.getConfigScreen(ConfigMenu.MOD_ID, mc.gui.screen()) instanceof ModListScreen);
        log("Mod Menu: config button for create -> " + root + ", for this mod -> " + list);
        expect(root, "Mod Menu's config screen for Create is not our root screen");
        expect(list, "Mod Menu's config screen for this mod is not our mod list");
    }

    // ---- helpers --------------------------------------------------------------------------------------------------

    @FunctionalInterface
    private interface Step {
        void run() throws Exception;
    }

    private void step(String name, Step step) {
        try {
            step.run();
            log("step '" + name + "' done");
        } catch (Throwable t) {
            failures.add("step '" + name + "' threw " + t);
            t.printStackTrace();
        }
    }

    private void expect(boolean condition, String message) {
        if (!condition) {
            failures.add(message);
        }
    }

    private static void log(String message) {
        System.out.println("[CFCM-TEST] " + message);
    }
}
