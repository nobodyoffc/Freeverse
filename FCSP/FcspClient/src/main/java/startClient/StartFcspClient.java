package startClient;

import apip.apipData.RequestBody;
import apip.apipData.Session;
import appTools.Inputer;
import appTools.Menu;
import appTools.Shower;
import clients.apipClient.ApipClient;
import clients.fcspClient.DiskClient;
import clients.fcspClient.DiskItem;
import configure.ApiAccount;
import configure.ServiceType;
import configure.Configure;
import constants.ApiNames;
import crypto.Hash;
import crypto.old.EccAes256K1P7;
import fcData.FcReplier;
import feip.feipData.serviceParams.ApipParams;
import javaTools.Hex;
import javaTools.JsonTools;
import javaTools.http.AuthType;
import javaTools.http.HttpRequestMethod;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import static constants.ApiNames.Version2;

public class StartFcspClient {
    public static final int DEFAULT_SIZE = 20;
    private static String fid;
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
        byte[] symKey = configure.getSymKey();

        //Load the local settings from the file of localSettings.json
        fid = configure.chooseMainFid(symKey);

        settings = FcspClientSettings.loadFromFile(fid, FcspClientSettings.class);//new ApipClientSettings(configure,br);
        if(settings==null) settings = new FcspClientSettings(configure);
        settings.initiateClient(fid,symKey, configure, br);

        apipClient = (ApipClient) settings.getApipAccount().getClient();
        diskClient = (DiskClient) settings.getDiskAccount().getClient();

        Menu menu = new Menu();
        menu.setName("Disk Client");
        menu.add("getService","Ping free","Ping","PUT","GET","GET by Post","CHECK","LIST","LIST post","Carve","SignIn","SignIn encrypted","Settings");
        while (true) {
            menu.show();
            int choice = menu.choose(br);
            switch (choice) {
                case 1 -> getService();
                case 2 -> pingFree(br);
                case 3 -> ping(br);
                case 4 -> put(br);
                case 5 -> get(HttpRequestMethod.GET, AuthType.FREE, br);
                case 6 -> get(HttpRequestMethod.POST,AuthType.FC_SIGN_BODY, br);
                case 7 -> check(br);
                case 8 -> list(HttpRequestMethod.GET, AuthType.FREE, br);
                case 9 -> list(HttpRequestMethod.POST, AuthType.FC_SIGN_BODY, br);
                case 10 -> carve(br);
                case 11 -> signIn(configure);
                case 12 -> signInEcc(configure);
                case 13 -> settings.setting(symKey, br, null);
                case 0 -> {
                    return;
                }
            }
        }
    }

    private static void signInEcc(Configure configure) {
        Session session = diskClient.signInEcc(settings.getDiskAccount(), RequestBody.SignInMode.NORMAL, symKey);
        JsonTools.printJson(session);
        configure.saveConfig();
        Menu.anyKeyToContinue(br);
    }

    private static void signIn(Configure configure) {
        Session session = diskClient.signIn(settings.getDiskAccount(), RequestBody.SignInMode.NORMAL,symKey);
        JsonTools.printJson(session);
        configure.saveConfig();
        Menu.anyKeyToContinue(br);
    }

    public static void getService() {
        System.out.println("Getting the service information...");
        FcReplier replier = DiskClient.getService(diskClient.getUrlHead(), Version2, ApipParams.class);
        if(replier!=null)JsonTools.printJson(replier);
        else System.out.println("Failed to get service.");
        Menu.anyKeyToContinue(br);
    }

    public static void pingFree(BufferedReader br){
        boolean done = (boolean) diskClient.ping(Version2,HttpRequestMethod.GET,AuthType.FREE, ServiceType.DISK);
        if(done) System.out.println("OK!");
        else System.out.println("Failed!");
        Menu.anyKeyToContinue(br);
    }

    public static void ping(BufferedReader br){
        Object rest = diskClient.ping(Version2,HttpRequestMethod.POST,AuthType.FC_SIGN_BODY, null);
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
            new File(dataResponse).delete();
            System.out.println(apiName+":" + dataResponse);
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
            fileName = DiskClient.encryptFile(fileName, diskClient.getApiAccount().getUserPubKey());
            System.out.println("Encrypted to: "+fileName);
        }
        return fileName;
    }

