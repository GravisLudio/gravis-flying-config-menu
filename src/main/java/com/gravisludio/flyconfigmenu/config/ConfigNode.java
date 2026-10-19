package com.gravisludio.flyconfigmenu.config;

/** One line of the config tree: a group of options or a single option. */
public sealed interface ConfigNode permits GroupNode, LeafNode {
    String name();

    ConfigComment comment();
}
