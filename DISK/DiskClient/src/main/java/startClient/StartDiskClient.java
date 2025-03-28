package startClient;

import crypto.Decryptor;
import crypto.Encryptor;
import fcData.FcSession;
import apip.apipData.RequestBody;
import appTools.*;
import clients.ApipClient;
import clients.DiskClient;
import fcData.DiskItem;
import handlers.Handler;
import server.ApipApiNames;
import crypto.CryptoDataStr;
import crypto.Hash;
import fcData.ReplyBody;
import feip.feipData.Service;
import feip.feipData.serviceParams.ApipParams;
import server.DiskApiNames;
import utils.Hex;
import utils.JsonUtils;
import utils.http.AuthType;
import utils.http.RequestMethod;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.*;

import static configure.Configure.saveConfig;
import static server.ApipApiNames.VERSION_1;

public class StartDiskClient {
    private static BufferedReader br;
    public static ApipClient apipClient;
    public static DiskClient diskClient;
    private static Settings settings;
    public static String clientName= Service.ServiceType.DISK.name();
    public static final Object[] modules = new Object[]{
            Service.ServiceType.APIP,
            Service.ServiceType.DISK,
            Handler.HandlerType.HAT,
            Handler.HandlerType.DISK
    };
//    public static Service.ServiceType[] serviceAliases = new Service.ServiceType[]{Service.ServiceType.APIP, Service.ServiceType.DISK};
    public static Map<String,Object> settingMap = new HashMap<>();

    public static final String MY_DATA_DIR = System.getProperty("user.home")+"/myData";
//    public static HandlerType[] requiredHandlers = new HandlerType[]{
//            HandlerType.HAT,
//            HandlerType.DISK
//    };
    public static void main(String[] args) {
        br = new BufferedReader(new InputStreamReader(System.in));
        Menu.welcome(clientName);

        settings = Starter.startClient(clientName, settingMap, br, modules);
        if(settings==null)return;
        apipClient = (ApipClient) settings.getClient(Service.ServiceType.APIP);//settings.getApipAccount().getClient();
        diskClient = (DiskClient) settings.getClient(Service.ServiceType.DISK);//settings.getDiskAccount().getClient();
        byte[] symKey = settings.getSymKey();

        disk(symKey);
    }

    private static void disk(byte[] symKey) {
        Menu menu = new Menu();
        menu.setTitle("Disk Client");
        menu.add("getService","Ping free","Ping","Put","Get","Get by POST","Check","Check by POST","List","List by POST","Carve","SignIn","SignIn encrypted","Settings");
        while (true) {
            menu.show();
            int choice = menu.choose(br);
            switch (choice) {
                case 1 -> getService();
                case 2 -> pingFree(br);
                case 3 -> ping(br);
                case 4 -> put(br);
                case 5 -> get(RequestMethod.GET, AuthType.FREE, br,symKey);
                case 6 -> get(RequestMethod.POST,AuthType.FC_SIGN_BODY, br,symKey);
                case 7 -> checkFree(br);
                case 8 -> check(br);
                case 9 -> list(RequestMethod.GET, AuthType.FREE, br);
                case 10 -> list(RequestMethod.POST, AuthType.FC_SIGN_BODY, br);
                case 11 -> carve(br);
                case 12 -> signIn(symKey);
                case 13 -> signInEcc(symKey);
                case 14 -> settings.setting(br, null);
                case 0 -> {
                    return;
                }
            }
        }
    }

    private static void signInEcc(byte[] symKey) {
        FcSession fcSession = diskClient.signInEcc(settings.getApiAccount(Service.ServiceType.DISK), RequestBody.SignInMode.NORMAL, symKey, null);
        JsonUtils.printJson(fcSession);
        saveConfig();
        Menu.anyKeyToContinue(br);
    }

    private static void signIn(byte[] symKey) {
        FcSession fcSession = diskClient.signIn(settings.getApiAccount(Service.ServiceType.DISK), RequestBody.SignInMode.NORMAL,symKey);
        JsonUtils.printJson(fcSession);
        saveConfig();
        Menu.anyKeyToContinue(br);
    }

    public static void getService() {
        System.out.println("Getting the service information...");
        ReplyBody replier = DiskClient.getService(diskClient.getUrlHead(), VERSION_1, ApipParams.class);
        if(replier!=null) JsonUtils.printJson(replier);
        else System.out.println("Failed to get service.");
        Menu.anyKeyToContinue(br);
    }

    public static void pingFree(BufferedReader br){
        boolean done = (boolean) diskClient.ping(VERSION_1, RequestMethod.GET,AuthType.FREE, Service.ServiceType.DISK);
        if(done) System.out.println("OK!");
        else System.out.println("Failed!");
        Menu.anyKeyToContinue(br);
    }

    public static void ping(BufferedReader br){
        Object rest = diskClient.ping(VERSION_1, RequestMethod.POST,AuthType.FC_SIGN_BODY, null);
        if(rest!=null) System.out.println("OK! "+rest+" KB/requests are available.");
        else System.out.println("Failed!");

        Menu.anyKeyToContinue(br);
    }

    public static void put(BufferedReader br){
        String fileName = getFileName(br);
        String dataResponse = diskClient.put(fileName);
        showPutResult(dataResponse, DiskApiNames.PUT);
        Menu.anyKeyToContinue(br);
    }

    public static void carve(BufferedReader br){
        String fileName = getFileName(br);
        String dataResponse = diskClient.carve(fileName);
        showPutResult(dataResponse, ApipApiNames.Carve);
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
            fileName = Encryptor.encryptFile(fileName, diskClient.getApiAccount().getUserPubKey());
            System.out.println("Encrypted to: "+fileName);
        }
        return fileName;
    }


    public static void get(RequestMethod method, AuthType authType, BufferedReader br,byte[] symKey){
        String filename = Inputer.inputString(br,"Input the DID of the file:");
        String path = Inputer.inputString(br,"Input the destination path. Default:"+MY_DATA_DIR);
        if("".equals(path))path = MY_DATA_DIR;
        String gotFileId = diskClient.get(method,authType,filename,path);
        System.out.println("Got:"+Path.of(path,gotFileId));
        if(!Hex.isHexString(gotFileId))return;
        tryToDecryptFile(path, gotFileId,symKey);
        Menu.anyKeyToContinue(br);
    }

    private static void tryToDecryptFile(String path, String gotFileId,byte[] symKey) {
        try {
            JsonUtils.readOneJsonFromFile(path, gotFileId, CryptoDataStr.class);
            String did = Decryptor.decryptFile(path, gotFileId,symKey, diskClient.getApiAccount().getUserPriKeyCipher());
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
            size = Inputer.inputInt(br,"Set the size:",0);
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
        Shower.showDataTable(title,fields,widths,valueListList, null);
    }
}
