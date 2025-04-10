package app;

import appTools.Inputer;
import appTools.Menu;
import appTools.Settings;
import appTools.Starter;
import fcData.AutoTask;
import feip.feipData.Service;
import handlers.AccountHandler;
import handlers.Handler;
import server.ApipApiNames;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static constants.Constants.SEC_PER_DAY;
import static constants.Constants.UserHome;

public class AccountApp {

    public static void main(String[] args) {
        String name = "Account";
        Menu.welcome(name);

        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        Object[] modules = new Object[]{
                Service.ServiceType.REDIS,
                Service.ServiceType.NASA_RPC,
                Service.ServiceType.ES,
                Handler.HandlerType.CASH,
                Handler.HandlerType.ACCOUNT
        };

        Map<String,Object>  settingMap = new HashMap<> ();
        settingMap.put(Settings.LISTEN_PATH,System.getProperty(UserHome)+"/fc_data/blocks");
        settingMap.put(AccountHandler.DISTRIBUTE_DAYS,AccountHandler.DEFAULT_DISTRIBUTE_DAYS);
        settingMap.put(AccountHandler.MIN_DISTRIBUTE_BALANCE,AccountHandler.DEFAULT_MIN_DISTRIBUTE_BALANCE);
        settingMap.put(AccountHandler.DEALER_MIN_BALANCE,AccountHandler.DEFAULT_DEALER_MIN_BALANCE);

        List<AutoTask> autoTaskList = new ArrayList<>();
        autoTaskList.add(new AutoTask(Handler.HandlerType.ACCOUNT, "updateIncome", (String)settingMap.get(Settings.LISTEN_PATH)));
        autoTaskList.add(new AutoTask(Handler.HandlerType.ACCOUNT, "distribute", 10*SEC_PER_DAY));
        autoTaskList.add(new AutoTask(Handler.HandlerType.ACCOUNT, "saveMapsToLocalDB", 5));

        while(true) {
            Settings settings = Starter.startServer(Service.ServiceType.APIP, settingMap, ApipApiNames.apiList,modules, br, autoTaskList);

            if (settings == null) return;

            AccountHandler accountHandler = (AccountHandler)settings.getHandler(Handler.HandlerType.ACCOUNT);
            accountHandler.menu(br, true);
            if (!Inputer.askIfYes(br, "Switch to another FID relevant to "+settings.getMainFid()+"?")) break;
            if (!Inputer.askIfYes(br, "Switch to another FID?")) {
                try {
                    br.close();
                } catch (IOException ignore) {}
                System.exit(0);
            }
        }
    }
}
