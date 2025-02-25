package app;

import appTools.Settings;

public class App {
    private Settings settings;

    public void close() {
        settings.close();
    }
}
