package startClient;

import appTools.Menu;
import clients.apipClient.ApipClient;
import configure.ApiAccount;
import configure.Configure;
import configure.ServiceType;
import feip.feipData.Service;
import redis.clients.jedis.JedisPool;
import server.Settings;

import java.io.BufferedReader;

import static configure.Configure.saveConfig;

public class TalkClientSettings extends Settings {
    String talkAccountId;
    private transient ApiAccount talkAccount;

    public TalkClientSettings(Configure configure) {
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

        talkAccount = config.checkAPI(talkAccountId, mainFid, ServiceType.TALK,symKey, (ApipClient) apipAccount.getClient(),shareApiAccount);//checkFcAccount(talkAccountId,ApiType.TALK,config,symKey, (ApipClient) apipAccount.getClient());
        if(shareApiAccount)checkIfMainFidIsApiAccountUser(symKey,config,br,apipAccount, mainFid);
        if(talkAccount !=null && talkAccount.getClient()!=null) talkAccountId = talkAccount.getId();
        else System.out.println("No Talk service.");

        if(apipAccount ==null || talkAccount ==null){
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
        menu.setName("Reset APIs for Talk client");
        menu.add("Reset APIP");
        menu.add("Reset TALK");

        while (true) {
            menu.show();
            int choice = menu.choose(br);
            switch (choice) {
                case 1 -> resetApi(symKey, apipClient, ServiceType.APIP);
                case 2 -> resetApi(symKey, apipClient, ServiceType.TALK);
                default -> {
                    return;
                }
            }
        }
    }

    @Override
    public void close() {

    }

    public String getTalkAccountId() {
        return talkAccountId;
    }

    public void setTalkAccountId(String talkAccountId) {
        this.talkAccountId = talkAccountId;
    }

    public String getApipAccountId() {
        return apipAccountId;
    }

    public void setApipAccountId(String apipAccountId) {
        this.apipAccountId = apipAccountId;
    }

    public ApiAccount getTalkAccount() {
        return talkAccount;
    }

    public void setTalkAccount(ApiAccount talkAccount) {
        this.talkAccount = talkAccount;
    }

}
