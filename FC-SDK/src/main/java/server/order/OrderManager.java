package server.order;

import fch.ParseTools;
import feip.feipData.Service;
import appTools.Inputer;
import appTools.Menu;
import appTools.Shower;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.google.gson.Gson;
import constants.IndicesNames;
import constants.Strings;
import constants.Values;
import crypto.KeyTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.Counter;
import server.Settings;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;

import static constants.Constants.*;
import static constants.Strings.*;
import static server.Indices.orderMappingJsonStr;
import static server.Indices.recreateApipIndex;
import static server.Settings.addSidBriefToName;


public class OrderManager {

    private static final Logger log = LoggerFactory.getLogger(OrderManager.class);
    private final ElasticsearchClient esClient;
    private final BufferedReader br;
    private final JedisPool jedisPool;
    private final Counter counter;
    private static String sid;
    private Service service;

    public OrderManager(Service service, Counter counter, BufferedReader br, ElasticsearchClient esClient, JedisPool jedisPool) {
        this.esClient = esClient;
        this.br = br;
        this.jedisPool = jedisPool;
        this.counter = counter;
        this.service = service;
        OrderManager.sid =service.getSid();
    }

    public void menu(){

        Menu menu = new Menu();
        menu.setName("Order Manager");
        ArrayList<String> menuItemList = new ArrayList<>();

        menuItemList.add("How to buy this service?");
        menuItemList.add("Recreate Order index");
        menuItemList.add("Switch scanning OpReturn");
        menuItemList.add("Switch order scanner");
        menuItemList.add("Find orders of a FID");
        menuItemList.add("Set last order height to 0");

        menu.add(menuItemList);
        while(true) {
            menu.show();
            int choice = menu.choose(br);
            switch (choice) {
                case 1-> howToBuyService(br);
                case 2 -> recreateIndexAndResetOrderHeight(sid,br, esClient,  IndicesNames.ORDER, orderMappingJsonStr);
                case 3 -> switchScanOpReturn(br);
                case 4 -> switchOrderScanner(counter);
                case 5 -> findFidOrders(br,esClient);
                case 6 -> resetLastOrderHeight(br);
                case 0 -> {
                    return;
                }
            }
        }
    }

    private void recreateIndexAndResetOrderHeight(String sid,BufferedReader br, ElasticsearchClient esClient, String order, String orderMappingJsonStr) {
        Menu.askIfToDo("You will loss all orders info in the 'order' index of ES and Redis. Do you want to RECREATE?",br);
        recreateApipIndex(sid,br, esClient, IndicesNames.ORDER, orderMappingJsonStr);
        resetLastOrderHeight(br);
    }

    private void resetLastOrderHeight(BufferedReader br) {

        System.out.println("Reset last order height to 0? All order and balance will be flushed. 'reset' to reset:");

        String input = Inputer.inputString(br);

        if ("reset".equals(input)) {
            try(Jedis jedis = jedisPool.getResource()) {
                jedis.set(Settings.addSidBriefToName(sid,ORDER_LAST_HEIGHT), "0");
                jedis.set(Settings.addSidBriefToName(sid,ORDER_LAST_BLOCK_ID), zeroBlockId);
                System.out.println("Last order height has set to 0.");
            }catch (Exception e){
                log.error("Set order height and blockId into jedis wrong.");
                return;
            }
        }
        Menu.anyKeyToContinue(br);
    }

    private void switchOrderScanner(Counter counter) {
        System.out.println("OrderScanner running is "+ counter.isRunning()+".");
        Menu.askIfToDo("Switch it?",br);
        if(counter.isRunning().get()){
            counter.close();
        }else{
            counter.restart();
        }
        System.out.println("OrderScanner running is "+ counter.isRunning()+" now.");
        Menu.anyKeyToContinue(br);
    }

