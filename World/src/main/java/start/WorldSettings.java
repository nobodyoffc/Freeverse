package start;

import clients.apipClient.ApipClient;
import appTools.Menu;
import configure.Configure;
import feip.feipData.Service;
import redis.clients.jedis.JedisPool;
import settings.Settings;

import java.io.BufferedReader;
import java.util.Map;

public class WorldSettings extends Settings {
    private Map<String,String> fidPriKeyCipherMap;

    public WorldSettings(Configure config, BufferedReader br) {
        super(config);
        this.config = config;
        this.br = br;
    }

    @Override
    public Service initiateServer(String sid, byte[] symKey, Configure config, BufferedReader br) {
        return null;
    }

    @Override
    public String initiateClient(String fid, byte[] symKey, Configure config, BufferedReader br) {
        return null;
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
        Menu menu = new Menu();
        menu.add("Add ID");
        menu.add("Remove ID");
        menu.add("Reset API services");
        menu.add("Refresh API Sessions");
        int choice = menu.choose(br);
        menu.show();
        switch (choice){
//            case 1 -> addId(br);
//            case 2 -> removeId(br);
//            case 3 -> resetApi(br);
//            case 4 -> refreshApi(br);
        }
    }


    @Override
    public void resetApis(byte[] symKey, JedisPool jedisPool, ApipClient apipClient) {
    }

    @Override
    public void close() {
    }

    public Map<String, String> getFidPriKeyCipherMap() {
        return fidPriKeyCipherMap;
    }

    public void setFidPriKeyCipherMap(Map<String, String> fidPriKeyCipherMap) {
        this.fidPriKeyCipherMap = fidPriKeyCipherMap;
    }
}
