package app;

import handlers.Manager;
import handlers.SecretManager;
import ui.Inputer;
import ui.Menu;
import config.Settings;
import config.Starter;
import data.feipData.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static constants.Constants.UserHome;
import static constants.Strings.LISTEN_PATH;

public class SecretApp {

    public static void main(String[] args) {

        Menu.welcome(SecretManager.name);

        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        Map<String,Object> settingsMap = new HashMap<>();
        settingsMap.put(LISTEN_PATH,System.getProperty(UserHome)+"/fc_data/blocks");

        List<data.fcData.Module> modules = new ArrayList<>();
        modules.add(new data.fcData.Module(Service.class.getSimpleName(),Service.ServiceType.APIP.name()));
        modules.add(new data.fcData.Module(Manager.class.getSimpleName(),Manager.ManagerType.CASH.name()));
        modules.add(new data.fcData.Module(Manager.class.getSimpleName(),Manager.ManagerType.SECRET.name()));

        while(true) {
            Settings settings = Starter.startClient(SecretManager.name, settingsMap, br, modules, null);

            if (settings == null) return;

            SecretManager secretHandler = new SecretManager(settings);
            secretHandler.freshOnChainSecrets(br);
            secretHandler.menu(br, true);
            if (!Inputer.askIfYes(br, "Switch to another FID relevant to "+settings.getMainFid()+"?")) break;
            if (!Inputer.askIfYes(br, "Switch to another FID?")) System.exit(0);
        }
    }
}
