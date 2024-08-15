package clients.apipClient;

import appTools.Menu;
import configure.ServiceType;
import configure.Configure;
import feip.feipData.Service;
import redis.clients.jedis.JedisPool;
import server.Settings;

import java.io.BufferedReader;

public class ApipClientSettings extends Settings {
    public ApipClientSettings(Configure configure) {
        super(configure);
    }


//    public ApipClientSettings(String fid, byte[] symKey) {
//        super(fid,symKey);
//    }

    @Override
    public Service initiateServer(String sid, byte[] symKey, Configure config, BufferedReader br) {
        return null;
    }


    public String initiateClient(String fid,byte[] symKey, Configure config, BufferedReader br) {
        System.out.println("Initiating APIP Client settings...");
        setInitForClient(fid, config, br);

        String mainFidPriKeyCipher = config.getFidCipherMap().get(mainFid);

        apipAccount = config.checkAPI(apipAccountId, mainFid, ServiceType.APIP,symKey);//checkApiAccount(apipAccountId,ApiType.APIP , config, symKey, null);
        checkIfMainFidIsApiAccountUser(symKey,config,br,apipAccount, mainFid);
        if(apipAccount.getClient()!=null)apipAccountId=apipAccount.getId();
        else System.out.println("No APIP service or failed to get APIP client.");

        saveSettings(mainFid);
        Configure.saveConfig();
        System.out.println("APIP client settings initiated.");
        return mainFid;
    }

    @Override
    public void inputAll(BufferedReader br) {
    }

    @Override
    public void updateAll(BufferedReader br) {
    }

    @Override
    public void saveSettings(String mainFid) {
        writeToFile(mainFid);
    }

    @Override
    public void resetLocalSettings(byte[] symKey) {
        System.out.println("No local settings.");
        Menu.anyKeyToContinue(br);
    }
    
    @Override
    public void resetApis(byte[] symKey,JedisPool jedisPool,ApipClient apipClient){
        Menu menu = new Menu();
        menu.setName("Reset APIs for Disk client");
        menu.add("Reset APIP");

        while (true) {
            menu.show();
            int choice = menu.choose(br);
            switch (choice) {
                case 1 -> resetApi(symKey, apipClient, ServiceType.APIP);
                default -> {
                    return;
                }
            }
        }
    }

    @Override
    public void close() {
    }

    public String getApipAccountId() {
        return apipAccountId;
    }

    public void setApipAccountId(String apipAccountId) {
        this.apipAccountId = apipAccountId;
    }

}
