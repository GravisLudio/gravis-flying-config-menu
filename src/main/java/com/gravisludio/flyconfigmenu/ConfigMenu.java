package com.gravisludio.flyconfigmenu;

import com.mojang.logging.LogUtils;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

public final class ConfigMenu {
    public static final String MOD_ID = "create_fly_config_menu";
    public static final Logger LOGGER = LogUtils.getLogger();

    private ConfigMenu() {
    }

    /**
     * The name Fabric knows a mod by, or the id with a capital letter when it is not a mod of its own (Create Fly builds
     * the configs of Flywheel, Ponder and Vanillin, which are bundled inside it).
     */
    public static String displayName(String modId) {
        return FabricLoader.getInstance().getModContainer(modId)
            .map(container -> container.getMetadata().getName())
            .orElseGet(() -> modId.isEmpty() ? modId : Character.toUpperCase(modId.charAt(0)) + modId.substring(1));
    }
}
