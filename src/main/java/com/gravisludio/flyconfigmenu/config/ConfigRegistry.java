package com.gravisludio.flyconfigmenu.config;

import com.zurrtum.create.catnip.config.Builder;
import com.zurrtum.create.catnip.config.ConfigBase;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Every config root that went through Create Fly's {@code Builder.create}. Filled by
 * {@link com.gravisludio.flyconfigmenu.mixin.BuilderCreateMixin}, so an addon that uses Create Fly's config
 * system shows up here without registering anything.
 */
public final class ConfigRegistry {
    private static final List<Entry> ENTRIES = new CopyOnWriteArrayList<>();

    private ConfigRegistry() {
    }

    /**
     * @param modId   the id the config was created with (the mod's own id, e.g. {@code create})
     * @param type    {@code client}, {@code common} or {@code server}
     * @param single  Builder's {@code single} flag: whether the file is {@code config/<id>-<type>.json} instead of {@code config/<id>/<type>.json}
     * @param root    the config object itself
     * @param builder the builder that wrote it; owns the JSON tree and the file
     */
    public record Entry(String modId, String type, boolean single, ConfigBase root, Builder builder) {
    }

    /** A second {@code create} for the same id and type replaces the first (Dragons Plus builds its server config twice). */
    public static void register(Entry entry) {
        ENTRIES.removeIf(e -> e.modId().equals(entry.modId()) && e.type().equals(entry.type()));
        ENTRIES.add(entry);
    }

    public static List<Entry> entries() {
        return List.copyOf(ENTRIES);
    }

    public static List<String> modIds() {
        return ENTRIES.stream().map(Entry::modId).distinct().sorted().toList();
    }

    public static List<Entry> forMod(String modId) {
        return ENTRIES.stream().filter(e -> e.modId().equals(modId)).toList();
    }

    /** For tests. */
    public static void clear() {
        ENTRIES.clear();
    }
}
