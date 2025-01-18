package server.balance;

import constants.FieldNames;
import feip.feipData.Service;
import appTools.Menu;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import constants.Strings;
import tools.JsonTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import appTools.Settings;
import server.order.User;

import java.io.BufferedReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Set;

import static tools.EsTools.recreateIndex;
import static constants.Strings.*;
import static appTools.Settings.addSidBriefToName;

public class BalanceManager {
    private static final Logger log = LoggerFactory.getLogger(BalanceManager.class);
    private final ElasticsearchClient esClient;
    private final JedisPool jedisPool;
    private final BufferedReader br;
    private Service service;
    public static  final  String  balanceMappingJsonStr = "{\"mappings\":{\"properties\":{\"user\":{\"type\":\"text\"},\"consumeVia\":{\"type\":\"text\"},\"orderVia\":{\"type\":\"text\"},\"bestHeight\":{\"type\":\"keyword\"}}}}";
    public static String sid;

    public BalanceManager(Service service, BufferedReader br, ElasticsearchClient esClient, JedisPool jedisPool) {
        this.esClient = esClient;
        this.jedisPool = jedisPool;
        this.br = br;
        this.service = service;
        BalanceManager.sid = service.getSid();
    }

    public void menu()  {

        Menu menu = new Menu();
        menu.setTitle("User Manager");
        ArrayList<String> menuItemList = new ArrayList<>();


        menuItemList.add("Find user balance");
        menuItemList.add("Backup user balance to ES");
        menuItemList.add("Recover user balance from ES");
        menuItemList.add("Recover user balance from File");
        menuItemList.add("Recreate balance index");

        menu.add(menuItemList);
        while (true) {
            menu.show();
            int choice = menu.choose(br);
            String balance0FileName = Settings.getLocalDataDir(sid)+ FieldNames.BALANCE + 0 + DOT_JSON;
            switch (choice) {

                case 1 -> findUsers(br);
                case 2 -> BalanceInfo.backupBalance(sid,esClient,jedisPool);
                case 3 -> BalanceInfo.recoverUserBalanceFromEs(sid, esClient,jedisPool);
                case 4 -> BalanceInfo.recoverUserBalanceFromFile(sid,balance0FileName,jedisPool);
                case 5 -> recreateBalanceIndex(br, esClient,  FieldNames.BALANCE, balanceMappingJsonStr);
                case 0 -> {
                    return;
                }
            }
        }
    }

    public static void recreateBalanceIndex(BufferedReader br, ElasticsearchClient esClient, String indexName, String mappingJsonStr) {
        String index = addSidBriefToName(sid,indexName);
        recreateIndex(index, mappingJsonStr, esClient, br);
        Menu.anyKeyToContinue(br);
    }

    public void findUsers(BufferedReader br) {
        System.out.println("All users balances are under the key of "+Settings.addSidBriefToName(sid, FieldNames.BALANCE)+" in Redis.");
        System.out.println("Input user's fch address or session name. Press enter to list all users:");
        String str;
        try {
            str = br.readLine();
        } catch (IOException e) {
            log.debug("br.readLine() wrong.");
            return;
        }

        try(Jedis jedis = this.jedisPool.getResource()) {

            if ("".equals(str)) {
                Set<String> addrSet = jedis.hkeys(Settings.addSidBriefToName(sid, FieldNames.BALANCE));
                for (String addr : addrSet) {
                    User user = getUser(addr, jedis);
                    System.out.println(JsonTools.toNiceJson(user));
                }
            } else {
                if (jedis.hget(Settings.addSidBriefToName(sid, FieldNames.BALANCE), str) != null) {
                    //For FID
                    jedis.select(0);
                    User user = getUser(str, jedis);
                    System.out.println(JsonTools.toNiceJson(user));
                } else {
                    //For sessionName
                    jedis.select(1);
                    if(jedis.exists(str)) {
                        User user = getUser(jedis.hget(str, FID), jedis);
                        System.out.println(JsonTools.toNiceJson(user));
                    }else {
                        System.out.println("Failed to find "+str);
                        return;
                    }
                }
            }
        }
        Menu.anyKeyToContinue(br);
    }

    private static User getUser(String addr, Jedis jedis) {
        if(addr==null)return null;
        User user = new User();
        user.setFid(addr);
        jedis.select(0);
        String balance = jedis.hget(addSidBriefToName(sid, FieldNames.BALANCE), addr);
        if(balance!=null)user.setBalance(balance);
        String sessionName = jedis.hget(Settings.addSidBriefToName(sid,Strings.ID_SESSION_NAME), addr);
        if(sessionName!=null)user.setSessionName(sessionName);
        else {
            return user;
        }
        jedis.select(1);
        user.setSessionKey(jedis.hget(sessionName, FieldNames.SESSION_KEY));

        long timestamp = System.currentTimeMillis() + jedis.expireTime(sessionName); // example timestamp in milliseconds
        Date date = new Date(timestamp); // create a new date object from the timestamp

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"); // define the date format
        String formattedDate = sdf.format(date); // format the date object to a string

        user.setExpireAt(formattedDate);

        return user;
    }

    public Service getService() {
        return service;
    }

    public void setService(Service service) {
        this.service = service;
    }
}