//    public static void putFree(BufferedReader br){
//        String fileName;
//        while(true) {
//            fileName = Inputer.inputPath(br, "Input the file path and name:");
//            if (new File(fileName).isDirectory()) {
//                System.out.println("It is a directory. A file name is required.");
//                continue;
//            }
//            break;
//        }
//        if(Inputer.askIfYes(br,"Encrypt it?")) {
//            fileName = DiskClient.encryptFile(fileName, diskClient.getApiAccount().getUserPubKey());
//            System.out.println("Encrypted to: "+fileName);
//        }
//        String dataResponse = diskClient.putFree(fileName);
//        if(Hex.isHexString(dataResponse)) {
//            if(!new File(dataResponse).delete()){
//                System.out.println("Failed to delete the local cipher file.");
//            };
//            System.out.println("Put: " + dataResponse);
//        }else System.out.println(dataResponse);
//        Menu.anyKeyToContinue(br);
//    }
//    public static void getFree(BufferedReader br){
//        String filename = Inputer.inputString(br,"Input the DID of the file");
//        String path = Inputer.inputString(br,"Input the destination path");
//        String gotFile = diskClient.getFree(filename,path);
//        System.out.println("Got:"+Path.of(path,gotFile));
//        if(!Hex.isHexString(gotFile))return;
//
//        String did = DiskClient.decryptFile(path, gotFile,symKey,diskClient.getApiAccount().getUserPriKeyCipher());
//        if(did!= null) System.out.println("Decrypted to:"+Path.of(path,did));
//        Menu.anyKeyToContinue(br);
//    }

    public static void get(HttpRequestMethod method, AuthType authType, BufferedReader br){
        String filename = Inputer.inputString(br,"Input the DID of the file:");
        String path = Inputer.inputString(br,"Input the destination path");
        String gotFile = diskClient.get(method,authType,filename,path);
        System.out.println("Got:"+Path.of(path,gotFile));
        if(!Hex.isHexString(gotFile))return;
        String did = DiskClient.decryptFile(path, gotFile,symKey, diskClient.getApiAccount().getUserPriKeyCipher());
        if(did!= null) System.out.println("Decrypted to:"+Path.of(path,did));
        Menu.anyKeyToContinue(br);
    }

//    public static void getPost(BufferedReader br){
//        String filename = Inputer.inputString(br,"Input the DID of the file:");
//        String path = Inputer.inputString(br,"Input the destination path");
//        String gotFile = diskClient.get(HttpRequestMethod.POST,filename,path );
//        System.out.println("Got:"+gotFile);
//        if(!Hex.isHexString(gotFile))return;
//        String did = DiskClient.decryptFile(path, gotFile,symKey,diskClient.getApiAccount().getUserPriKeyCipher());
//        if(did!= null) System.out.println("Decrypted to:"+Path.of(path,did));
//        Menu.anyKeyToContinue(br);
//    }
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

    public static void list(HttpRequestMethod method, AuthType authType, BufferedReader br){
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
            if(diskItem.getSince()!=null)valueList.add(formatter.format(diskItem.getSince()));
            if(diskItem.getExpire()!=null)valueList.add(formatter.format(diskItem.getExpire()));
            if(diskItem.getSize()!=null)valueList.add(String.valueOf(diskItem.getSize()));
            valueListList.add(valueList);
        }
        Shower.showDataTable(title,fields,widths,valueListList);
    }

    public static void setting(BufferedReader br){
        System.out.println("Setting...");
        Menu.anyKeyToContinue(br);
    }

    public static byte[] signInEccPost(byte[] symKey, RequestBody.SignInMode mode) {
        ApiAccount apiAccount = apipClient.getApiAccount();
        byte[] priKey = EccAes256K1P7.decryptJsonBytes(apiAccount.getUserPriKeyCipher(), symKey);
        if (priKey == null) return null;
        System.out.println("Sign in for the priKey encrypted sessionKey...");
        Session session = apipClient.signInEcc(apiAccount, RequestBody.SignInMode.NORMAL,symKey);
//        String sessionKeyCipherFromApip = session.getSessionKeyCipher();
//        byte[] newSessionKey = EccAes256K1P7.decryptWithPriKey(sessionKeyCipherFromApip, priKey);
//
//        updateSession(apiAccount,symKey, session, newSessionKey);
        return Hex.fromHex(session.getSessionKey());
    }
}
