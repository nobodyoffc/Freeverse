package startClient;

import apip.apipData.RequestBody;
import apip.apipData.Session;
import appTools.Inputer;
import appTools.Menu;
import appTools.Shower;
import clients.Client;
import clients.apipClient.ApipClient;
import clients.fcspClient.DiskClient;
import clients.fcspClient.DiskItem;
import configure.Configure;
import configure.ServiceType;
import constants.ApiNames;
import crypto.CryptoDataStr;
import crypto.Hash;
import fcData.FcReplierHttp;
import feip.feipData.serviceParams.ApipParams;
import javaTools.Hex;
import javaTools.JsonTools;
import javaTools.http.AuthType;
import javaTools.http.RequestMethod;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import static configure.Configure.saveConfig;
import static constants.ApiNames.Version1;

public class StartFcspClient {
    private static BufferedReader br;
    public static ApipClient apipClient;
    public static DiskClient diskClient;
    private static FcspClientSettings settings;
    private static byte[] symKey;


    public static final String MY_DATA_DIR = System.getProperty("user.home")+"/myData";

    public static void main(String[] args) {
        br = new BufferedReader(new InputStreamReader(System.in));
        Menu.welcome("DISK");

        //Load config info from the file of config.json
        Configure.loadConfig(br);
        Configure configure = Configure.checkPassword(br);
        symKey = configure.getSymKey();

        //Load the local settings from the file of localSettings.json
        String fid = configure.chooseMainFid(symKey);

        settings = FcspClientSettings.loadFromFile(fid, FcspClientSettings.class);//new ApipClientSettings(configure,br);
        if(settings==null) settings = new FcspClientSettings(configure);
        settings.initiateClient(fid,symKey, configure, br);

        apipClient = (ApipClient) settings.getApipAccount().getClient();
        diskClient = (DiskClient) settings.getDiskAccount().getClient();

        System.out.println("\n Menu");
        Menu menu = new Menu();
        menu.setName("Freeverse Commercial Services Clients");
        menu.add("DISK","TALK");
        while (true) {
            menu.show();
            int choice = menu.choose(br);
            switch (choice) {
                case 1 -> disk();
                case 2 -> talk();

                case 0 -> {
                    return;
                }
            }
        }
    }

    private static void talk() {
        System.out.println("Under coding...");
    }

    private static void disk() {
        Menu menu = new Menu();
        menu.setName("Disk Client");
        menu.add("getService","Ping free","Ping","Put","Get","Get by POST","Check","Check by POST","List","List by POST","Carve","SignIn","SignIn encrypted","Settings");
        while (true) {
            menu.show();
            int choice = menu.choose(br);
            switch (choice) {
                case 1 -> getService();
                case 2 -> pingFree(br);
                case 3 -> ping(br);
                case 4 -> put(br);
                case 5 -> get(RequestMethod.GET, AuthType.FREE, br);
                case 6 -> get(RequestMethod.POST,AuthType.FC_SIGN_BODY, br);
                case 7 -> checkFree(br);
                case 8 -> check(br);
                case 9 -> list(RequestMethod.GET, AuthType.FREE, br);
                case 10 -> list(RequestMethod.POST, AuthType.FC_SIGN_BODY, br);
                case 11 -> carve(br);
                case 12 -> signIn();
                case 13 -> signInEcc();
                case 14 -> settings.setting(symKey, br, null);
                case 0 -> {
                    return;
                }
            }
        }
    }

    private static void signInEcc() {
        Session session = diskClient.signInEcc(settings.getDiskAccount(), RequestBody.SignInMode.NORMAL, symKey);
        JsonTools.printJson(session);
        saveConfig();
        Menu.anyKeyToContinue(br);
    }

    private static void signIn() {
        Session session = diskClient.signIn(settings.getDiskAccount(), RequestBody.SignInMode.NORMAL,symKey);
        JsonTools.printJson(session);
        saveConfig();
        Menu.anyKeyToContinue(br);
    }

    public static void getService() {
        System.out.println("Getting the service information...");
        FcReplierHttp replier = DiskClient.getService(diskClient.getUrlHead(), Version1, ApipParams.class);
        if(replier!=null)JsonTools.printJson(replier);
        else System.out.println("Failed to get service.");
        Menu.anyKeyToContinue(br);
    }

    public static void pingFree(BufferedReader br){
        boolean done = (boolean) diskClient.ping(Version1, RequestMethod.GET,AuthType.FREE, ServiceType.DISK);
        if(done) System.out.println("OK!");
        else System.out.println("Failed!");
        Menu.anyKeyToContinue(br);
    }

    public static void ping(BufferedReader br){
        Object rest = diskClient.ping(Version1, RequestMethod.POST,AuthType.FC_SIGN_BODY, null);
        if(rest!=null) System.out.println("OK! "+rest+" KB/requests are available.");
        else System.out.println("Failed!");

        Menu.anyKeyToContinue(br);
    }

    public static void put(BufferedReader br){
        String fileName = getFileName(br);
        String dataResponse = diskClient.put(fileName);
        showPutResult(dataResponse, ApiNames.Put);
        Menu.anyKeyToContinue(br);
    }

