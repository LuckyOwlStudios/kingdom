package com.luckyowlstudios.kingdoms;

import com.luckyowlstudios.kingdoms.platform.Services;
import com.luckyowlstudios.kingdoms.registry.ModRegistry;

/**
 * Shared entry point. Each loader calls {@link #init()} from its own mod initializer.
 * Put loader-agnostic setup here.
 */
public class CommonClass {

    public static void init() {
        Constants.LOG.info("Loading {} on {} ({} environment)",
                Constants.MOD_NAME,
                Services.PLATFORM.getPlatformName(),
                Services.PLATFORM.getEnvironmentName());
        ModRegistry.init();
    }
}
