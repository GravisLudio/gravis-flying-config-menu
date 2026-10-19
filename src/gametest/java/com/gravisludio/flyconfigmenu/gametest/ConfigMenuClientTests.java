package com.gravisludio.flyconfigmenu.gametest;

import com.gravisludio.flyconfigmenu.config.ConfigRegistry;
import com.gravisludio.flyconfigmenu.screen.ConfigOverviewScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;

import java.util.ArrayList;
import java.util.List;

/**
 * Real-client test of the config menu on Minecraft 26.2 with the real Create Fly. Run with {@code gradlew runClientGameTest}
 * (Minecraft opens a window, stays on the title screen, and closes). Look for the {@code [CFCM-TEST]} markers: the checks
 * are collected and the test fails at the end, so one run reports everything that is wrong.
 */
public class ConfigMenuClientTests implements FabricClientGameTest {
    private final List<String> failures = new ArrayList<>();

    @Override
    public void runTest(ClientGameTestContext context) {
        context.waitForScreen(TitleScreen.class);
        context.waitTicks(20);

        // 1. The Builder.create mixin found the configs that Create Fly builds in its own initializers.
        List<ConfigRegistry.Entry> entries = ConfigRegistry.entries();
        for (ConfigRegistry.Entry entry : entries) {
            log("found " + entry.modId() + " / " + entry.type() + " -> " + entry.root().getClass().getName());
        }
        for (String type : List.of("common", "server", "client")) {
            expect(entries.stream().anyMatch(e -> e.modId().equals("create") && e.type().equals(type)),
                "Create Fly's " + type + " config was not found by the mixin");
        }

        // 2. The overview screen opens and draws.
        context.setScreen(() -> new ConfigOverviewScreen(Minecraft.getInstance().gui.screen(), null));
        context.waitTicks(10);
        context.takeScreenshot("overview");
        expect(context.computeOnClient(mc -> mc.gui.screen() instanceof ConfigOverviewScreen), "the overview screen did not open");

        context.runOnClient(mc -> mc.gui.screen().onClose());
        context.waitTicks(5);
        expect(context.computeOnClient(mc -> mc.gui.screen() instanceof TitleScreen), "closing the overview did not return to the title screen");

        if (failures.isEmpty()) {
            log("ALL OK");
        } else {
            failures.forEach(f -> log("FAIL: " + f));
            throw new AssertionError(failures.size() + " check(s) failed: " + failures);
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
