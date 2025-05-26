//package nettyClient;
//
//import appTools.Menu;
//import config.Settings;
//import config.Starter;
//import clients.ApipClient;
//import talkClient.ClientTalk;
//import feip.feipData.Service.ServiceType;
//
//import java.io.BufferedReader;
//import java.io.InputStreamReader;
//import java.util.HashMap;
//import java.util.Map;
//
//
//public class StartTalkClient{
//    private static BufferedReader br;
//    public static ApipClient apipClient;
//    public static ClientTalk clientTalk;
//    private static Settings settings;
//    public static String clientName= ServiceType.TALK.name();
//    public static String[] serviceAliases = new String[]{ServiceType.APIP.name(),ServiceType.TALK.name()};
//    public static Map<String,Object> settingMap = new HashMap<>();
//
//    public static void main(String[] args) {
//        br = new BufferedReader(new InputStreamReader(System.in));
//        Menu.welcome(clientName);
//
//        settings = Starter.startClient(clientName, serviceAliases, settingMap, br);
//        if(settings==null)return;
//
//        apipClient = (ApipClient) settings.getClient(ServiceType.APIP);//settings.getApipAccount().getClient();
//        clientTalk = (ClientTalk) settings.getClient(ServiceType.TALK);
//
//        try {
//            clientTalk.start(settings);
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }
//    }
//}
