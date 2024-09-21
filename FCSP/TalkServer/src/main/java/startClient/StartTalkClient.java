package startClient;

import apip.apipData.RequestBody;
import apip.apipData.Session;
import appTools.Menu;
import clients.apipClient.ApipClient;
import clients.fcspClient.TalkTcpClient;
import clients.fcspClient.TalkUdpClient;
import configure.Configure;
import javaTools.JsonTools;
import settings.TalkClientSettings;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import static configure.Configure.saveConfig;

public class StartTalkClient{
    private static BufferedReader br;
    public static ApipClient apipClient;
    public static TalkUdpClient talkUdpClient;
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
        String userPriKeyCipher = configure.getFidCipherMap().get(fid);

        settings = TalkClientSettings.loadFromFile(fid, TalkClientSettings.class);//new ApipClientSettings(configure,br);
        if(settings==null) settings = new TalkClientSettings(configure);
        settings.initiateClient(fid,symKey, configure, br);

        apipClient = (ApipClient) settings.getApipAccount().getClient();
        talkUdpClient = (TalkUdpClient) settings.getTalkAccount().getClient();


        talkUdpClient.start(settings,userPriKeyCipher);
    }


    private static void signInEcc() {
        Session session = talkUdpClient.signInEcc(settings.getTalkAccount(), RequestBody.SignInMode.NORMAL, symKey);
        JsonTools.printJson(session);
        saveConfig();
        Menu.anyKeyToContinue(br);
    }

}
