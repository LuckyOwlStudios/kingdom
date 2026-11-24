package com.luckyowlstudios.kingdoms.platform.services;

public interface IPlatformHelper {

    /** @return the name of the current platform, e.g. "Fabric" or "NeoForge". */
    String getPlatformName();

    /** @return true if a mod with the given id is loaded. */
    boolean isModLoaded(String modId);

    /** @return true when running in a development environment. */
    boolean isDevelopmentEnvironment();

    default String getEnvironmentName() {
        return isDevelopmentEnvironment() ? "development" : "production";
    }
}
