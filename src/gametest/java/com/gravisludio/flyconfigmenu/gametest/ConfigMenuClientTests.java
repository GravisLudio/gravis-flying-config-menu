package com.gravisludio.flyconfigmenu.gametest;

import com.google.gson.JsonObject;
import com.gravisludio.flyconfigmenu.config.ConfigComment;
import com.gravisludio.flyconfigmenu.config.ConfigNode;
import com.gravisludio.flyconfigmenu.config.ConfigRegistry;
import com.gravisludio.flyconfigmenu.config.ConfigTrees;
import com.gravisludio.flyconfigmenu.config.GroupNode;
import com.gravisludio.flyconfigmenu.config.LeafNode;
import com.gravisludio.flyconfigmenu.config.ValueCodec;
import com.gravisludio.flyconfigmenu.config.WorldState;
import com.gravisludio.flyconfigmenu.screen.ConfigList;
import com.gravisludio.flyconfigmenu.screen.ConfigOverviewScreen;
import com.gravisludio.flyconfigmenu.screen.ModConfigScreen;
import com.zurrtum.create.catnip.config.Builder;
import com.zurrtum.create.infrastructure.config.AllConfigs;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
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
 * <p>What this cannot check: the read-only server tab on a REMOTE server (needs a second machine or a dedicated server).
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
        step("stale builder state after reloads", this::staleBuilder);
        step("re-read from disk", this::rereadFromDisk);
        step("screens", () -> screens(context));

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
     * Create Fly's own {@code CServer.reload} re-registers without saving. Two in a row leave a stale root at the bottom of
     * the builder's stack. This reproduces that, reports what plain {@code CValue.set} does, and checks that our path edits correctly.
     */
    private void staleBuilder() throws IOException {
        ConfigRegistry.Entry server = create("server");
        AllConfigs.server().reload(null);
        AllConfigs.server().reload(null);

        AllConfigs.server().tickrateSyncTimer.set(27);
        int plain = tickrateOnDisk(server);
        log("upstream CValue.set after two reloads wrote " + plain + " to disk (27 = fine, 20 = the stale-root bug is real)");

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

    private void screens(ClientGameTestContext context) {
        // Overview: one button per mod with a config.
        context.setScreen(() -> new ConfigOverviewScreen(Minecraft.getInstance().gui.screen()));
        context.waitTicks(10);
        context.takeScreenshot("overview");
        expect(context.computeOnClient(mc -> mc.gui.screen() instanceof ConfigOverviewScreen), "the overview screen did not open");

        // Editor for Create, starting on the client tab.
        context.setScreen(() -> new ModConfigScreen(Minecraft.getInstance().gui.screen(), "create"));
        context.waitTicks(10);
        ModConfigScreen screen = context.computeOnClient(mc -> mc.gui.screen() instanceof ModConfigScreen s ? s : null);
        expect(screen != null, "the editor screen did not open");
        if (screen == null) {
            return;
        }
        log("editor opened on tab " + screen.selectedEntry().type() + ", read-only " + screen.isReadOnly());
        context.takeScreenshot("editor-client-collapsed");
        int collapsedRows = context.computeOnClient(mc -> screen.list().children().size());
        context.runOnClient(mc -> screen.expandAll());
        context.waitTicks(5);
        int expandedRows = context.computeOnClient(mc -> screen.list().children().size());
        log("client tab rows: " + collapsedRows + " collapsed, " + expandedRows + " expanded");
        expect(expandedRows > collapsedRows && collapsedRows > 0, "expandAll did not add rows (" + collapsedRows + " -> " + expandedRows + ")");
        context.takeScreenshot("editor-client-expanded");

        // Server tab, searching for the tick rate option; then type a new value into its box like a player would.
        context.runOnClient(mc -> screen.select(ConfigRegistry.forMod("create").stream().filter(e -> e.type().equals("server")).findFirst().orElseThrow()));
        context.waitTicks(5);
        expect(!screen.isReadOnly(), "the server tab is read-only at the title screen");
        context.runOnClient(mc -> screen.setQuery("tickrate"));
        context.waitTicks(5);
        int searchRows = context.computeOnClient(mc -> screen.list().children().size());
        expect(searchRows >= 2 && searchRows <= 6, "searching 'tickrate' left " + searchRows + " rows (expected a group header and the option)");
        context.takeScreenshot("editor-server-search");

        context.runOnClient(mc -> {
            ConfigList.Row row = screen.list().children().stream().filter(r -> r instanceof ConfigList.ValueRow).findFirst().orElseThrow();
            EditBox box = (EditBox) row.children().getFirst();
            box.setValue("31");
        });
        context.waitTicks(3);
        try {
            expect(tickrateOnDisk(create("server")) == 31, "typing 31 in the box did not reach server.json");
        } catch (IOException e) {
            failures.add("could not read server.json: " + e);
        }
        context.takeScreenshot("editor-server-edited");
        context.runOnClient(mc -> {
            ConfigList.Row row = screen.list().children().stream().filter(r -> r instanceof ConfigList.ValueRow).findFirst().orElseThrow();
            ((EditBox) row.children().getFirst()).setValue("2");   // below the minimum of 5: refused, red text
        });
        context.waitTicks(3);
        try {
            expect(tickrateOnDisk(create("server")) == 31, "an invalid value (2) was written to server.json");
        } catch (IOException e) {
            failures.add("could not read server.json: " + e);
        }
        context.takeScreenshot("editor-server-invalid");

        // Put the default back so the run directory ends clean.
        try {
            find(ConfigTrees.readForEditing(create("server")).root(), "infrastructure", "tickrateSyncTimer").reset();
        } catch (RuntimeException e) {
            failures.add("could not reset tickrateSyncTimer: " + e);
        }

        context.runOnClient(mc -> mc.gui.screen().onClose());
        context.waitTicks(5);
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
