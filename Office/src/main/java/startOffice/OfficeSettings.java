package startOffice;

import appTools.Menu;
import clients.apipClient.ApipClient;
import configure.ApiAccount;
import configure.ApiProvider;
import configure.ServiceType;
import configure.Configure;
import feip.feipData.Service;
import redis.clients.jedis.JedisPool;
import server.Settings;

import java.io.BufferedReader;
import java.io.IOException;

public class OfficeSettings extends Settings {
    private String diskAccountId;
    private transient ApiAccount diskAccount;
    public OfficeSettings(Configure configure) {
        super(configure);
    }
    @Override
    public String initiateClient(String fid, byte[] symKey, Configure config, BufferedReader br) {

        System.out.println("Initiating APP settings...");
        setInitForClient(fid, config, br);

        apipAccount = config.checkAPI(apipAccountId, mainFid, ServiceType.APIP,symKey);//checkApiAccount(apipAccountId, ApiType.APIP , config, symKey, null);
        checkIfMainFidIsApiAccountUser(symKey,config,br,apipAccount, mainFid);
        if(apipAccount.getClient()!=null)apipAccountId=apipAccount.getId();
        else System.out.println("No APIP service.");

        nasaAccount =config.checkAPI(nasaAccountId, mainFid, ServiceType.NASA_RPC,symKey);// checkApiAccount(nasaAccountId, ApiType.NASA_RPC, config, symKey, null);
        if(nasaAccount.getClient()!=null)nasaAccountId=nasaAccount.getId();
        else System.out.println("No Nasa node RPC service.");

        esAccount = config.checkAPI(esAccountId, mainFid, ServiceType.ES,symKey);//checkApiAccount(esAccountId, ApiType.ES, config, symKey, null);
        if(esAccount.getClient()!=null)esAccountId = esAccount.getId();
        else System.out.println("No ES service.");

        redisAccount = config.checkAPI(redisAccountId, mainFid, ServiceType.REDIS,symKey);//checkApiAccount(redisAccountId,ApiType.REDIS,config,symKey,null );
        if(redisAccount.getClient()!=null)redisAccountId = redisAccount.getId();
        else System.out.println("No Redis service.");

//        diskAccount = checkFcAccount(diskAccountId,ApiType.DISK, config,symKey,(ApipClient)apipAccount.getClient());
//        if(diskAccount!=null && diskAccount.getClient()!=null)
//            diskAccountId = diskAccount.getId();
//        else System.out.println("No Disk service.");

        saveSettings(mainFid);
        config.saveConfig();
        System.out.println("Service settings initiated.");
        return null;
    }

    @Override
    public Service initiateServer(String fid, byte[] symKey, Configure config, BufferedReader br) {
        return null;
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
    public void resetApis(byte[] symKey, JedisPool jedisPool, ApipClient apipClient){
        Menu menu = new Menu();
        menu.add("Reset initial APIP");
        menu.add("Reset Disk service");
        while (true) {
            System.out.println("Reset default API service...");
            ApiProvider apiProvider = config.chooseApiProviderOrAdd(config.getApiProviderMap(), apipClient);
            ApiAccount apiAccount = config.chooseAccountForTheProvider(apiProvider, mainFid, symKey,apipClient);

            if (apiAccount != null) {
                Object client = apiAccount.connectApi(config.getApiProviderMap().get(apiAccount.getProviderId()), symKey, br, null);
                if (client != null) {
                    menu.show();
                    int choice = menu.choose(br);
                    switch (choice) {
                        case 1 -> apipAccountId=apiAccount.getId();
                        case 2 -> diskAccountId=apiAccount.getId();
                        default -> {
                            return;
                        }
                    }
                    config.saveConfig();
                    System.out.println("Done.");
                } else System.out.println("Failed to connect the apiAccount: " + apiAccount.getApiUrl());
            } else System.out.println("Failed to get the apiAccount.");
        }
    }

    @Override
    public void close() throws IOException {
        br.close();
        esAccount.closeEs();
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
