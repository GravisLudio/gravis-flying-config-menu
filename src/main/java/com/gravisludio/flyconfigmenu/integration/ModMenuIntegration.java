package com.gravisludio.flyconfigmenu.integration;

import com.gravisludio.flyconfigmenu.config.ConfigRegistry;
import com.gravisludio.flyconfigmenu.screen.ConfigOverviewScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Optional Mod Menu integration: only Mod Menu ever loads this class (entrypoint {@code modmenu}).
 * Our own entry opens the overview of every config; each mod whose config we found gets a config button too.
 */
public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new ConfigOverviewScreen(parent, null);
    }

    @Override
    public Map<String, ConfigScreenFactory<?>> getProvidedConfigScreenFactories() {
        Map<String, ConfigScreenFactory<?>> factories = new LinkedHashMap<>();
        for (String modId : ConfigRegistry.modIds()) {
            factories.put(modId, parent -> new ConfigOverviewScreen(parent, modId));
        }
        return factories;
    }
}
