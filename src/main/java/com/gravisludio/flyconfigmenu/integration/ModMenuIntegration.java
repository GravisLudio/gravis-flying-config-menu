package com.gravisludio.flyconfigmenu.integration;

import com.gravisludio.flyconfigmenu.config.ConfigRegistry;
import com.gravisludio.flyconfigmenu.ui.ModListScreen;
import com.gravisludio.flyconfigmenu.ui.RootScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Optional Mod Menu integration: only Mod Menu ever loads this class (entrypoint {@code modmenu}).
 * Our own entry opens the list of every mod with a config; each of those mods gets a config button that opens its own root screen.
 */
public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new ModListScreen(parent);
    }

    @Override
    public Map<String, ConfigScreenFactory<?>> getProvidedConfigScreenFactories() {
        Map<String, ConfigScreenFactory<?>> factories = new LinkedHashMap<>();
        for (String modId : ConfigRegistry.modIds()) {
            factories.put(modId, parent -> new RootScreen(parent, modId));
        }
        return factories;
    }
}
