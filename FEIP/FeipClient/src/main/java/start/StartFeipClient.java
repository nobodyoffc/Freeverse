package start;

import core.fch.Inputer;
import ui.Menu;
import config.Settings;
import config.Starter;
import clients.FcClient;
import clients.FeipClient;
import clients.ApipClient;
import config.ApiAccount;
import config.Configure;
import core.crypto.Decryptor;
import data.fchData.SendTo;
import data.feipData.Feip.ProtocolName;
import data.feipData.Service;
import data.feipData.serviceParams.Params;
import data.feipData.Service.ServiceType;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static core.fch.Inputer.askIfYes;

public class StartFeipClient {

    public static Configure configure;
    public static Settings settings;
    public static ApiAccount apipAccount;
    public static ApipClient apipClient;
    public static BufferedReader br ;
    public static String clientName= Service.ServiceType.APIP.name();
    public static String myFid;
    public static String sid;
    public static String myPriKeyCipher;
    public static Map<String, Long> lastTimeMap;


    public static Map<String,Object> settingMap = new HashMap<>();

    public static void main(String[] args) {
        Menu.welcome(clientName);

        br = new BufferedReader(new InputStreamReader(System.in));

        List<data.fcData.Module> modules = new ArrayList<>();
        modules.add(new data.fcData.Module(Service.class.getSimpleName(),Service.ServiceType.APIP.name()));

        settings = Starter.startClient(clientName, settingMap, br, modules,null);
        if(settings==null)return;

        myFid = settings.getMainFid();

        byte[] symKey = settings.getSymkey();
        apipClient = (ApipClient) settings.getClient(Service.ServiceType.APIP);
        configure = settings.getConfig();
        apipAccount = settings.getApiAccount(ServiceType.APIP);

        myFid = apipAccount.getUserId();
        sid = apipClient.getApiProvider().getId();
        myPriKeyCipher = apipAccount.getUserPrikeyCipher();

        lastTimeMap = FcClient.loadLastTime(myFid,sid);
        if(lastTimeMap==null)lastTimeMap=new HashMap<>();

        byte[] priKey = Decryptor.decryptPrikey(myPriKeyCipher,symKey);

        System.out.println(myFid + " loaded.");

        // Create and show the FEIP protocols menu
        Menu feipMenu = new Menu("FEIP Creator");

        String watchFid;
        if(askIfYes(br, "Are you creating FEIP for a watch fid?"))
            watchFid = Inputer.inputString(br, "Please input the fid of the receiver:");
        else watchFid = null;


        List<SendTo> sendToList;
        System.out.println("Create new FEIP operation...");

        if(askIfYes(br,"Does this FEIP need one or more recipient?"))sendToList = SendTo.inputSendToList(br);
        else sendToList=null;

        // Add menu items for each protocol
        for (ProtocolName protocol : ProtocolName.values()) {
            feipMenu.add(protocol.getName(), () -> {
                try {
                    switch (protocol) {
                        case CID -> FeipClient.cid(priKey, watchFid, sendToList, null, apipClient, null, br);
                        case NOBODY -> FeipClient.nobody(priKey, watchFid, sendToList, null, apipClient, null, br);
                        case NID -> FeipClient.nid(priKey, watchFid, sendToList, null, apipClient, null, br);
                        case MASTER -> FeipClient.master(priKey, watchFid, sendToList, null, apipClient, null, br);
                        case HOMEPAGE -> FeipClient.homepage(priKey, watchFid, sendToList, null, apipClient, null, br);
                        case NOTICE_FEE -> FeipClient.noticeFee(priKey, watchFid, sendToList, null, apipClient, null, br);
                        case REPUTATION -> FeipClient.reputation(priKey, watchFid, sendToList, null, apipClient, null, br);
                       
                        case PROTOCOL -> FeipClient.protocol(priKey, watchFid, sendToList, null, apipClient, null, br);
                        case CODE -> FeipClient.code(priKey, watchFid, sendToList, null, apipClient, null, br);
                        case SERVICE -> FeipClient.service(priKey, watchFid, sendToList, null, Params.class, apipClient, null, br);
                        case APP -> FeipClient.app(priKey, watchFid, sendToList, null, apipClient, null, br);
 
                        case MAIL -> FeipClient.mail(priKey, watchFid, sendToList, null, apipClient, null, br);
                        case SECRET -> FeipClient.secret(priKey, watchFid, sendToList, null, apipClient, null, br);
                        case CONTACT -> FeipClient.contact(priKey, watchFid, sendToList, null, apipClient, null, br);
                        case BOX -> FeipClient.box(priKey, watchFid, sendToList, null, apipClient, null, br);
                        
                        case STATEMENT -> FeipClient.statement(priKey, watchFid, sendToList, null, apipClient, null, br);
                        case ESSAY -> FeipClient.essay(priKey, watchFid, sendToList, null, apipClient, null, br);
                        case REPORT -> FeipClient.report(priKey, watchFid, sendToList, null, apipClient, null, br);
                        case PAPER -> FeipClient.paper(priKey, watchFid, sendToList, null, apipClient, null, br);
                        case BOOK -> FeipClient.book(priKey, watchFid, sendToList, null, apipClient, null, br);
                        
                        case TEAM -> FeipClient.team(priKey, watchFid, sendToList, null, apipClient, null, br);
                        case GROUP -> FeipClient.group(priKey, watchFid, sendToList, null, null, apipClient, null, br);
                        
                        case PROOF -> FeipClient.proof(priKey, watchFid, sendToList, null, apipClient, null, br);
                        case TOKEN -> FeipClient.token(priKey, watchFid, sendToList, null, apipClient, null, br);
                    }
                } catch (Exception e) {
                    System.out.println("Error executing " + protocol.getName() + ": " + e.getMessage());
                    e.printStackTrace();
                }
            });
        }

        // Show the menu and handle selections
        feipMenu.showAndSelect(br);
    }
}
