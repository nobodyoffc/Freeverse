package app;

import appTools.Inputer;
import appTools.Menu;
import appTools.Settings;
import appTools.Starter;
import handlers.SecretHandler;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import static constants.Constants.UserHome;
import static constants.Strings.LISTEN_PATH;

public class SecretApp {

    public static void main(String[] args) {

        Menu.welcome(SecretHandler.name);

        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        Map<String,Object> settingsMap = new HashMap<>();
        settingsMap.put(LISTEN_PATH,System.getProperty(UserHome)+"/fc_data/blocks");

        while(true) {
            Settings settings = Starter.startClient(SecretHandler.name, settingsMap, br, SecretHandler.modules);

            if (settings == null) return;

            SecretHandler secretHandler = new SecretHandler(settings);
            secretHandler.freshOnChainSecrets(br);
            secretHandler.menu(br, true);
            if (!Inputer.askIfYes(br, "Switch to another FID relevant to "+settings.getMainFid()+"?")) break;
            if (!Inputer.askIfYes(br, "Switch to another FID?")) System.exit(0);
        }
    }
}
