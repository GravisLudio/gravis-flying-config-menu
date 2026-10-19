package com.gravisludio.flyconfigmenu.config;

import com.google.gson.JsonObject;
import com.zurrtum.create.catnip.config.Builder;
import com.zurrtum.create.catnip.config.ConfigBase;
import com.zurrtum.create.catnip.config.ConfigValue;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Create Fly's config classes keep what a UI needs in protected or private fields ({@code ConfigBase.allValues},
 * {@code ConfigBase.children}, {@code CValue.value}, {@code ConfigValue.object}, {@code Builder.stack}). They are read
 * here by reflection, in one place, so a Create Fly update that renames one of them breaks {@link #available()} instead
 * of crashing the game.
 */
final class ConfigReflection {
    private static final Field ALL_VALUES;
    private static final Field CHILDREN;
    private static final Field DEPTH;
    private static final Field CVALUE_VALUE;
    private static final Field CONFIG_VALUE_OBJECT;
    private static final Field BUILDER_STACK;
    private static final Throwable FAILURE;

    static {
        Field allValues = null, children = null, depth = null, cvalueValue = null, configValueObject = null, builderStack = null;
        Throwable failure = null;
        try {
            allValues = open(ConfigBase.class, "allValues");
            children = open(ConfigBase.class, "children");
            depth = open(ConfigBase.class, "depth");
            cvalueValue = open(ConfigBase.CValue.class, "value");
            configValueObject = open(ConfigValue.class, "object");
            builderStack = open(Builder.class, "stack");
        } catch (ReflectiveOperationException | RuntimeException e) {
            failure = e;
        }
        ALL_VALUES = allValues;
        CHILDREN = children;
        DEPTH = depth;
        CVALUE_VALUE = cvalueValue;
        CONFIG_VALUE_OBJECT = configValueObject;
        BUILDER_STACK = builderStack;
        FAILURE = failure;
    }

    private ConfigReflection() {
    }

    private static Field open(Class<?> owner, String name) throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    /** False when Create Fly's config classes no longer look the way this mod expects. */
    static boolean available() {
        return FAILURE == null;
    }

    static Throwable failure() {
        return FAILURE;
    }

    @SuppressWarnings("unchecked")
    static List<ConfigBase.CValue<?>> allValues(ConfigBase config) {
        try {
            return (List<ConfigBase.CValue<?>>) ALL_VALUES.get(config);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    static List<ConfigBase> children(ConfigBase config) {
        try {
            return (List<ConfigBase>) CHILDREN.get(config);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    static void resetDepth(ConfigBase config) {
        try {
            DEPTH.setInt(config, 0);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The bound value of a {@code CValue}, or {@code null} for the placeholder entries of nested configs and group markers. */
    static ConfigValue<?> boundValue(ConfigBase.CValue<?> cvalue) {
        try {
            return (ConfigValue<?>) CVALUE_VALUE.get(cvalue);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The JSON object that backs a value: the node that holds its {@code "value"} and {@code "comment"}. */
    static JsonObject jsonOf(ConfigValue<?> value) {
        try {
            return (JsonObject) CONFIG_VALUE_OBJECT.get(value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    static List<JsonObject> stackOf(Builder builder) {
        try {
            return (List<JsonObject>) BUILDER_STACK.get(builder);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }
}
