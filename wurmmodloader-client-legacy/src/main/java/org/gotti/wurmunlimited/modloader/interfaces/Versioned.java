package org.gotti.wurmunlimited.modloader.interfaces;

/**
 * Legacy interface from Ago's WurmClientModLauncher.
 * Provides version information for mods.
 *
 * @since 0.1.0 (legacy compatibility)
 */
public interface Versioned {

    /**
     * Get the version of the mod.
     * The default implementation tries to read the implementation version from the jar file.
     *
     * @return version. May be null
     */
    default String getVersion() {
        String version = this.getClass().getPackage().getImplementationVersion();
        if (version != null && !version.isEmpty()) {
            return version;
        }
        return null;
    }
}
