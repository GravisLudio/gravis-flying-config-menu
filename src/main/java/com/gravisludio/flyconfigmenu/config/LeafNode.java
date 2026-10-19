package com.gravisludio.flyconfigmenu.config;

import com.google.gson.JsonObject;
import com.zurrtum.create.catnip.config.BooleanValue;
import com.zurrtum.create.catnip.config.ConfigBase;
import com.zurrtum.create.catnip.config.ConfigValue;
import com.zurrtum.create.catnip.config.EnumValue;
import com.zurrtum.create.catnip.config.FloatValue;
import com.zurrtum.create.catnip.config.IntValue;
import com.zurrtum.create.catnip.config.StringValue;

import java.util.Objects;

/** One option. Reads and writes go through Create Fly's own {@code CValue}, so the JSON file is updated the way Create does it. */
public final class LeafNode implements ConfigNode {
    public enum Kind { BOOLEAN, INT, FLOAT, STRING, ENUM, UNSUPPORTED }

    private final String name;
    private final ConfigComment comment;
    private final ConfigBase.CValue<Object> cvalue;
    private final Kind kind;

    @SuppressWarnings("unchecked")
    LeafNode(String name, ConfigComment comment, ConfigBase.CValue<?> cvalue, ConfigValue<?> value) {
        this.name = name;
        this.comment = comment;
        this.cvalue = (ConfigBase.CValue<Object>) cvalue;
        this.kind = kindOf(value);
    }

    /** The live value object: Create Fly replaces it (not the CValue) whenever it re-registers a config. */
    @SuppressWarnings("unchecked")
    private ConfigValue<Object> value() {
        return (ConfigValue<Object>) ConfigReflection.boundValue(cvalue);
    }

    private static Kind kindOf(ConfigValue<?> value) {
        if (value instanceof BooleanValue) {
            return Kind.BOOLEAN;
        } else if (value instanceof IntValue) {
            return Kind.INT;
        } else if (value instanceof FloatValue) {
            return Kind.FLOAT;
        } else if (value instanceof StringValue) {
            return Kind.STRING;
        } else if (value instanceof EnumValue<?>) {
            return Kind.ENUM;
        }
        return Kind.UNSUPPORTED;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public ConfigComment comment() {
        return comment;
    }

    public Kind kind() {
        return kind;
    }

    public Object get() {
        return cvalue.get();
    }

    public Object defaultValue() {
        return value().getDefault();
    }

    public boolean isDefault() {
        return Objects.equals(get(), defaultValue());
    }

    /** Smallest allowed value of an INT or FLOAT option. */
    public double min() {
        Object raw = value();
        return raw instanceof IntValue i ? i.min : raw instanceof FloatValue f ? f.min : Double.NEGATIVE_INFINITY;
    }

    /** Largest allowed value of an INT or FLOAT option. */
    public double max() {
        Object raw = value();
        return raw instanceof IntValue i ? i.max : raw instanceof FloatValue f ? f.max : Double.POSITIVE_INFINITY;
    }

    public Object[] enumConstants() {
        return ((Enum<?>) value().getDefault()).getDeclaringClass().getEnumConstants();
    }

    /** Whether the number is inside the range Create Fly declared for it. */
    public boolean inRange(Number number) {
        double v = number.doubleValue();
        return v >= min() && v <= max();
    }

    /** Sets the value and writes the JSON file now (this is what Create's own {@code CValue.set} does). */
    public void set(Object newValue) {
        if (newValue instanceof Number number && (kind == Kind.INT || kind == Kind.FLOAT) && !inRange(number)) {
            throw new IllegalArgumentException(name + ": " + newValue + " is outside " + min() + " ~ " + max());
        }
        cvalue.set(newValue);
    }

    public void reset() {
        set(defaultValue());
    }

    /** The JSON object behind this option; for tests that want to compare against what is on disk. */
    public JsonObject json() {
        return ConfigReflection.jsonOf(value());
    }
}
