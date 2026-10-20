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
import com.gravisludio.flyconfigmenu.ui.menu.CreateMenuScreen;
import com.gravisludio.flyconfigmenu.ui.menu.GogglesButton;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.PauseScreen;
import com.gravisludio.flyconfigmenu.ui.cog.CogShadow;
import com.terraformersmc.modmenu.ModMenu;
import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.catnip.config.Builder;
import com.zurrtum.create.content.contraptions.bearing.BearingContraption;
import com.zurrtum.create.content.contraptions.bearing.WindmillBearingBlockEntity;
import com.zurrtum.create.content.kinetics.base.KineticBlockEntity;
import com.zurrtum.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.zurrtum.create.infrastructure.config.AllConfigs;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

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
 * <p>The "gameplay" step opens a singleplayer world and checks that values saved in the menu change the game (Create's
 * maximum rotation speed breaks a too-fast shaft, the windmill speed follows the sails per RPM) and that the windmill
 * advancements are still given. The "remote server" step needs {@code eula=true} in the run folder.
 */
public class ConfigMenuClientTests implements FabricClientGameTest {
    private final List<String> failures = new ArrayList<>();

    @Override
    public void runTest(ClientGameTestContext context) {
        context.waitForScreen(TitleScreen.class);
        context.waitTicks(20);

        step("goggles button and Create menu", () -> goggles(context));
        step("registry", this::registry);
        step("codecs", this::codecs);
        step("tree and editing", this::treeAndEditing);
        step("builder state after reloads", this::builderAfterReloads);
        step("re-read from disk", this::rereadFromDisk);
        step("screens", () -> screens(context));
        step("Mod Menu", () -> modMenu(context));
        step("addons", () -> addons(context));
        step("gameplay", () -> gameplay(context));
        step("remote server", () -> remoteServer(context));

        context.runOnClient(mc -> mc.gui.setScreen(null));
        context.waitTicks(5);

        if (failures.isEmpty()) {
            log("ALL OK");
        } else {
            failures.forEach(f -> log("FAIL: " + f));
            throw new AssertionError(failures.size() + " check(s) failed: " + failures);
        }
    }

    // ---- the goggles button on the title screen and the Create menu behind it --------------------------------------

    private static boolean hasGoggles(ClientGameTestContext context) {
        return context.computeOnClient(mc -> mc.gui.screen() != null
            && Screens.getWidgets(mc.gui.screen()).stream().anyMatch(w -> w instanceof GogglesButton));
    }

    /** True if the goggles button covers another visible button of the screen (it must sit beside them). */
    private static boolean gogglesOverlap(ClientGameTestContext context) {
        return context.computeOnClient(mc -> {
            List<AbstractWidget> widgets = Screens.getWidgets(mc.gui.screen());
            for (AbstractWidget goggles : widgets) {
                if (!(goggles instanceof GogglesButton)) {
                    continue;
                }
                for (AbstractWidget other : widgets) {
                    if (other != goggles && other.visible
                        && other.getX() < goggles.getX() + goggles.getWidth() && goggles.getX() < other.getX() + other.getWidth()
                        && other.getY() < goggles.getY() + goggles.getHeight() && goggles.getY() < other.getY() + other.getHeight()) {
                        log("the goggles button covers '" + other.getMessage().getString() + "'");
                        return true;
                    }
                }
            }
            return false;
        });
    }

