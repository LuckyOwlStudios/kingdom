package com.luckyowlstudios.kingdoms.platform;

import com.luckyowlstudios.kingdoms.Constants;
import com.luckyowlstudios.kingdoms.platform.services.IPlatformHelper;

import java.util.ServiceLoader;

/**
 * Loads loader-specific implementations of common interfaces via {@link ServiceLoader}.
 * Each loader registers its implementation in META-INF/services.
 */
public class Services {

    public static final IPlatformHelper PLATFORM = load(IPlatformHelper.class);

    public static <T> T load(Class<T> clazz) {
        final T loadedService = ServiceLoader.load(clazz)
                .findFirst()
                .orElseThrow(() -> new NullPointerException("Failed to load service for " + clazz.getName()));
        Constants.LOG.debug("Loaded {} for service {}", loadedService, clazz);
        return loadedService;
    }
}
