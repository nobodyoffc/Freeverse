package startApipClient;

import appTools.Menu;
import appTools.Settings;
import appTools.Starter;
import clients.Client;
import clients.ApipClient;
import feip.feipData.Service;
import handlers.CashHandler;
import handlers.ContactHandler;
import configure.ApiAccount;
import configure.Configure;
import feip.feipData.Service.ServiceType;
import handlers.Handler;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;



public class startContactClient {

    public static final int DEFAULT_SIZE = 20;
    public static Configure configure;
    public static Settings settings;
    public static ApiAccount apipAccount;
    public static ApipClient apipClient;
    public static CashHandler cashHandler;
    public static BufferedReader br ;
    public static String clientName= Handler.HandlerType.CONTACT.name();
    public static String myFid;
    public static String sid;
    public static byte[] symKey;
    public static String myPriKeyCipher;
    public static Map<String, Long> lastTimeMap;

    public static Map<String,Object> settingMap = new HashMap<>();
    public static final Object[] modules = new Object[]{
            Service.ServiceType.APIP,
            Handler.HandlerType.CASH
    };
    public static void main(String[] args) {
        Menu.welcome(clientName);

        br = new BufferedReader(new InputStreamReader(System.in));
        settings = Starter.startClient(clientName, settingMap, br, modules, null);
        if(settings==null)return;
        apipClient = (ApipClient) settings.getClient(ServiceType.APIP);
        cashHandler = (CashHandler)settings.getHandler(Handler.HandlerType.CASH);
        configure = settings.getConfig();
        apipAccount = settings.getApiAccount(ServiceType.APIP);

        myFid = apipAccount.getUserId();
        sid = apipClient.getApiProvider().getId();
        myPriKeyCipher = apipAccount.getUserPriKeyCipher();

        lastTimeMap = Client.loadLastTime(myFid,sid);
        if(lastTimeMap==null)lastTimeMap=new HashMap<>();

        ContactHandler contactHandler = new ContactHandler(settings);

        contactHandler.freshOnChainContacts(br);

        contactHandler.menu(br, false);
    }
}