    private void goggles(ClientGameTestContext context) {
        expect(hasGoggles(context), "the title screen has no goggles button");
        expect(!gogglesOverlap(context), "the goggles button covers another button of the title screen");
        // The goggles are drawn once the loading overlay is gone; wait for it so the picture shows them.
        context.waitFor(mc -> mc.gui.overlay() == null);
        context.waitTicks(10);
        context.takeScreenshot("00-title-goggles");
        context.clickScreenButton("gravis_flying_config_menu.goggles");
        context.waitTicks(30);
        expect(context.computeOnClient(mc -> mc.gui.screen() instanceof CreateMenuScreen), "the goggles button did not open the Create menu");
        context.takeScreenshot("00b-create-menu");
        // Fabric's clickScreenButton fails on Create Fly's AbstractSimiScreen (IllegalAccessError inside the test API):
        // click it with the simulated mouse instead, which is also closer to what a player does.
        double[] pos = context.computeOnClient(mc -> {
            net.minecraft.client.gui.components.Button button = ((CreateMenuScreen) mc.gui.screen()).configureButton();
            double scale = mc.getWindow().getGuiScale();
            return new double[]{(button.getX() + button.getWidth() / 2.0) * scale, (button.getY() + button.getHeight() / 2.0) * scale};
        });
        context.getInput().setCursorPos(pos[0], pos[1]);
        context.getInput().pressMouse(0);
        context.waitTicks(10);
        expect(context.computeOnClient(mc -> mc.gui.screen() instanceof RootScreen), "Configure... did not open the config root screen");
        context.takeScreenshot("00c-root-from-menu");
        context.setScreen(() -> new TitleScreen());
        context.waitTicks(10);

        // Create Fly's own setting can move the button to row 3, which is "Mods | Minecraft Realms" with Mod Menu and only
        // "Minecraft Realms" without it: the button has to find its place either way.
        var client = com.zurrtum.create.client.infrastructure.config.AllConfigs.client();
        int row = client.mainMenuConfigButtonRow.get();
        try {
            client.mainMenuConfigButtonRow.set(3);
            context.setScreen(() -> new TitleScreen());
            context.waitTicks(5);
            String modMenu = FabricLoader.getInstance().isModLoaded("modmenu") ? "installed" : "not installed";
            expect(hasGoggles(context), "with the button in row 3 the title screen has no goggles button (Mod Menu " + modMenu + ")");
            expect(!gogglesOverlap(context), "with the button in row 3 the goggles cover another button (Mod Menu " + modMenu + ")");
            context.takeScreenshot("00d-title-goggles-row3");
        } finally {
            client.mainMenuConfigButtonRow.set(row);
        }
        context.setScreen(() -> new TitleScreen());
        context.waitTicks(5);
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

        ConfigComment comment = ConfigComment.parse(List.of("Hello", "[in Blocks]", "Default: 20", "Range: 5 ~ 9", "[@cui:RequiresRestart:both]", "[@cui:RequiresRelog] Rejoin", "[@cui:IntDisplay:#]"));
        expect("in Blocks".equals(comment.unit()), "the unit line was not read: " + comment.unit());
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
        // Diagnostic: the same screen with the cog drawn opaque, to check it is there and how big and tilted it is.
        context.runOnClient(mc -> CogShadow.setTint(0xFF_FFFFFF));
        context.waitTicks(3);
        context.takeScreenshot("02b-root-cog-visible");
        context.runOnClient(mc -> CogShadow.setTint(CogShadow.SHADOW_TINT));
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
        expect(!CogShadow.isDisabled(), "the background cog hit a drawing error and switched itself off (see latest.log)");
        int clientRows = context.computeOnClient(mc -> editor(context).list().children().size());
        expect(clientRows > 5, "the client page shows " + clientRows + " row(s): the single wrapping group was not flattened");

        context.runOnClient(mc -> mc.gui.setScreen(null));
        context.waitTicks(5);
    }

    private void modMenu(ClientGameTestContext context) {
        if (!FabricLoader.getInstance().isModLoaded("modmenu")) {
            log("Mod Menu not installed (optional): the goggles button is the way in, checked in the first step");
            return;
        }
        expect(ModMenu.hasConfigScreen("create"), "Mod Menu has no config button for Create");
        expect(ModMenu.hasConfigScreen(ConfigMenu.MOD_ID), "Mod Menu has no config button for our own entry");
        boolean root = context.computeOnClient(mc -> ModMenu.getConfigScreen("create", mc.gui.screen()) instanceof RootScreen);
        boolean list = context.computeOnClient(mc -> ModMenu.getConfigScreen(ConfigMenu.MOD_ID, mc.gui.screen()) instanceof ModListScreen);
        log("Mod Menu: config button for create -> " + root + ", for this mod -> " + list);
        expect(root, "Mod Menu's config screen for Create is not our root screen");
        expect(list, "Mod Menu's config screen for this mod is not our mod list");
    }

