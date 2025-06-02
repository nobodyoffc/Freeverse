package app;

import handlers.AccountManager;
import handlers.Manager;
import ui.Inputer;
import ui.Menu;
import config.Settings;
import config.Starter;
import data.fcData.AutoTask;
import data.feipData.Service;
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


        Map<String,Object>  settingMap = new HashMap<> ();
        settingMap.put(Settings.LISTEN_PATH,System.getProperty(UserHome)+"/fc_data/blocks");
        settingMap.put(AccountManager.DISTRIBUTE_DAYS, AccountManager.DEFAULT_DISTRIBUTE_DAYS);
        settingMap.put(AccountManager.MIN_DISTRIBUTE_BALANCE, AccountManager.DEFAULT_MIN_DISTRIBUTE_BALANCE);
        settingMap.put(AccountManager.DEALER_MIN_BALANCE, AccountManager.DEFAULT_DEALER_MIN_BALANCE);

        List<AutoTask> autoTaskList = new ArrayList<>();
        autoTaskList.add(new AutoTask(Manager.ManagerType.ACCOUNT, "updateIncome", (String)settingMap.get(Settings.LISTEN_PATH)));
        autoTaskList.add(new AutoTask(Manager.ManagerType.ACCOUNT, "distribute", 10*SEC_PER_DAY));
        autoTaskList.add(new AutoTask(Manager.ManagerType.ACCOUNT, "saveMapsToLocalDB", 5));

        List<data.fcData.Module> modules = new ArrayList<>();
        modules.add(new data.fcData.Module(Service.class.getSimpleName(),Service.ServiceType.REDIS.name()));
        modules.add(new data.fcData.Module(Service.class.getSimpleName(),Service.ServiceType.NASA_RPC.name()));
        modules.add(new data.fcData.Module(Service.class.getSimpleName(),Service.ServiceType.ES.name()));
        modules.add(new data.fcData.Module(Manager.class.getSimpleName(),Manager.ManagerType.CASH.name()));
        modules.add(new data.fcData.Module(Manager.class.getSimpleName(),Manager.ManagerType.ACCOUNT.name()));


        while(true) {
            Settings settings = Starter.startServer(Service.ServiceType.APIP, settingMap, ApipApiNames.apiList,modules, br, autoTaskList);

            if (settings == null) return;

            AccountManager accountHandler = (AccountManager)settings.getManager(Manager.ManagerType.ACCOUNT);
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
