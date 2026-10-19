package com.gravisludio.flyconfigmenu.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.zurrtum.create.catnip.config.Builder;
import com.zurrtum.create.catnip.config.ConfigBase;
import com.zurrtum.create.catnip.config.ConfigValue;

import java.io.File;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads a registered config into a tree of {@link GroupNode}/{@link LeafNode}, and keeps Create Fly's {@code Builder}
 * in a state where saving is safe.
 *
 * <p>The structure comes from the JSON tree that the {@code Builder} owns (groups are objects, options are objects
 * with a {@code "value"}); the typed {@code CValue}/{@code ConfigValue} instances are tied to it by the identity of the
 * JSON object each one wraps. That way the tree is exactly what lands in the file, whatever the {@code depth} bookkeeping
 * of {@code ConfigBase} did.
 *
 * <p><b>Why {@link #normalize} exists.</b> {@code Builder.save()} writes {@code builder.object} after popping its private
 * {@code stack}, and Create Fly's {@code CServer.reload} re-registers values without saving, so the builder can be left
 * with {@code object} inside a group and a stack of entries from earlier reads. In theory a second reload leaves a stale
 * root at the bottom of the stack and the next {@code CValue.set} would write old data. The 2026-10-19 client test did NOT
 * reproduce it (plain {@code CValue.set} after two reloads wrote the right value), so this is a cheap precaution, not a
 * confirmed fix: it finds the real root (the candidate whose JSON contains the live values), clears the stack and points
 * {@code object} at it. {@link #describeBuilder} shows the state for tests and logs.
 */
public final class ConfigTrees {
    private ConfigTrees() {
    }

    /**
     * @param root      the tree
     * @param bound     how many typed options the config has
     * @param reachable how many of them were found in the JSON tree (fewer than {@code bound} means something is out of sync)
     */
    public record Tree(GroupNode root, int bound, int reachable) {
    }

    /** Reads the tree without touching the builder (safe on a remote server, where nothing may be written). */
    public static Tree read(ConfigRegistry.Entry entry) {
        Map<JsonObject, ConfigBase.CValue<?>> bindings = new IdentityHashMap<>();
        collect(entry.root(), bindings);
        JsonObject root = findRoot(entry.builder(), bindings);
        int[] reachable = {0};
        GroupNode tree = group(entry.type(), root, bindings, reachable);
        // Create Fly's client file wraps everything in one group named like the file: show its options directly.
        if (tree.children().size() == 1 && tree.children().getFirst() instanceof GroupNode only && only.name().equals(entry.type())) {
            tree = new GroupNode(entry.type(), only.comment(), only.children());
        }
        return new Tree(tree, bindings.size(), reachable[0]);
    }

    /** Makes the builder safe to save with, then reads the tree. Use before any edit. */
    public static Tree readForEditing(ConfigRegistry.Entry entry) {
        normalize(entry);
        return read(entry);
    }

    /** Clears the builder's stack and points it at the real root. */
    public static void normalize(ConfigRegistry.Entry entry) {
        Map<JsonObject, ConfigBase.CValue<?>> bindings = new IdentityHashMap<>();
        collect(entry.root(), bindings);
        Builder builder = entry.builder();
        JsonObject root = findRoot(builder, bindings);
        ConfigReflection.stackOf(builder).clear();
        builder.object = root;
    }

    /**
     * Throws away what is in memory for this config and reads the file again, then re-binds every value (the same steps as
     * Create Fly's {@code CServer.reload(null)}, plus {@link #normalize}). Needed at the main menu, where the in-memory
     * {@code server} config may still be the one a remote server sent.
     */
    public static void rereadFromDisk(ConfigRegistry.Entry entry) {
        Builder builder = entry.builder();
        File file = new File(builder.configDir, builder.type + ".json");
        if (!file.isFile()) {
            return;
        }
        ConfigReflection.stackOf(builder).clear();
        builder.read();
        ConfigReflection.resetDepth(entry.root());
        entry.root().registerAll(builder);
        normalize(entry);
    }

    /** The builder's bookkeeping in one line, for tests and logs. */
    public static String describeBuilder(ConfigRegistry.Entry entry) {
        Map<JsonObject, ConfigBase.CValue<?>> bindings = new IdentityHashMap<>();
        collect(entry.root(), bindings);
        Builder builder = entry.builder();
        return "stack size " + ConfigReflection.stackOf(builder).size() + ", object holds " + countReachable(builder.object, bindings) + " of " + bindings.size() + " options";
    }

    private static void collect(ConfigBase config, Map<JsonObject, ConfigBase.CValue<?>> bindings) {
        for (ConfigBase.CValue<?> cvalue : ConfigReflection.allValues(config)) {
            ConfigValue<?> value = ConfigReflection.boundValue(cvalue);
            if (value != null) {
                bindings.put(ConfigReflection.jsonOf(value), cvalue);
            }
        }
        for (ConfigBase child : ConfigReflection.children(config)) {
            collect(child, bindings);
        }
    }

    /** The root is the candidate (builder.object or something on its stack) that contains the most live options. */
    private static JsonObject findRoot(Builder builder, Map<JsonObject, ConfigBase.CValue<?>> bindings) {
        List<JsonObject> candidates = new ArrayList<>(ConfigReflection.stackOf(builder));
        candidates.add(builder.object);
        JsonObject best = builder.object;
        int bestCount = -1;
        for (JsonObject candidate : candidates) {
            int count = countReachable(candidate, bindings);
            // ">=": among equals the later one (closer to the top of the stack) wins, which is the live root after a re-read.
            if (count >= bestCount && candidate != null) {
                best = candidate;
                bestCount = count;
            }
        }
        return best;
    }

    private static int countReachable(JsonObject object, Map<JsonObject, ConfigBase.CValue<?>> bindings) {
        if (object == null) {
            return -1;
        }
        int count = bindings.containsKey(object) ? 1 : 0;
        for (Map.Entry<String, JsonElement> member : object.entrySet()) {
            if (member.getValue() instanceof JsonObject child) {
                count += countReachable(child, bindings);
            }
        }
        return count;
    }

    private static GroupNode group(String name, JsonObject object, Map<JsonObject, ConfigBase.CValue<?>> bindings, int[] reachable) {
        List<ConfigNode> children = new ArrayList<>();
        for (Map.Entry<String, JsonElement> member : object.entrySet()) {
            if (!(member.getValue() instanceof JsonObject child)) {
                continue;
            }
            ConfigBase.CValue<?> cvalue = bindings.get(child);
            if (cvalue != null) {
                ConfigValue<?> value = ConfigReflection.boundValue(cvalue);
                children.add(new LeafNode(member.getKey(), ConfigComment.of(child), cvalue, value));
                reachable[0]++;
            } else if (!child.has("value")) {
                GroupNode sub = group(member.getKey(), child, bindings, reachable);
                if (!sub.children().isEmpty()) {
                    children.add(sub);
                }
            }
        }
        return new GroupNode(name, ConfigComment.of(object), List.copyOf(children));
    }
}
