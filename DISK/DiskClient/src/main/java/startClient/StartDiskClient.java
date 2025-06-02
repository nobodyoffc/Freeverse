package startClient;

import handlers.Manager;
import ui.Inputer;
import ui.Menu;
import ui.Shower;
import config.Settings;
import config.Starter;
import core.crypto.Decryptor;
import core.crypto.Encryptor;
import data.fcData.FcSession;
import data.apipData.RequestBody;
import clients.ApipClient;
import clients.DiskClient;
import data.fcData.DiskItem;
import server.ApipApiNames;
import core.crypto.CryptoDataStr;
import core.crypto.Hash;
import data.fcData.ReplyBody;
import data.feipData.Service;
import data.feipData.serviceParams.ApipParams;
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

import static config.Configure.saveConfig;
import static server.ApipApiNames.VERSION_1;

public class StartDiskClient {
    private static BufferedReader br;
    public static ApipClient apipClient;
    public static DiskClient diskClient;
    private static Settings settings;
    public static String clientName= Service.ServiceType.DISK.name();

//    public static Service.ServiceType[] serviceAliases = new Service.ServiceType[]{Service.ServiceType.APIP, Service.ServiceType.DISK};
    public static Map<String,Object> settingMap = new HashMap<>();

    public static final String MY_DATA_DIR = System.getProperty("user.home")+"/myData";

    public static void main(String[] args) {
        br = new BufferedReader(new InputStreamReader(System.in));
        Menu.welcome(clientName);

        List<data.fcData.Module> modules = new ArrayList<>();
        modules.add(new data.fcData.Module(Service.class.getSimpleName(),Service.ServiceType.APIP.name()));
        modules.add(new data.fcData.Module(Service.class.getSimpleName(),Service.ServiceType.DISK.name()));
        modules.add(new data.fcData.Module(Manager.class.getSimpleName(),Manager.ManagerType.HAT.name()));
        modules.add(new data.fcData.Module(Manager.class.getSimpleName(),Manager.ManagerType.DISK.name()));

        settings = Starter.startClient(clientName, settingMap, br, modules, null);
        if(settings==null)return;
        apipClient = (ApipClient) settings.getClient(Service.ServiceType.APIP);//settings.getApipAccount().getClient();
        diskClient = (DiskClient) settings.getClient(Service.ServiceType.DISK);//settings.getDiskAccount().getClient();
        byte[] symkey = settings.getSymkey();
        while(true) {
            try {
                disk(symkey);
                return;
            } catch (Exception e) {
                if(diskClient==null)
                    System.out.println("Please setup the DISK API.");
            }
        }
    }

    private static void disk(byte[] symkey) {
        Menu menu = new Menu();
        menu.setTitle("Disk Client");
        menu.add("getService","Ping free","Ping","Put","Get","Get by POST","Check","Check by POST","List","List by POST","Carve","SignIn","Settings");
        while (true) {
            menu.show();
            int choice = menu.choose(br);
            switch (choice) {
                case 1 -> getService();
                case 2 -> pingFree(br);
                case 3 -> ping(br);
                case 4 -> put(br);
                case 5 -> get(RequestMethod.GET, AuthType.FREE, br,symkey);
                case 6 -> get(RequestMethod.POST,AuthType.FC_SIGN_BODY, br,symkey);
                case 7 -> checkFree(br);
                case 8 -> check(br);
                case 9 -> list(RequestMethod.GET, AuthType.FREE, br);
                case 10 -> list(RequestMethod.POST, AuthType.FC_SIGN_BODY, br);
                case 11 -> carve(br);
//                case 12 -> signIn(symkey);
                case 12 -> signInEcc(symkey);
                case 13 -> settings.setting(br, null);
                case 0 -> {
                    return;
                }
            }
        }
    }

    private static void signInEcc(byte[] symkey) {
        FcSession fcSession = diskClient.signInEcc(settings.getApiAccount(Service.ServiceType.DISK), RequestBody.SignInMode.NORMAL, symkey, null);
        JsonUtils.printJson(fcSession);
        saveConfig();
        Menu.anyKeyToContinue(br);
    }

    private static void signIn(byte[] symkey) {
        FcSession fcSession = diskClient.signIn(settings.getApiAccount(Service.ServiceType.DISK), RequestBody.SignInMode.NORMAL,symkey);
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
        String fileName = Inputer.inputPath(br, "Input the file path and name, or directory path:");

        boolean isEncrypt = Inputer.askIfYes(br, "Encrypt the files?");

        File file = new File(fileName);
        
        if (file.isDirectory()) {
            System.out.println("Processing directory: " + fileName);
            putDirectory(file, isEncrypt);
        } else {
            putFile(fileName, isEncrypt);
        }
        Menu.anyKeyToContinue(br);
    }

