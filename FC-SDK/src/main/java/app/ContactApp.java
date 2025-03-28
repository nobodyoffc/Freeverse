package app;

import appTools.Inputer;
import appTools.Menu;
import appTools.Settings;
import appTools.Starter;
import handlers.ContactHandler;
import handlers.Handler;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import static constants.Constants.UserHome;
import static constants.Strings.LISTEN_PATH;

public class ContactApp {

    public static void main(String[] args) {
        Menu.welcome(ContactHandler.name);

        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        Map<String,Object> settingsMap = new HashMap<>();
//        settingsMap.put(LISTEN_PATH,System.getProperty(UserHome)+"/fc_data/blocks");

        while(true) {
            Settings settings = Starter.startClient(ContactHandler.name, settingsMap, br, ContactHandler.modules);

            if (settings == null) return;

            ContactHandler contactHandler = (ContactHandler)settings.getHandler(Handler.HandlerType.CONTACT);
            contactHandler.freshOnChainContacts(br);
            contactHandler.menu(br, true);

            if (!Inputer.askIfYes(br, "Switch to another FID?")) System.exit(0);
        }
    }
} 