    private void findFidOrders(BufferedReader br, ElasticsearchClient esClient)  {
        String fid;
        while(true) {
            System.out.println("Input FID. 'q' to quit:");
            String input;
            try {
                input = br.readLine();
            } catch (IOException e) {
                System.out.println("br.readLine() wrong.");
                return;
            }
            if ("q".equals(input)) return;
            if (!KeyTools.isValidFchAddr(input)) {
                System.out.println("Invalid FID. Input again.");
                continue;
            }
            fid =input;
            break;
        }

        String finalFid = fid;
        SearchResponse<Order> result = null;
        try {
            result = esClient.search(s -> s
                            .index(addSidBriefToName(sid,IndicesNames.ORDER))
                            .query(q -> q.term(t -> t.field(FROM_FID).value(finalFid)))
                            .sort(so->so.field(f->f.field(TIME)))
                            .size(100)
                    , Order.class);
        } catch (IOException e) {
            log.debug("Find order wrong. Check ES");
        }

        if(result!=null && result.hits().hits().size()>0){
            int totalLength =FCH_LENGTH +2+DATE_TIME_LENGTH+2+FID_LENGTH+2+HEX256_LENGTH+2;
            System.out.println("Orders of "+fid+" : ");
            Shower.printUnderline(totalLength);
            System.out.print(Shower.formatString("fch",20));
            System.out.print(Shower.formatString("Time",22));
            System.out.print(Shower.formatString("Via",38));
            System.out.print(Shower.formatString("TxId",66));
            System.out.println();
            Shower.printUnderline(totalLength);

            for(Hit<Order> hit: result.hits().hits()){
                Order order = hit.source();
                if (order==null)continue;
                String fch = String.valueOf((double) order.getAmount()/FchToSatoshi);
                String time = ParseTools.convertTimestampToDate(order.getTime());
                String txId = order.getTxId();
                String via = order.getVia();
                System.out.print(Shower.formatString(fch, FCH_LENGTH +2));
                System.out.print(Shower.formatString(time, DATE_TIME_LENGTH+2));
                System.out.print(Shower.formatString(via, FID_LENGTH+2));
                System.out.print(Shower.formatString(txId, HEX256_LENGTH+2));
                System.out.println();
            }
            Shower.printUnderline(totalLength);
        }else{
            System.out.println("No orders found.");
        }
        Menu.anyKeyToContinue(br);
    }

    private void switchScanOpReturn(BufferedReader br) {
        try(Jedis jedis = jedisPool.getResource()) {
            String isCheckOrderOpReturn = jedis.hget(CONFIG, Strings.CHECK_ORDER_OPRETURN);
            System.out.println("Check order's OpReturn: " + isCheckOrderOpReturn + ". Change it? 'y' to switch.");
            String input;
            try {
                input = br.readLine();
            } catch (IOException e) {
                System.out.println("br.readLine() wrong.");
                return;
            }
            if ("y".equals(input)) {
                if (Values.TRUE.equals(isCheckOrderOpReturn)) {
                    jedis.hset(CONFIG, Strings.CHECK_ORDER_OPRETURN, Values.FALSE);
                } else if (Values.FALSE.equals(isCheckOrderOpReturn)) {
                    jedis.hset(CONFIG, Strings.CHECK_ORDER_OPRETURN, Values.TRUE);
                } else {
                    System.out.println("Invalid input.");
                }
            }
            System.out.println("Check order's OpReturn: " + jedis.hget(CONFIG, Strings.CHECK_ORDER_OPRETURN) + " now.");
        }
        Menu.anyKeyToContinue(br);
    }

    private static void howToBuyService(BufferedReader br) {
        System.out.println("Anyone can send a freecash TX with following json in Op_Return to buy your service:" +
                "\n--------");
        System.out.println(new Gson().toJson(Order.getJsonBuyOrder(sid)) +
                "\n--------" +
                "\nMake sure the 'sid' is your service id. " );
        Menu.anyKeyToContinue(br);
//        try(Jedis jedis = jedisPool.getResource()) {
//            String sidStr = jedis.get(Settings.addSidBriefToName(sid,SERVICE));
//            if (sidStr == null) {
//                System.out.println("No service yet.");
//                return;
//            }
//            Gson gson = new GsonBuilder().setPrettyPrinting().create();
//            Service service = gson.fromJson(sidStr, Service.class);
//
//
//            try {
//                br.readLine();
//            } catch (IOException ignored) {
//            }
//        }
    }

    public static String getSid() {
        return sid;
    }

    public static void setSid(String sid) {
        OrderManager.sid = sid;
    }

    public Service getService() {
        return service;
    }

    public void setService(Service service) {
        this.service = service;
    }
}