    public static void carve(BufferedReader br){
        String fileName = getFileName(br);
        String dataResponse = diskClient.carve(fileName);
        showPutResult(dataResponse,ApiNames.Carve);
        Menu.anyKeyToContinue(br);
    }

    private static void showPutResult(String dataResponse, String apiName) {
        if(Hex.isHexString(dataResponse)) {
            System.out.println("Done to "+apiName+":" + dataResponse);
        }else System.out.println(dataResponse);
    }

    @Nullable
    private static String getFileName(BufferedReader br) {
        String fileName;
        while(true) {
            fileName = Inputer.inputPath(br, "Input the file path and name:");
            if (new File(fileName).isDirectory()) {
                System.out.println("It is a directory. A file name is required.");
                continue;
            }
            break;
        }
        if(Inputer.askIfYes(br,"Encrypt it?")) {
            try {
                String rawHash = Hash.sha256x2(new File(fileName));
                System.out.println("The DID is:"+rawHash);
            } catch (IOException e) {
                System.out.println("Failed to hash file:"+fileName);
            }
            fileName = Client.encryptFile(fileName, diskClient.getApiAccount().getUserPubKey());
            System.out.println("Encrypted to: "+fileName);
        }
        return fileName;
    }


    public static void get(RequestMethod method, AuthType authType, BufferedReader br){
        String filename = Inputer.inputString(br,"Input the DID of the file:");
        String path = Inputer.inputString(br,"Input the destination path. Default:"+MY_DATA_DIR);
        if("".equals(path))path = MY_DATA_DIR;
        String gotFileId = diskClient.get(method,authType,filename,path);
        System.out.println("Got:"+Path.of(path,gotFileId));
        if(!Hex.isHexString(gotFileId))return;
        tryToDecryptFile(path, gotFileId);
        Menu.anyKeyToContinue(br);
    }

    private static void tryToDecryptFile(String path, String gotFileId) {
        try {
            JsonTools.readOneJsonFromFile(path, gotFileId, CryptoDataStr.class);
            String did = Client.decryptFile(path, gotFileId,symKey, diskClient.getApiAccount().getUserPriKeyCipher());
            if(did!= null) System.out.println("Decrypted to:"+Path.of(path,did));
        } catch (IOException ignore) {}
    }

    public static void check(BufferedReader br){
        System.out.println("Check...");
        String did = Inputer.inputString(br,"Input the DID of the file:");
        String dataResponse = diskClient.check(did);
        System.out.println("Got:"+dataResponse);
        Menu.anyKeyToContinue(br);
    }

    public static void checkFree(BufferedReader br){
        System.out.println("Check...");
        String did = Inputer.inputString(br,"Input the DID of the file:");
        String dataResponse = diskClient.check(did);
        System.out.println("Got:"+dataResponse);
        Menu.anyKeyToContinue(br);
    }

    public static void list(RequestMethod method, AuthType authType, BufferedReader br){
        System.out.println("List...");
        String[] last = new String[0];
        String sort = null;
        String order = null;
        int size = 0;
        if(Inputer.askIfYes(br,"Set the last?")){
            last = Inputer.inputStringArray(br,"Set the last values of the sorted fields:",0);
        }
        if(Inputer.askIfYes(br,"Set the sort?")){
            sort = Inputer.inputString(br,"Set the field name of the sort:");
        }
        if(sort!=null && Inputer.askIfYes(br,"Set the order of the sort?")){
            do {
                order = Inputer.inputString(br, "Set the order, asc or desc:");
            } while (!order.equalsIgnoreCase("asc") && !order.equalsIgnoreCase("desc"));
        }
        if(Inputer.askIfYes(br,"Set the size?")){
            size = Inputer.inputInteger(br,"Set the size:",0);
        }

        List<DiskItem> dataResponse = diskClient.list(method,authType,size,sort,order,last);

        showDiskInfoList(dataResponse);

        Menu.anyKeyToContinue(br);
    }

    private static void showDiskInfoList(List<DiskItem> dataResponse) {
        String title = "Got disk items";
        String[] fields = new String[]{"did","since","expire","size"};
        int[] widths = new int[]{16,20,20,9};
        List<List<Object>> valueListList=new ArrayList<>();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        if(dataResponse==null||dataResponse.isEmpty()){
            System.out.println("Nothing to show.");
            return;
        }
        for(DiskItem diskItem : dataResponse){
            List<Object> valueList = new ArrayList<>();
            if(diskItem.getDid()!=null)valueList.add(diskItem.getDid());
            else valueList.add("");
            if(diskItem.getSince()!=null)valueList.add(formatter.format(diskItem.getSince()));
            else valueList.add("");
            if(diskItem.getExpire()!=null)valueList.add(formatter.format(diskItem.getExpire()));
            else valueList.add("");
            if(diskItem.getSize()!=null)valueList.add(String.valueOf(diskItem.getSize()));
            else valueList.add("");
            valueListList.add(valueList);
        }
        Shower.showDataTable(title,fields,widths,valueListList);
    }
}