    // ---- addons: Dragons Plus and Connected, when the runner loaded them (-ConAddons) ------------------------------

    private void addons(ClientGameTestContext context) {
        for (String modId : List.of("create_dragons_plus", "create_connected")) {
            if (!FabricLoader.getInstance().isModLoaded(modId)) {
                log(modId + " not loaded (run with -ConAddons to include it)");
                continue;
            }
            List<ConfigRegistry.Entry> entries = ConfigRegistry.forMod(modId);
            expect(!entries.isEmpty(), modId + " is loaded but the mixin found none of its configs");
            for (ConfigRegistry.Entry entry : entries) {
                ConfigTrees.Tree tree = ConfigTrees.readForEditing(entry);
                Map<LeafNode.Kind, Integer> kinds = new EnumMap<>(LeafNode.Kind.class);
                count(tree.root(), kinds);
                log(modId + " / " + entry.type() + ": " + tree.bound() + " bound, " + tree.reachable() + " reached, kinds " + kinds);
                expect(tree.bound() > 0 && tree.reachable() == tree.bound(), modId + " " + entry.type() + " tree is incomplete: " + tree.reachable() + "/" + tree.bound());
            }
            context.setScreen(() -> new RootScreen(Minecraft.getInstance().gui.screen(), modId));
            context.waitTicks(10);
            context.takeScreenshot("30-" + modId + "-root");
            ConfigRegistry.Entry first = entries.stream().filter(e -> e.type().equals("server")).findFirst().orElse(entries.getFirst());
            context.setScreen(() -> new EditorScreen(Minecraft.getInstance().gui.screen(), first));
            context.waitTicks(10);
            context.takeScreenshot("31-" + modId + "-" + first.type());
        }
        context.runOnClient(mc -> mc.gui.setScreen(null));
        context.waitTicks(5);
    }

    // ---- gameplay: a value changed in the menu changes the game, and Create's advancements still come --------------

