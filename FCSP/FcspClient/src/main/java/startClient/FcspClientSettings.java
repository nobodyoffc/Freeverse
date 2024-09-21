package startClient;

import appTools.Menu;
import clients.apipClient.ApipClient;
import configure.ApiAccount;
import configure.ServiceType;
import feip.feipData.Service;
import redis.clients.jedis.JedisPool;
import configure.Configure;
import settings.Settings;

import java.io.BufferedReader;

import static configure.Configure.saveConfig;

public class FcspClientSettings extends Settings {
    String diskAccountId;
    private transient ApiAccount diskAccount;

    public FcspClientSettings(Configure configure) {
        super(configure);
    }

    @Override
    public Service initiateServer(String sid, byte[] symKey, Configure config, BufferedReader br) {
        return null;
    }

    public String initiateClient(String fid, byte[] symKey, Configure config, BufferedReader br) {
        System.out.println("Initiating APP settings...\n");
        setInitForClient(fid, config, br);

        if(shareApiAccount==null)inputShareApiAccount(br);

        apipAccount = config.checkAPI(apipAccountId, mainFid, ServiceType.APIP,symKey, shareApiAccount);//checkApiAccount(apipAccountId, ApiType.APIP, config, symKey, null);
        if(shareApiAccount)checkIfMainFidIsApiAccountUser(symKey,config,br,apipAccount, mainFid);
        if(apipAccount.getClient()!=null)apipAccountId=apipAccount.getId();
        else System.out.println("No APIP service.");

        diskAccount = config.checkAPI(diskAccountId, mainFid, ServiceType.DISK,symKey, (ApipClient) apipAccount.getClient(),shareApiAccount);//checkFcAccount(diskAccountId,ApiType.DISK,config,symKey, (ApipClient) apipAccount.getClient());
        if(shareApiAccount)checkIfMainFidIsApiAccountUser(symKey,config,br,apipAccount, mainFid);
        if(diskAccount !=null && diskAccount.getClient()!=null) diskAccountId = diskAccount.getId();
        else System.out.println("No Disk service.");

        if(apipAccount ==null || diskAccount ==null){
            System.out.println("Failed to get necessary API services.");
            System.exit(0);
        }

        saveSettings(mainFid);
        saveConfig();
        System.out.println("Service settings initiated.");
        return mainFid;
    }

    @Override
    public void inputAll(BufferedReader br) {
    }

    @Override
    public void updateAll(BufferedReader br) {

    }

    @Override
    public void saveSettings(String id) {
        writeToFile(id);
    }

    @Override
    public void resetLocalSettings(byte[] symKey) {
        System.out.println("No local settings.");
        Menu.anyKeyToContinue(br);

//        Menu menu = new Menu();
//        menu.add("Reset listenPath");
//        menu.add("Reset account");
//        menu.add("minPayment");
//        int choice = menu.choose(br);
//        menu.show();
//        switch (choice){
//            case 1 -> updateAll(br);
//        }
    }

//    @Override
//    public Object resetDefaultApi(byte[] symKey, ApiType apiType) {
//        return null;
//    }

    @Override
    public void resetApis(byte[] symKey, JedisPool jedisPool, ApipClient apipClient){
        Menu menu = new Menu();
        menu.setName("Reset APIs for Disk client");
        menu.add("Reset APIP");
        menu.add("Reset DISK");

        while (true) {
            menu.show();
            int choice = menu.choose(br);
            switch (choice) {
                case 1 -> resetApi(symKey, apipClient, ServiceType.APIP);
                case 2 -> resetApi(symKey, apipClient, ServiceType.DISK);
                default -> {
                    return;
                }
            }
        }
    }

    @Override
    public void close() {

    }

    public String getDiskAccountId() {
        return diskAccountId;
    }

    public void setDiskAccountId(String diskAccountId) {
        this.diskAccountId = diskAccountId;
    }

    public String getApipAccountId() {
        return apipAccountId;
    }

    public void setApipAccountId(String apipAccountId) {
        this.apipAccountId = apipAccountId;
    }

    public ApiAccount getDiskAccount() {
        return diskAccount;
    }

    public void setDiskAccount(ApiAccount diskAccount) {
        this.diskAccount = diskAccount;
    }

}
