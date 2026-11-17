package fapi;

import config.Settings;

/** Test access to ServiceBootstrap's package-private start checks. */
public final class ServiceBootstrapAccess {
    private ServiceBootstrapAccess() {}

    public static String checkImportedChainHeight(Settings settings, String sid) {
        return ServiceBootstrap.checkImportedChainHeight(settings, sid);
    }
}
