package startApipClient;

import appTools.Menu;
import appTools.Settings;
import appTools.Starter;
import clients.Client;
import clients.apipClient.ApipClient;
import clients.contactClient.ContactClient;
import configure.ApiAccount;
import configure.Configure;
import configure.ServiceType;

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
    public static BufferedReader br ;
    public static String clientName= ServiceType.APIP.name();
    public static String[] serviceAliases = new String[]{ServiceType.APIP.name()};
    public static String myFid;
    public static String sid;
    public static byte[] symKey;
    public static String myPriKeyCipher;
    public static Map<String, Long> lastTimeMap;

    public static 	Map<String,Object> settingMap = new HashMap<>();

    static {
        settingMap.put(Settings.SHARE_API, false);
    }

    public static void main(String[] args) {
        Menu.welcome(clientName);

        br = new BufferedReader(new InputStreamReader(System.in));
        settings = Starter.startClient(clientName, serviceAliases, settingMap, br);
        if(settings==null)return;
        byte[] symKey = settings.getSymKey();
        apipClient = (ApipClient) settings.getClient(ServiceType.APIP);
        configure = settings.getConfig();
        apipAccount = settings.getApiAccount(ServiceType.APIP);

        myFid = apipAccount.getUserId();
        sid = apipClient.getApiProvider().getId();
        myPriKeyCipher = apipAccount.getUserPriKeyCipher();

        lastTimeMap = Client.loadLastTime(myFid,sid);
        if(lastTimeMap==null)lastTimeMap=new HashMap<>();

        ContactClient contactClient = new ContactClient(myFid, apipClient, sid, symKey, myPriKeyCipher, lastTimeMap);

        contactClient.checkContacts(br);

        contactClient.menu(br);
    }
}
