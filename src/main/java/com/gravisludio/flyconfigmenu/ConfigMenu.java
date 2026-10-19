package com.gravisludio.flyconfigmenu;

import com.mojang.logging.LogUtils;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

public final class ConfigMenu {
    public static final String MOD_ID = "create_fly_config_menu";
    public static final Logger LOGGER = LogUtils.getLogger();

    private ConfigMenu() {
    }

    /** The name Fabric knows a mod by (Create's own entry says "Create"), or the id when it is not a mod we can find. */
    public static String displayName(String modId) {
        return FabricLoader.getInstance().getModContainer(modId).map(container -> container.getMetadata().getName()).orElse(modId);
    }
}
