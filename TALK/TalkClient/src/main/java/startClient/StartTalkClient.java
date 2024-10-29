package startClient;

import appTools.Menu;
import appTools.Settings;
import appTools.Starter;
import clients.apipClient.ApipClient;
import clients.talkClient.TalkTcpClient;
import configure.ServiceType;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;


public class StartTalkClient{
    private static BufferedReader br;
    public static ApipClient apipClient;
    public static TalkTcpClient talkTcpClient;
    private static Settings settings;
    public static String clientName= ServiceType.TALK.name();
    public static String[] serviceAliases = new String[]{ServiceType.APIP.name(),ServiceType.TALK.name()};
    public static Map<String,Object> settingMap = new HashMap<>();
    static {
        settingMap.put(appTools.Settings.SHARE_API, false);
    }

    public static void main(String[] args) {
        br = new BufferedReader(new InputStreamReader(System.in));
        Menu.welcome(clientName);

        settings = Starter.startClient(clientName, serviceAliases, settingMap, br);
        if(settings==null)return;

        apipClient = (ApipClient) settings.getClient(ServiceType.APIP);//settings.getApipAccount().getClient();
        apipClient = (ApipClient) settings.getClient(ServiceType.APIP);
        talkTcpClient = (TalkTcpClient) settings.getClient(ServiceType.TALK);

        talkTcpClient.start(settings);
    }

}