    private static void putDirectory(File directory, boolean isEncrypt) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.getName().startsWith(".")) {
                    continue;
                }
                if (file.isDirectory()) {
                    putDirectory(file, isEncrypt);
                } else {
                    putFile(file.getAbsolutePath(), isEncrypt);
                }
            }
        }
    }

    private static void putFile(String fileName, boolean isEncrypt) {
        File file = new File(fileName);
        if(!file.exists()) {
            System.out.println("File does not exist: " + fileName);
            return;
        }
        if (isEncrypt) {
            try {
                String rawHash = Hash.sha256x2(file);
                System.out.println("The DID is:" + rawHash);
            } catch (IOException e) {
                System.out.println("Failed to hash file:" + fileName);
            }
            fileName = Encryptor.encryptFile(fileName, diskClient.getApiAccount().getUserPubkey());
            System.out.println("Encrypted to: " + fileName);
        }
        String dataResponse = diskClient.put(fileName);
        if(Hex.isHex32(dataResponse)){
            if(isEncrypt)System.out.println(file.getName()+" is encrypted and saved as:\n\t"+dataResponse);
            else System.out.println(file.getName()+" is saved as:\n\t"+dataResponse);
        }
        else System.out.println(dataResponse);
        System.out.println();
    }

    public static void carve(BufferedReader br){
        String fileName = Inputer.inputPath(br, "Input the file path and name, or directory path:");
        
        boolean isEncrypt = Inputer.askIfYes(br, "Encrypt the files?");

        File file = new File(fileName);
        
        if (file.isDirectory()) {
            System.out.println("Processing directory: " + fileName);
            carveDirectory(file, isEncrypt);
        } else {
            carveFile(fileName, isEncrypt);
        }
        Menu.anyKeyToContinue(br);
    }

    private static void carveDirectory(File directory, boolean isEncrypt) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                // Skip files and directories that start with '.'
                if (file.getName().startsWith(".")) {
                    continue;
                }
                if (file.isDirectory()) {
                    carveDirectory(file, isEncrypt);
                } else {
                    carveFile(file.getAbsolutePath(), isEncrypt);
                }
            }
        }
    }

    private static void carveFile(String fileName, boolean isEncrypt) {
        File file = new File(fileName);
        if(!file.exists()) {
            System.out.println("File does not exist: " + fileName);
            return;
        }
        if (isEncrypt) {
            try {
                String rawHash = Hash.sha256x2(file);
                System.out.println("The DID is:" + rawHash);
            } catch (IOException e) {
                System.out.println("Failed to hash file:" + fileName);
            }
            fileName = Encryptor.encryptFile(fileName, diskClient.getApiAccount().getUserPubkey());
            System.out.println("Encrypted to: " + fileName);
        }
        String dataResponse = diskClient.carve(fileName);
        if(Hex.isHex32(dataResponse)){
            if(isEncrypt)System.out.println(file.getName()+" is encrypted and carved as:\n\t"+dataResponse);
            else System.out.println(file.getName()+" is carved as:\n\t"+dataResponse);
        }
        else System.out.println(dataResponse);
        System.out.println();
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
            fileName = Encryptor.encryptFile(fileName, diskClient.getApiAccount().getUserPubkey());
            System.out.println("Encrypted to: "+fileName);
        }
        return fileName;
    }


    public static void get(RequestMethod method, AuthType authType, BufferedReader br,byte[] symkey){
        String filename = Inputer.inputString(br,"Input the DID of the file:");
        String path = Inputer.inputString(br,"Input the destination path. Default:"+MY_DATA_DIR);
        if("".equals(path))path = MY_DATA_DIR;
        String gotFileId = diskClient.get(method,authType,filename,path);
        if(gotFileId==null){
            System.out.println(diskClient.getFcClientEvent().getResponseBody().getMessage());
            return;
        }
        System.out.println("Got:"+Path.of(path,gotFileId));
        if(!Hex.isHexString(gotFileId))return;
        tryToDecryptFile(path, gotFileId,symkey);
        Menu.anyKeyToContinue(br);
    }

    private static void tryToDecryptFile(String path, String gotFileId,byte[] symkey) {
        try {
            CryptoDataStr cryptoDataStr = JsonUtils.readOneJsonFromFile(path, gotFileId, CryptoDataStr.class);
            if(cryptoDataStr==null)return;
            String did = Decryptor.decryptFile(path, gotFileId,symkey, diskClient.getApiAccount().getUserPrikeyCipher());
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
        Shower.showOrChooseList(title,fields,widths,valueListList, null);
    }
}
