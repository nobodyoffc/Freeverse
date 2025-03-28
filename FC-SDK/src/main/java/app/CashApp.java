package app;

import appTools.Inputer;
import appTools.Menu;
import appTools.Settings;
import appTools.Starter;
import feip.feipData.Service;
import handlers.CashHandler;
import handlers.Handler;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class CashApp extends FcApp{

    public static void main(String[] args) {

        Menu.welcome(CashHandler.name);

        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        Map<String,Object> settingsMap = new HashMap<>();
//        settingsMap.put(LISTEN_PATH,System.getProperty(UserHome)+"/fc_data/blocks");
        Object[] modules = new Object[]{
                Service.ServiceType.APIP,
                Handler.HandlerType.CASH
//                Service.ServiceType.ES,
//                Service.ServiceType.NASA_RPC
        };

        while(true) {
            Settings settings = Starter.startClient(CashHandler.name, settingsMap, br, modules);

            if (settings == null) return;

            CashHandler cashHandler = (CashHandler)settings.getHandler(Handler.HandlerType.CASH);
            cashHandler.freshCashDB();
            cashHandler.menu();

            if (!Inputer.askIfYes(br, "Switch to another main FID?"))
                System.exit(0);
        }
    }
}
