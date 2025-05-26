package server.order;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.json.JsonData;
import data.feipData.Service;
import ui.Inputer;
import ui.Menu;
import ui.Shower;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import constants.IndicesNames;
import core.crypto.KeyTools;
import data.feipData.serviceParams.Params;
import utils.FchUtils;
import utils.JsonUtils;
import utils.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.Counter;
import config.Settings;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static constants.Constants.*;
import static constants.Strings.*;
import static server.Indices.orderMappingJsonStr;
import static server.Indices.recreateApipIndex;
import static config.Settings.addSidBriefToName;


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
        OrderManager.sid =service.getId();
    }

    public void menu(){

        Menu menu = new Menu();
        menu.setTitle("Order Manager");
        ArrayList<String> menuItemList = new ArrayList<>();

        menuItemList.add("Find orders of a FID");
        menuItemList.add("Find orders since height");
        menuItemList.add("Recreate Order index");
        menuItemList.add("Switch order scanner");
        menuItemList.add("Set last order height to 0");
        menuItemList.add("How to buy this service?");

        menu.add(menuItemList);
        while(true) {
            menu.show();
            int choice = menu.choose(br);
            switch (choice) {
                case 1 -> findFidOrders(br,esClient);
                case 2 -> findOrderSinceHeight(br,esClient);
                case 3 -> recreateIndexAndResetOrderHeight(sid,br, esClient,  IndicesNames.ORDER, orderMappingJsonStr);
                case 4 -> switchOrderScanner(counter);
                case 5 -> resetLastOrderHeight(br);
                case 6 -> howToBuyService(br);
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
        System.out.println("All orders are in the ES index of "+Settings.addSidBriefToName(sid,ORDER)+".");
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
            if (!KeyTools.isGoodFid(input)) {
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
                            .sort(so->so.field(f->f.field(TIME).order(SortOrder.Desc)))
                            .size(100)
                    , Order.class);
        } catch (IOException e) {
            log.debug("Find order wrong. Check ES");
        }

        showEsOrderResult(fid, result);
        Menu.anyKeyToContinue(br);
    }
    private void findOrderSinceHeight(BufferedReader br, ElasticsearchClient esClient)  {
        long height = Inputer.inputLong(br,"Input the height:");
        SearchResponse<Order> result = null;
        try {
            result = esClient.search(s -> s
                            .index(addSidBriefToName(sid, IndicesNames.ORDER))
                            .query(q -> q.range(r -> r.field(HEIGHT).gte(JsonData.of(height))))
                            .sort(so -> so.field(f -> f.field(TIME).order(SortOrder.Desc)))
                            .size(100)
                    , Order.class);
            if(result==null)return;
            showEsOrderResult(null,result);
            Menu.anyKeyToContinue(br);
            if(result.hits()==null||result.hits().hits()==null)return;
            int size = result.hits().hits().size();
            if(size<100)return;
            List<String> last = result.hits().hits().get(size - 1).sort();
            while(true){
                result = esClient.search(s -> s
                                .index(addSidBriefToName(sid, IndicesNames.ORDER))
                                .query(q -> q.range(r -> r.field(HEIGHT).gte(JsonData.of(height))))
                                .sort(so -> so.field(f -> f.field(TIME).order(SortOrder.Desc)))
                                .size(100)
                                .searchAfter(last)
                        , Order.class);
                if(result==null)return;
                showEsOrderResult(null,result);
                Menu.anyKeyToContinue(br);
                if(result.hits()==null||result.hits().hits()==null)return;
                size = result.hits().hits().size();
                if(size<100)return;
            }
        } catch (IOException e) {
            System.out.println("Something wrong:"+e.getMessage());
        }

    }
    private static void showEsOrderResult(String fid, SearchResponse<Order> result) {
        if(result !=null && result.hits().hits().size()>0){
            int totalLength =FCH_LENGTH +2+DATE_TIME_LENGTH+2+FID_LENGTH+2+HEX256_LENGTH+2;
            if(fid!=null)System.out.println("Orders of "+ fid +" : ");
            Shower.printUnderline(totalLength);
            System.out.print(Shower.formatString("core/fch",20));
            System.out.print(Shower.formatString("Time",22));
            System.out.print(Shower.formatString("Via",38));
            System.out.print(Shower.formatString("TxId",66));
            System.out.println();
            Shower.printUnderline(totalLength);

            for(Hit<Order> hit: result.hits().hits()){
                Order order = hit.source();
                if (order==null)continue;
                String fch = String.valueOf(utils.FchUtils.satoshiToCoin(order.getAmount()));
                String time = FchUtils.convertTimestampToDate(order.getTime());
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
    }

//    private void switchScanOpReturn(BufferedReader br) {
//        try(Jedis jedis = jedisPool.getResource()) {
//            String isCheckOrderOpReturn = jedis.hget(CONFIG, Strings.CHECK_ORDER_OPRETURN);
//            System.out.println("Check order's OpReturn: " + isCheckOrderOpReturn + ". Change it? 'y' to switch.");
//            String input;
//            try {
//                input = br.readLine();
//            } catch (IOException e) {
//                System.out.println("br.readLine() wrong.");
//                return;
//            }
//            if ("y".equals(input)) {
//                if (Values.TRUE.equals(isCheckOrderOpReturn)) {
//                    jedis.hset(CONFIG, Strings.CHECK_ORDER_OPRETURN, Values.FALSE);
//                } else if (Values.FALSE.equals(isCheckOrderOpReturn)) {
//                    jedis.hset(CONFIG, Strings.CHECK_ORDER_OPRETURN, Values.TRUE);
//                } else {
//                    System.out.println("Invalid input.");
//                }
//            }
//            System.out.println("Check order's OpReturn: " + jedis.hget(CONFIG, Strings.CHECK_ORDER_OPRETURN) + " now.");
//        }
//        Menu.anyKeyToContinue(br);
//    }

    private void howToBuyService(BufferedReader br) {

        Params params = (Params) service.getParams();
        Shower.printUnderline(20);
        System.out.println("Send at lest "+ params.getMinPayment()+"f to " +params.getDealer()+ " to buy the service. The price is " + NumberUtils.numberToPlainString(params.getPricePerKBytes(),"8")+"f/KB.");
        System.out.println("If you want to set the 'via' FID, write below into the OP_RETURN of the TX:");
        Shower.printUnderline(20);
        System.out.println(JsonUtils.toNiceJson(Order.getJsonBuyOrder(sid)));
        Shower.printUnderline(20);
//                "\nMake sure the 'sid' is your service id. " );
        Menu.anyKeyToContinue(br);
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
