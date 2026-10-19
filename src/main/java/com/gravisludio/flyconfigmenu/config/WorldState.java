package com.gravisludio.flyconfigmenu.config;

import net.minecraft.client.Minecraft;

/** Where the player is, which decides whether the {@code server} config can be edited. */
public enum WorldState {
    /** Title screen or any menu outside a world: the files on disk are the truth. */
    NO_WORLD,
    /** Singleplayer or LAN host: the integrated server reads this very file. */
    SINGLEPLAYER,
    /** Connected to someone else's server: Create Fly replaced the in-memory {@code server} config with theirs. */
    REMOTE;

    public static WorldState current() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return NO_WORLD;
        }
        return minecraft.hasSingleplayerServer() ? SINGLEPLAYER : REMOTE;
    }

    /** The {@code server} config may only be written when it is the local one. */
    public boolean canEditServerConfig() {
        return this != REMOTE;
    }
}
