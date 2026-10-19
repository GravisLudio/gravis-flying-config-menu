package com.gravisludio.flyconfigmenu.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The comment Create Fly's {@code Builder} stores next to every value, split into the text a player should read and the
 * UI annotations that travel inside it ({@code [@cui:RequiresRestart:both]}, {@code [@cui:RequiresRelog]},
 * {@code [@cui:IntDisplay:#]}, {@code [@cui:Execute:...]}; see Create Fly's {@code ConfigAnnotations}).
 *
 * @param lines   the text lines, without annotations and without the generated "Default / Range / Allowed Values" lines
 *                (the screen shows the real default and range itself)
 * @param restart {@code client}, {@code server} or {@code both} when changing the value needs a restart, else {@code null}
 * @param relog   whether the player has to rejoin the world for it to take effect
 * @param intDisplay {@code #}, {@code 0x} or {@code 0b} when an integer should be shown in hexadecimal or binary, else {@code null}
 */
public record ConfigComment(List<String> lines, String restart, boolean relog, String intDisplay) {
    public static final ConfigComment EMPTY = new ConfigComment(List.of(), null, false, null);

    private static final Pattern ANNOTATION = Pattern.compile("\\[@cui:(\\w+)(?::([^\\]]*))?]");

    /** Reads {@code "comment"} (a string, or an array of strings) from a value's or group's JSON object. */
    public static ConfigComment of(JsonObject object) {
        List<String> raw = new ArrayList<>();
        JsonElement element = object.get("comment");
        if (element != null) {
            if (element.isJsonArray()) {
                element.getAsJsonArray().forEach(e -> raw.add(e.getAsString()));
            } else if (element.isJsonPrimitive()) {
                raw.add(element.getAsString());
            }
        }
        return parse(raw);
    }

    public static ConfigComment parse(List<String> raw) {
        List<String> lines = new ArrayList<>();
        String restart = null;
        boolean relog = false;
        String intDisplay = null;
        for (String line : raw) {
            Matcher matcher = ANNOTATION.matcher(line);
            while (matcher.find()) {
                switch (matcher.group(1)) {
                    // RequiresRestart reports its name as "RequiresReload" (a quirk of Create Fly's enum): accept both.
                    case "RequiresRestart", "RequiresReload" -> restart = matcher.group(2) == null ? "both" : matcher.group(2);
                    case "RequiresRelog" -> relog = true;
                    case "IntDisplay" -> intDisplay = matcher.group(2);
                    default -> {
                    }
                }
            }
            String text = matcher.replaceAll("").strip();
            if (text.isEmpty() || isGenerated(text)) {
                continue;
            }
            lines.add(text);
        }
        return new ConfigComment(List.copyOf(lines), restart, relog, intDisplay);
    }

    private static boolean isGenerated(String line) {
        return line.startsWith("Default:") || line.startsWith("Range:") || line.startsWith("Allowed Values:");
    }

    public boolean matches(String lowerCaseQuery) {
        for (String line : lines) {
            if (line.toLowerCase().contains(lowerCaseQuery)) {
                return true;
            }
        }
        return false;
    }
}
