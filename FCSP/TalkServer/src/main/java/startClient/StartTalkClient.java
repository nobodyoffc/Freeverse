package startClient;

import apip.apipData.RequestBody;
import apip.apipData.Session;
import appTools.Menu;
import clients.apipClient.ApipClient;
import clients.fcspClient.TalkClient;
import configure.Configure;
import javaTools.JsonTools;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import static configure.Configure.saveConfig;

public class StartTalkClient{
    private static BufferedReader br;
    public static ApipClient apipClient;
    public static TalkClient talkClient;
    private static TalkClientSettings settings;
    private static byte[] symKey;

    public static void main(String[] args) {
        br = new BufferedReader(new InputStreamReader(System.in));
        Menu.welcome("TALK");

        //Load config info from the file of config.json
        Configure.loadConfig(br);
        Configure configure = Configure.checkPassword(br);
        symKey = configure.getSymKey();

        //Load the local settings from the file of localSettings.json
        String fid = configure.chooseMainFid(symKey);

        settings = TalkClientSettings.loadFromFile(fid, TalkClientSettings.class);//new ApipClientSettings(configure,br);
        if(settings==null) settings = new TalkClientSettings(configure);
        settings.initiateClient(fid,symKey, configure, br);

        apipClient = (ApipClient) settings.getApipAccount().getClient();
        talkClient = (TalkClient) settings.getTalkAccount().getClient();
        Menu menu = new Menu();
        menu.setName("Talk Client");
        menu.add("start","signIn","Ping free","Ping","Put","Get","Get by POST","Check","Check by POST","List","List by POST","Carve","SignIn","SignIn encrypted","Settings");
        while (true) {
            menu.show();
            int choice = menu.choose(br);
            switch (choice) {
                case 1 -> talkClient.start();
//                case 2 -> sendText();
                case 14 -> settings.setting(symKey, br, null);
                case 0 -> {
                    return;
                }
            }
        }
    }

    private static void signInEcc() {
        Session session = talkClient.signInEcc(settings.getTalkAccount(), RequestBody.SignInMode.NORMAL, symKey);
        JsonTools.printJson(session);
        saveConfig();
        Menu.anyKeyToContinue(br);
    }

}
