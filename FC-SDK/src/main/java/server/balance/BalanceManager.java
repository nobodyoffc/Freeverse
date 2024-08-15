package server.balance;

import feip.feipData.Service;
import appTools.Menu;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import constants.Strings;
import javaTools.JsonTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.Settings;
import server.order.User;

import java.io.BufferedReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Set;

import static constants.Strings.BALANCE;
import static clients.esClient.EsTools.recreateIndex;
import static constants.Strings.DOT_JSON;
import static server.Settings.addSidBriefToName;

public class BalanceManager {
    private static final Logger log = LoggerFactory.getLogger(BalanceManager.class);
    private final ElasticsearchClient esClient;
    private final JedisPool jedisPool;
    private final BufferedReader br;
    private Service service;
    public static  final  String  balanceMappingJsonStr = "{\"mappings\":{\"properties\":{\"user\":{\"type\":\"text\"},\"consumeVia\":{\"type\":\"text\"},\"orderVia\":{\"type\":\"text\"},\"bestHeight\":{\"type\":\"keyword\"}}}}";
    public static String sid;
    public static String sidBrief;

    public BalanceManager(Service service, BufferedReader br, ElasticsearchClient esClient, JedisPool jedisPool) {
        this.esClient = esClient;
        this.jedisPool = jedisPool;
        this.br = br;
        this.service = service;
        BalanceManager.sid = service.getSid();
        BalanceManager.sidBrief=sid.substring(0,6);
    }

    public void menu()  {

        Menu menu = new Menu();
        menu.setName("User Manager");
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
            String balance0FileName = Settings.getLocalDataDir(sid)+BALANCE + 0 + DOT_JSON;
            switch (choice) {

                case 1 -> findUsers(br);
                case 2 -> BalanceInfo.backupBalance(sid,esClient,jedisPool);
                case 3 -> BalanceInfo.recoverUserBalanceFromEs(sid, esClient,jedisPool);
                case 4 -> BalanceInfo.recoverUserBalanceFromFile(balance0FileName,jedisPool);
                case 5 -> recreateBalanceIndex(br, esClient,  BALANCE, balanceMappingJsonStr);
                case 0 -> {
                    return;
                }
            }
        }
    }

    public static void recreateBalanceIndex(BufferedReader br, ElasticsearchClient esClient, String indexName, String mappingJsonStr) {
        String index = addSidBriefToName(sid,indexName);
        try {
            recreateIndex(index, mappingJsonStr, esClient);
        } catch (InterruptedException e) {
            log.debug("Recreate index {} wrong.",index);
        }
        Menu.anyKeyToContinue(br);
    }

    public static void findUsers(BufferedReader br) {
        System.out.println("Input user's fch address or session name. Press enter to list all users:");
        String str;
        try {
            str = br.readLine();
        } catch (IOException e) {
            log.debug("br.readLine() wrong.");
            return;
        }

        Jedis jedis0Common = new Jedis();
        Jedis jedis1Session = new Jedis();

        jedis1Session.select(1);

        if ("".equals(str)) {
            Set<String> addrSet = jedis0Common.hkeys(sidBrief+"_"+Strings.FID_SESSION_NAME);
            for (String addr : addrSet) {
                User user = getUser(addr, jedis0Common, jedis1Session);
                System.out.println(JsonTools.toNiceJson(user));
            }
        } else {
            if (jedis0Common.hget(sidBrief+"_"+Strings.FID_SESSION_NAME, str) != null) {
                User user = getUser(str, jedis0Common, jedis1Session);
                System.out.println(JsonTools.toNiceJson(user));
            } else if (jedis1Session.hgetAll(str) != null) {
                User user = getUser(jedis1Session.hget(str, "addr"), jedis0Common, jedis1Session);
                System.out.println(JsonTools.toNiceJson(user));
            }
        }
        Menu.anyKeyToContinue(br);
    }

    private static User getUser(String addr, Jedis jedis0Common, Jedis jedis1Session) {
        User user = new User();
        user.setFid(addr);
        user.setBalance(jedis0Common.hget(sidBrief+"_"+Strings.BALANCE, addr));
        String sessionName = jedis0Common.hget(sidBrief+"_"+Strings.FID_SESSION_NAME, addr);
        user.setSessionName(sessionName);
        user.setSessionKey(jedis1Session.hget(sessionName, "sessionKey"));

        long timestamp = System.currentTimeMillis() + jedis1Session.expireTime(sessionName); // example timestamp in milliseconds
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