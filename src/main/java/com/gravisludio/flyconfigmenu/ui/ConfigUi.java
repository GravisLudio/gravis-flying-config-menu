package com.gravisludio.flyconfigmenu.ui;

import com.zurrtum.create.catnip.theme.Color;
import com.zurrtum.create.client.catnip.gui.UIRenderHelper;

/**
 * Colors and small helpers shared by the screens. The look follows Create's original config menu (Catnip's config screens,
 * MIT, Copyright (c) 2022 The Create Team; see THIRD_PARTY_NOTICES.md), rebuilt on the GUI toolkit Create Fly ships.
 */
public final class ConfigUi {
    public static final Color TITLE_A = new Color(0xff_c69fbc).setImmutable();
    public static final Color TITLE_B = new Color(0xff_f6b8bb).setImmutable();
    public static final Color TITLE_C = new Color(0xff_fbf994).setImmutable();

    public static final int TEXT = rgb(UIRenderHelper.COLOR_TEXT.getFirst());
    public static final int TEXT_DARKER = rgb(UIRenderHelper.COLOR_TEXT_DARKER.getFirst());
    public static final int TEXT_ACCENT = rgb(UIRenderHelper.COLOR_TEXT_STRONG_ACCENT.getFirst());

    private ConfigUi() {
    }

    /** An opaque ARGB int for text and tints (the toolkit's colors carry their own alpha, which text must not inherit). */
    public static int rgb(Color color) {
        return 0xFF000000 | (color.getRGB() & 0x00FFFFFF);
    }

    /** {@code tickrateSyncTimer} becomes {@code Tickrate Sync Timer}; {@code enable_foo} becomes {@code Enable Foo}. */
    public static String humanize(String name) {
        String spaced = name.replace('_', ' ')
            .replaceAll("(?<=[a-z0-9])(?=[A-Z])", " ")
            .replaceAll("(?<=[A-Z])(?=[A-Z][a-z])", " ");
        StringBuilder out = new StringBuilder();
        for (String word : spaced.trim().split("\\s+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }
}
