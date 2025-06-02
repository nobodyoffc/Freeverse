package startApipClient;

import clients.FcClient;
import data.fcData.Module;
import ui.Menu;
import config.Settings;
import config.Starter;
import clients.ApipClient;
import data.feipData.Service;
import handlers.CashManager;
import handlers.ContactManager;
import config.ApiAccount;
import config.Configure;
import data.feipData.Service.ServiceType;
import handlers.Manager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;



public class startContactClient {

    public static final int DEFAULT_SIZE = 20;
    public static Configure configure;
    public static Settings settings;
    public static ApiAccount apipAccount;
    public static ApipClient apipClient;
    public static CashManager cashHandler;
    public static BufferedReader br ;
    public static String clientName= Manager.ManagerType.CONTACT.name();
    public static String myFid;
    public static String sid;
    public static byte[] symkey;
    public static String myPrikeyCipher;
    public static Map<String, Long> lastTimeMap;

    public static Map<String,Object> settingMap = new HashMap<>();

    public static void main(String[] args) {
        Menu.welcome(clientName);

        br = new BufferedReader(new InputStreamReader(System.in));

        List<Module> modules = new ArrayList<>();
        modules.add(new Module(Service.class.getSimpleName(), ServiceType.APIP.name()));
        modules.add(new Module(Manager.class.getSimpleName(), Manager.ManagerType.CASH.name()));

        settings = Starter.startClient(clientName, settingMap, br, modules, null);
        if(settings==null)return;
        apipClient = (ApipClient) settings.getClient(ServiceType.APIP);
        cashHandler = (CashManager)settings.getManager(Manager.ManagerType.CASH);
        configure = settings.getConfig();
        apipAccount = settings.getApiAccount(ServiceType.APIP);

        myFid = apipAccount.getUserId();
        sid = apipClient.getApiProvider().getId();
        myPrikeyCipher = apipAccount.getUserPrikeyCipher();

        lastTimeMap = FcClient.loadLastTime(myFid,sid);
        if(lastTimeMap==null)lastTimeMap=new HashMap<>();

        ContactManager contactHandler = new ContactManager(settings);

        contactHandler.freshOnChainContacts(br);

        contactHandler.menu(br, false);
    }
}
