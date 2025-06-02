package app;

import handlers.ContactManager;
import handlers.Manager;
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

public class ContactApp {

    public static void main(String[] args) {
        Menu.welcome(ContactManager.name);

        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        Map<String,Object> settingsMap = new HashMap<>();
//        settingsMap.put(LISTEN_PATH,System.getProperty(UserHome)+"/fc_data/blocks");

        List<data.fcData.Module> modules = new ArrayList<>();
        modules.add(new data.fcData.Module(Service.class.getSimpleName(),Service.ServiceType.APIP.name()));
        modules.add(new data.fcData.Module(Manager.class.getSimpleName(),Manager.ManagerType.CASH.name()));
        modules.add(new data.fcData.Module(Manager.class.getSimpleName(),Manager.ManagerType.CONTACT.name()));

        while(true) {
            Settings settings = Starter.startClient(ContactManager.name, settingsMap, br, modules, null);

            if (settings == null) return;

            ContactManager contactHandler = (ContactManager)settings.getManager(Manager.ManagerType.CONTACT);

            contactHandler.menu(br, true);

            if (!Inputer.askIfYes(br, "Switch to another FID?")) System.exit(0);
        }
    }
} 