package com.gravisludio.flyconfigmenu.config;

import java.util.List;

/** A group of options (a JSON object without a {@code "value"}): Create's {@code infrastructure}, {@code kinetics}, ... */
public record GroupNode(String name, ConfigComment comment, List<ConfigNode> children) implements ConfigNode {
    /** How many options live in this group and below it. */
    public int leafCount() {
        int count = 0;
        for (ConfigNode child : children) {
            count += child instanceof GroupNode group ? group.leafCount() : 1;
        }
        return count;
    }
}
