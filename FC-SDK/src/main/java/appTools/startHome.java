package appTools;

import clients.ApipClient;
import clients.CashClient;
import clients.Client;
import clients.ContactClient;
import clients.MailClient;
import clients.SecretClient;
import configure.ApiAccount;
import configure.Configure;
import configure.ServiceType;
import fcData.FidTxMask;
import tools.http.AuthType;
import tools.http.RequestMethod;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static appTools.Inputer.askIfYes;
import static constants.Constants.HOME;

public class startHome {
    public static String myFid;
    public static String myPubKey;
    public static String myPriKeyCipher;
    public static Configure configure;
    public static Settings settings;
    public static ApiAccount apipAccount;
    public static ApipClient apipClient;
    public static CashClient cashClient;
    public static SecretClient secretClient;
    public static ContactClient contactClient;
    public static MailClient mailClient;
    public static BufferedReader br ;
    public static String clientName= "FCH";
    public static Map<String, Long> lastTimeMap;
    public static String[] serviceAliases = new String[]{ServiceType.APIP.name()};

    public static Map<String,Object> settingMap = new HashMap<>();

   public static void main(String[] args) throws Exception {
       Menu.welcome(clientName);

       br = new BufferedReader(new InputStreamReader(System.in));

       settings = Starter.startClient(clientName, serviceAliases, settingMap, br);
       if(settings==null)return;
       configure = settings.getConfig();
       byte[] symKey = settings.getSymKey();
       apipAccount = settings.getApiAccount(ServiceType.APIP);
       apipClient = (ApipClient) settings.getClient(ServiceType.APIP);
       myFid = settings.getMainFid();
       myPubKey = settings.getMyPubKey();   
       myPriKeyCipher = settings.getMyPriKeyCipher();

       lastTimeMap = Client.loadLastTime(myFid, HOME);

       cashClient = new CashClient(myFid, myPriKeyCipher,symKey,apipClient,null,null,null,br);
       secretClient = new SecretClient(myFid, apipClient, cashClient, HOME, symKey, myPriKeyCipher, lastTimeMap, br);
       contactClient = new ContactClient(myFid, apipClient, cashClient, HOME, symKey, myPriKeyCipher, lastTimeMap, br);
       mailClient = new MailClient(myFid, apipClient, cashClient, HOME, symKey, myPriKeyCipher, lastTimeMap, br);

       Menu menu = new Menu("Home");

       ArrayList<String> menuItemList = new ArrayList<>();
       menuItemList.add("Send");
       menuItemList.add("Cash");
       menuItemList.add("TX");
       menuItemList.add("Sign & Verify");
       menuItemList.add("Encrypt & Decrypt");
       menuItemList.add("Key & Address");
       menuItemList.add("Hash");
       menuItemList.add("MultiSign");
       menuItemList.add("Secrets");
       menuItemList.add("Contacts");
       menuItemList.add("Mails");
       menuItemList.add("Watching FID");
       menuItemList.add("Settings");

       menu.add(menuItemList);

       while (true) {
           System.out.println(" << APIP Client>>");
           menu.show();
           int choice = menu.choose(br);
           switch (choice) {
               case 1 -> cashClient.sendTx(null);
               case 2 -> cashClient.start();
               case 3 -> handleTxMenu(symKey);
               case 4 -> HomeMethods.signAndVerify(myPriKeyCipher,symKey, br);
               case 5 -> HomeMethods.encryptAndDecrypt(myPriKeyCipher,symKey, br);
               case 6 -> HomeMethods.keyAndAddress(myPubKey,myPriKeyCipher,symKey, br);
               case 7 -> HomeMethods.hash(br);
               case 8 -> HomeMethods.multiSign(myPriKeyCipher,symKey,br);
               case 9 -> secretClient.menu();   
               case 10 -> contactClient.menu();
               case 11 -> mailClient.menu();
               case 12 -> switchAsWatchingFid(br);
               case 13 -> {
                   Client.saveLastTime(myFid, HOME, lastTimeMap);
                   settings.setting(symKey,br,null);
                   symKey = settings.getSymKey();
               }
               case 0 -> {
                   return;
               }
           }
       }
   }

   private static void handleTxMenu(byte[] symKey) throws Exception {
       Menu txMenu = new Menu("TX Operations");
       ArrayList<String> txMenuItems = new ArrayList<>();
       txMenuItems.add("List Tx");
       txMenuItems.add("Decode Tx");
       txMenuItems.add("Sign Raw Tx");
       txMenuItems.add("Broadcast Tx");
       txMenu.add(txMenuItems);

       while (true) {
           txMenu.show();
           int choice = txMenu.choose(br);
           switch (choice) {
               case 1 -> listTx();
               case 2 -> HomeMethods.decodeTxFch(br);
               case 3 -> HomeMethods.signRawTx(myPriKeyCipher,symKey, br);
               case 4 -> HomeMethods.broadcastTx(br);
               case 0 -> {
                    return;
                }
            }
        }
    }
               
    public static void listTx() {
        System.out.println("Requesting txSearch...");
        List<String> last=null;
        while(true){
            List<FidTxMask> result = apipClient.txByFid(myFid,20,last==null?null:last.toArray(new String[0]),RequestMethod.POST,AuthType.FC_SIGN_BODY);
            if(result==null)return;
            if(result.size()<20)break;
            last = apipClient.getFcClientEvent().getResponseBody().getLast();
            FidTxMask.showFidTxMaskList(result,"Your TXs",0);
            if(!askIfYes(br,"Continue?"))break;
        }
   }

   private static void switchAsWatchingFid(BufferedReader br2) {
        System.out.println("Current FID: "+myFid);
        if(myPriKeyCipher == null) {
            System.out.println("It is a watching FID.");
        }
        if(!myFid.equals(settings.getMainFid())&& askIfYes(br2, "Switch to main FID?")) {
            myFid = settings.getMainFid();
            myPubKey = settings.getMyPubKey();
            myPriKeyCipher = settings.getMyPriKeyCipher();
            System.out.println("Acting as main FID: "+myFid);
            Menu.anyKeyToContinue(br);
            return;
        }
        String watchingFid;
        if(askIfYes(br2, "Switch to a watching FID?")) {
            if(settings.getWatchFidPubKeyMap()==null || settings.getWatchFidPubKeyMap().isEmpty()){
                if(askIfYes(br,"No watching FID yet. Add it?"))
                    watchingFid = settings.addWatchingFids(br, apipClient,clientName);
                else return;
            }else{
                watchingFid = Inputer.chooseOneKeyFromMap(settings.getWatchFidPubKeyMap(), false, null, "Choose one to act as:", br2);
            }
            if(watchingFid == null) {
                if(askIfYes(br,"Add new watching FID?"))
                    watchingFid = settings.addWatchingFids(br, apipClient, clientName);
                else return;
            }
            myFid = watchingFid;
            myPubKey = settings.getWatchFidPubKeyMap().get(watchingFid);
            if(!myFid.equals(settings.getMainFid()))myPriKeyCipher=null;
            System.out.println("Acting as watching FID: "+myFid);
            Menu.anyKeyToContinue(br);
        }
   }
}