    private void gameplay(ClientGameTestContext context) throws IOException {
        ConfigRegistry.Entry serverEntry = create("server");
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            context.waitTicks(20);
            WorldState state = context.computeOnClient(mc -> WorldState.current());
            log("singleplayer world: world state " + state);
            expect(state == WorldState.SINGLEPLAYER, "in a singleplayer world the world state is " + state);

            TestServerContext server = world.getServer();
            ServerPlayer player = server.computeOnServer(s -> world.getConnection().getServerPlayer());   // server thread only
            // The first free block above the ground next to the player; the machines are built from there.
            BlockPos base = server.computeOnServer(s -> s.overworld().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, player.blockPosition()));
            // A glass block to stand on, a little back and up, so the screenshots show both machines from above.
            BlockPos camera = base.offset(-6, 3, 1);
            server.runOnServer(s -> s.overworld().setBlockAndUpdate(camera, Blocks.GLASS.defaultBlockState()));
            server.runCommand("tp @a " + (camera.getX() + 0.5) + " " + (camera.getY() + 1) + " " + (camera.getZ() + 0.5) + " -90 25");
            context.waitTicks(10);
            try {
                maxRotationSpeed(context, server, serverEntry, base);
                windmill(context, world, serverEntry, player, base);
            } finally {
                // Whatever happened, the next steps and the next run start from Create's defaults.
                ConfigTrees.Tree tree = ConfigTrees.readForEditing(serverEntry);
                find(tree.root(), "kinetics", "maxRotationSpeed").reset();
                find(tree.root(), "kinetics", "windmillSailsPerRPM").reset();
                context.runOnClient(mc -> mc.gui.setScreen(null));
            }
        }
        context.waitForScreen(TitleScreen.class);
        context.waitTicks(10);
    }

    /**
     * A creative motor turns a line of shafts at 128 RPM. Create breaks a block that a too-fast network reaches
     * ({@code RotationPropagator}: faster than {@code kinetics.maxRotationSpeed}), so with the default limit of 256 a shaft
     * added to the line turns, with the limit lowered to 64 IN THE MENU the next shaft breaks, and back at 256 it turns again.
     */
    private void maxRotationSpeed(ClientGameTestContext context, TestServerContext server, ConfigRegistry.Entry entry, BlockPos base) throws IOException {
        expect(AllConfigs.server().kinetics.maxRotationSpeed.get() == 256, "the maximum speed does not start at 256 but " + AllConfigs.server().kinetics.maxRotationSpeed.get());
        BlockPos motor = base.offset(4, 0, -5);
        server.runOnServer(s -> {
            ServerLevel level = s.overworld();
            level.setBlockAndUpdate(motor, AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST));
            for (int i = 1; i <= 3; i++) {
                level.setBlockAndUpdate(motor.east(i), shaft());
            }
        });
        context.waitTicks(10);
        server.runOnServer(s -> ((CreativeMotorBlockEntity) s.overworld().getBlockEntity(motor)).generatedSpeed.setValue(128));
        context.waitTicks(10);
        float lineSpeed = server.computeOnServer(s -> speedAt(s, motor.east(3)));
        log("motor line turns at " + lineSpeed + " RPM");
        expect(Math.abs(lineSpeed) == 128, "the shaft line does not turn at 128 RPM but " + lineSpeed);
        context.takeScreenshot("40-gameplay-motor-128rpm");

        expect(placeShaft(context, server, motor.east(4)), "with the limit at 256 a shaft added to the 128 RPM line broke");

        editThroughMenu(context, entry, "kinetics", "maxRotationSpeed", "64", "41-gameplay-menu-max-64");
        expect(AllConfigs.server().kinetics.maxRotationSpeed.get() == 64, "after saving 64 in the menu Create reads " + AllConfigs.server().kinetics.maxRotationSpeed.get());
        expect(optionOnDisk(entry, "kinetics", "maxRotationSpeed") == 64, "after saving 64 in the menu the file has " + optionOnDisk(entry, "kinetics", "maxRotationSpeed"));
        boolean survived = placeShaft(context, server, motor.east(5));
        log("limit 64: the shaft added to the 128 RPM line " + (survived ? "SURVIVED" : "broke, as Create does when a network is too fast"));
        expect(!survived, "with the limit lowered to 64 in the menu a shaft at 128 RPM did not break");
        context.takeScreenshot("42-gameplay-shaft-broke");

        editThroughMenu(context, entry, "kinetics", "maxRotationSpeed", "256", null);
        expect(AllConfigs.server().kinetics.maxRotationSpeed.get() == 256, "after saving 256 in the menu Create reads " + AllConfigs.server().kinetics.maxRotationSpeed.get());
        expect(placeShaft(context, server, motor.east(5)), "with the limit back at 256 the shaft broke again");
    }

    /**
     * A windmill placed by the player, as in the game: the bearing remembers who placed it, a right click with an empty hand
     * assembles it, and Create gives the "windmill" advancement (and "windmill_maxed" from 128 sails). The windmill's speed
     * is sails / {@code kinetics.windmillSailsPerRPM}, between 1 and 16: 169 sails give 16 with the default 8, and 5 after
     * setting 32 in the menu and assembling it again.
     */
    private void windmill(ClientGameTestContext context, TestSingleplayerContext world, ConfigRegistry.Entry entry, ServerPlayer player, BlockPos base) throws IOException {
        TestServerContext server = world.getServer();
        expect(!advancementDone(server, player, "windmill") && !advancementDone(server, player, "windmill_maxed"), "the new world already has the windmill advancements");
        BlockPos bearing = base.offset(14, 0, 3);
        server.runOnServer(s -> {
            ServerLevel level = s.overworld();
            BlockState state = AllBlocks.WINDMILL_BEARING.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP);
            level.setBlockAndUpdate(bearing, state);
            // What placing it by hand does after the block is set: Create notes the player, to give them the advancements.
            state.getBlock().setPlacedBy(level, bearing, state, player, new ItemStack(AllBlocks.WINDMILL_BEARING));
            BlockState sail = AllBlocks.SAIL_FRAME.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP);
            for (int dx = -6; dx <= 6; dx++) {
                for (int dz = -6; dz <= 6; dz++) {
                    level.setBlockAndUpdate(bearing.offset(dx, 1, dz), sail);
                }
            }
        });
        context.waitTicks(10);

        rightClick(server, player, bearing);
        context.waitTicks(20);
        int[] sails = new int[1];
        float speed = server.computeOnServer(s -> {
            WindmillBearingBlockEntity be = (WindmillBearingBlockEntity) s.overworld().getBlockEntity(bearing);
            if (be.getLastAssemblyException() != null) {
                log("windmill did not assemble: " + be.getLastAssemblyException().getMessage());
            }
            sails[0] = be.isRunning() && be.getMovedContraption() != null ? ((BearingContraption) be.getMovedContraption().getContraption()).getSailBlocks() : -1;
            return be.getSpeed();
        });
        log("windmill assembled with " + sails[0] + " sails, turns at " + speed + " RPM (sails per RPM " + AllConfigs.server().kinetics.windmillSailsPerRPM.get() + ")");
        expect(sails[0] == 169, "the windmill did not assemble with its 169 sails (" + sails[0] + ")");
        expect(Math.abs(speed) == 16, "a windmill of 169 sails at 8 sails per RPM turns at " + speed + " instead of 16");
        boolean windmillDone = advancementDone(server, player, "windmill");
        boolean maxedDone = advancementDone(server, player, "windmill_maxed");
        log("advancements: create:windmill " + (windmillDone ? "GIVEN" : "missing") + ", create:windmill_maxed " + (maxedDone ? "GIVEN" : "missing"));
        expect(windmillDone, "assembling the windmill did not give the create:windmill advancement");
        expect(maxedDone, "assembling 169 sails did not give the create:windmill_maxed advancement");
        context.takeScreenshot("43-gameplay-windmill-advancements");

        editThroughMenu(context, entry, "kinetics", "windmillSailsPerRPM", "32", "44-gameplay-menu-sails-32");
        expect(AllConfigs.server().kinetics.windmillSailsPerRPM.get() == 32, "after saving 32 in the menu Create reads " + AllConfigs.server().kinetics.windmillSailsPerRPM.get());
        float slower = reassemble(context, server, player, bearing);
        log("sails per RPM 32: the same windmill, assembled again, turns at " + slower + " RPM");
        expect(Math.abs(slower) == 5, "with 32 sails per RPM the windmill turns at " + slower + " instead of 5 (169 / 32)");

        editThroughMenu(context, entry, "kinetics", "windmillSailsPerRPM", "8", null);
        float back = reassemble(context, server, player, bearing);
        log("sails per RPM back to 8: " + back + " RPM");
        expect(Math.abs(back) == 16, "with 8 sails per RPM again the windmill turns at " + back + " instead of 16");
    }

    /** Types a value into one option of a file in the editor and saves, the way a player does it in the world. */
    private void editThroughMenu(ClientGameTestContext context, ConfigRegistry.Entry entry, String group, String option, String value, String screenshot) {
        context.setScreen(() -> new EditorScreen(Minecraft.getInstance().gui.screen(), entry));
        context.waitTicks(5);
        EditorScreen editor = editor(context);
        expect(editor != null && editor.isMutable(), "the " + entry.type() + " file is not editable in a singleplayer world");
        if (editor == null || !editor.isMutable()) {
            context.runOnClient(mc -> mc.gui.setScreen(null));
            return;
        }
        GroupNode page = (GroupNode) editor.currentGroup().children().stream().filter(n -> n.name().equals(group)).findFirst().orElseThrow();
        context.runOnClient(mc -> editor.open(page));
        context.waitTicks(3);
        EntryList.NumberRow row = context.computeOnClient(mc -> editor.list().children().stream()
            .filter(r -> r instanceof EntryList.NumberRow n && n.leaf().name().equals(option))
            .map(EntryList.NumberRow.class::cast).findFirst().orElse(null));
        expect(row != null, "no row for " + option + " on the " + group + " page");
        if (row == null) {
            context.runOnClient(mc -> mc.gui.setScreen(null));
            return;
        }
        context.runOnClient(mc -> {
            editor.list().setScrollAmount(Math.max(0, editor.list().children().indexOf(row) * 40 - 80));
            row.textField().setValue(value);
        });
        context.waitTicks(5);
        expect(editor.changes().size() == 1, "typing " + value + " for " + option + " staged " + editor.changes().size() + " change(s)");
        if (screenshot != null) {
            context.takeScreenshot(screenshot);
        }
        context.runOnClient(mc -> {
            editor.saveChanges();
            mc.gui.setScreen(null);
        });
        context.waitTicks(5);
    }

    private static BlockState shaft() {
        return AllBlocks.SHAFT.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X);
    }

    /** Puts a shaft at the end of the motor line; true if it is still there (and turning) a moment later. */
    private static boolean placeShaft(ClientGameTestContext context, TestServerContext server, BlockPos pos) {
        server.runOnServer(s -> s.overworld().setBlockAndUpdate(pos, shaft()));
        context.waitTicks(10);
        return server.computeOnServer(s -> s.overworld().getBlockState(pos).is(AllBlocks.SHAFT) && Math.abs(speedAt(s, pos)) == 128);
    }

    private static float speedAt(MinecraftServer server, BlockPos pos) {
        return server.overworld().getBlockEntity(pos) instanceof KineticBlockEntity kinetic ? kinetic.getSpeed() : Float.NaN;
    }

    /** A right click with an empty hand on the block, as the player does on a bearing to assemble or stop it. */
    private static void rightClick(TestServerContext server, ServerPlayer player, BlockPos pos) {
        server.runOnServer(s -> {
            ServerLevel level = s.overworld();
            level.getBlockState(pos).useItemOn(ItemStack.EMPTY, level, player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        });
    }

    /** Stops the windmill and assembles it again with two right clicks; returns its new speed. */
    private static float reassemble(ClientGameTestContext context, TestServerContext server, ServerPlayer player, BlockPos bearing) {
        rightClick(server, player, bearing);
        context.waitTicks(10);
        rightClick(server, player, bearing);
        context.waitTicks(20);
        return server.computeOnServer(s -> s.overworld().getBlockEntity(bearing) instanceof WindmillBearingBlockEntity be && be.isRunning() ? be.getSpeed() : Float.NaN);
    }

    private static boolean advancementDone(TestServerContext server, ServerPlayer player, String name) {
        return server.computeOnServer(s -> {
            AdvancementHolder advancement = s.getAdvancements().get(Identifier.fromNamespaceAndPath("create", name));
            return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
        });
    }

    private static int optionOnDisk(ConfigRegistry.Entry entry, String group, String option) throws IOException {
        return readFile(entry).getAsJsonObject(group).getAsJsonObject(option).get("value").getAsInt();
    }

    // ---- remote server: the server file must be read-only and the local file untouched ----------------------------

    private void remoteServer(ClientGameTestContext context) throws IOException {
        // A dedicated server only starts once the Minecraft EULA is accepted in the run folder. Accepting it is the
        // player's decision (Gravis edits eula.txt himself), so without it this step is skipped, not failed.
        java.nio.file.Path eula = FabricLoader.getInstance().getGameDir().resolve("eula.txt");
        if (!Files.exists(eula) || !Files.readString(eula, StandardCharsets.UTF_8).contains("eula=true")) {
            log("remote server SKIPPED: the Minecraft EULA is not accepted in " + eula.toAbsolutePath() + " (eula=true runs this step)");
            return;
        }
        ConfigRegistry.Entry serverEntry = create("server");
        Builder builder = serverEntry.builder();
        File file = new File(builder.configDir, builder.type + ".json");
        String before = Files.readString(file.toPath(), StandardCharsets.UTF_8);

        try (TestDedicatedServerContext server = context.worldBuilder().createServer()) {
            try (TestDedicatedServerConnection connection = server.connect()) {
                context.waitFor(mc -> mc.level != null && mc.player != null);
                context.waitTicks(20);
                WorldState state = context.computeOnClient(mc -> WorldState.current());
                log("connected to a dedicated server: world state " + state);
                expect(state == WorldState.REMOTE, "connected to a dedicated server but the world state is " + state);

                context.setScreen(() -> new RootScreen(Minecraft.getInstance().gui.screen(), "create"));
                context.waitTicks(10);
                context.takeScreenshot("20-remote-root");

                context.setScreen(() -> new EditorScreen(Minecraft.getInstance().gui.screen(), serverEntry));
                context.waitTicks(10);
                EditorScreen editor = editor(context);
                expect(editor != null && !editor.isMutable(), "the server file is editable while connected to a remote server");
                if (editor != null) {
                    GroupNode infrastructure = (GroupNode) editor.currentGroup().children().stream().filter(n -> n.name().equals("infrastructure")).findFirst().orElseThrow();
                    context.runOnClient(mc -> editor.open(infrastructure));
                    context.waitTicks(5);
                    EntryList.NumberRow number = context.computeOnClient(mc -> firstRow(editor, EntryList.NumberRow.class));
                    if (number != null) {
                        context.runOnClient(mc -> number.textField().setValue("44"));
                        context.waitTicks(3);
                    }
                    expect(editor.changes().isEmpty(), "a value was staged in the read-only server file: " + editor.changes().keySet());
                    context.takeScreenshot("21-remote-editor-locked");
                    context.runOnClient(mc -> editor.saveChanges());
                }

                // The goggles button is in the pause menu too.
                context.setScreen(() -> new PauseScreen(true));
                context.waitTicks(10);
                expect(hasGoggles(context), "the pause menu has no goggles button");
                expect(!gogglesOverlap(context), "the goggles button covers another button of the pause menu");
                context.takeScreenshot("22-pause-goggles");

                // The client's own (client) file stays editable on a remote server.
                context.setScreen(() -> new EditorScreen(Minecraft.getInstance().gui.screen(), create("client")));
                context.waitTicks(5);
                expect(editor(context) != null && editor(context).isMutable(), "the client file is read-only on a remote server");
                context.runOnClient(mc -> mc.gui.setScreen(null));
                context.waitTicks(5);
            }
        }
        context.waitForScreen(TitleScreen.class);
        context.waitTicks(10);

        String after = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        expect(before.equals(after), "the local server.json changed while connected to a remote server");

        // Back at the title screen: the server file is editable again and shows what is on disk.
        context.setScreen(() -> new EditorScreen(Minecraft.getInstance().gui.screen(), serverEntry));
        context.waitTicks(5);
        EditorScreen back = editor(context);
        expect(back != null && back.isMutable(), "the server file is not editable again after leaving the server");
        expect(Integer.valueOf(tickrateOnDisk(serverEntry)).equals(AllConfigs.server().tickrateSyncTimer.get()),
            "after leaving the server, memory and disk disagree on the tick rate");
        context.runOnClient(mc -> mc.gui.setScreen(null));
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
