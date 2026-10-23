package com.trevorschoeny.openanywhere;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Open Anywhere's config button in Mod Menu's list (and Mod Hub's, which reads the same entrypoint). */
public final class OpenAnywhereModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return SettingsScreen::create;
    }
}
