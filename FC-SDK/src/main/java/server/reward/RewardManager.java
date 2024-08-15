package server.reward;

import clients.apipClient.ApipClient;
import apip.apipData.Sort;
import fch.ParseTools;
import fch.fchData.Address;
import appTools.Menu;
import appTools.Shower;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch.core.DeleteResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import clients.esClient.EsTools;
import javaTools.JsonTools;
import nasa.NaSaRpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.Indices;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import static constants.Constants.FchToSatoshi;
import static constants.Constants.REWARD_HISTORY_FILE;
import static constants.IndicesNames.ADDRESS;
import static constants.Strings.*;
import static server.Indices.recreateApipIndex;
import static server.Settings.addSidBriefToName;

public class RewardManager {
    private static final Logger log = LoggerFactory.getLogger(RewardManager.class);
    public static final String UNSIGNED_REWARD_TX_FILE = "unsigned.txt";
    private final ElasticsearchClient esClient;
    private final ApipClient apipClient;
    private NaSaRpcClient naSaRpcClient;//for the data of the FCH
    private final JedisPool jedisPool;
    private final BufferedReader br;
    private final String sid;
    private final String account;
    Rewarder rewarder;

    public RewardManager(String sid, String account,ApipClient apipClient,ElasticsearchClient esClient,NaSaRpcClient naSaRpcClient,JedisPool jedisPool, BufferedReader br) {
        this.esClient = esClient;
        this.apipClient = apipClient;
        this.jedisPool = jedisPool;
        this.br = br;
        this.rewarder = new Rewarder(sid,account,apipClient,esClient,naSaRpcClient,jedisPool);
        this.sid =sid;
        this.account = account;
    }

    public void menu(String consumeViaShare,String orderViaShare) {
        Menu menu = new Menu();

        ArrayList<String> menuItemList = new ArrayList<>();
        menuItemList.add("Show the last reward");
        menuItemList.add("Show all unpaid rewards");
        menuItemList.add("Get all unsignedTxCs for paying ");
        menuItemList.add("Delete rewards");
//        menuItemList.add("Make a fixed incomeT rewardInfo");
        menuItemList.add("Backup reward history to file");
        menuItemList.add("Set reward parameters");

        menu.add(menuItemList);
        while(true) {
            menu.show();
            int choice = menu.choose(br);
            switch (choice) {
                case 1 -> showLastReward(esClient,br);
                case 2 -> showAllUnpaidRewards(esClient,br);
                case 3 -> getAllUnsignedTxCsToPay(esClient,br);
                case 4 -> deleteRewards(br, esClient);
//                case 5 -> makeFixedIncomeTReward(br,account);
                case 5 -> backupRewardHistoryToFile(br,esClient);
                case 6 -> {
                   Rewarder.setRewardParameters(sid,consumeViaShare,orderViaShare,jedisPool,br);
                }
                case 0 -> {
                    return;
                }
            }
        }
    }

    private void backupRewardHistoryToFile(BufferedReader br, ElasticsearchClient esClient) {

        ArrayList<RewardInfo> rewardInfoList = getRewardInfoList(esClient);
        if(rewardInfoList==null)return;
        for(RewardInfo rewardInfo:rewardInfoList){
            JsonTools.writeObjectToJsonFile(rewardInfo,REWARD_HISTORY_FILE,true);
        }
        log.debug(rewardInfoList.size() +"reward records saved to "+REWARD_HISTORY_FILE+ ".");
        Menu.anyKeyToContinue(br);
    }

    private ArrayList<RewardInfo> getRewardInfoList(ElasticsearchClient esClient) {
        List<SortOptions> sort = Sort.makeTwoFieldsSort(TIME, DESC, REWARD_ID, ASC);
        ArrayList<RewardInfo> rewardInfoList = new ArrayList<>();
        SearchResponse<RewardInfo> result;
        List<String> last = null;
        int size;
        try {
            result = esClient.search(s -> s
                            .index(addSidBriefToName(sid,REWARD).toLowerCase())
                            .size(EsTools.READ_MAX / 10)
                            .sort(sort)
                    , RewardInfo.class);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        size = result.hits().hits().size();
        if(size>0) {
            List<Hit<RewardInfo>> hitList = result.hits().hits();
            for(Hit<RewardInfo> hit:hitList){
                rewardInfoList.add(hit.source());
            }
            last =  hitList.get(hitList.size()-1).sort();
        }

        while(size == EsTools.READ_MAX / 10){
            try {
                List<String> finalLast = last;
                result = esClient.search(s -> s
                                .index(addSidBriefToName(sid,REWARD).toLowerCase())
                                .size(EsTools.READ_MAX / 10)
                                .sort(sort)
                                .searchAfter(finalLast)
                        , RewardInfo.class);
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }

            size = result.hits().hits().size();

            if(size>0) {
                List<Hit<RewardInfo>> hitList = result.hits().hits();
                for(Hit<RewardInfo> hit:hitList){
                    rewardInfoList.add(hit.source());
                }
                last =  hitList.get(hitList.size()-1).sort();
            }
        }

        return rewardInfoList;
    }

    private void getAllUnsignedTxCsToPay(ElasticsearchClient esClient, BufferedReader br) {
        ArrayList<RewardInfo> unpaidRewardList = getUnpaidRewardList(esClient);
        if(unpaidRewardList==null|| unpaidRewardList.size()==0){
            System.out.println("No unpaid reward.");
            Menu.anyKeyToContinue(br);
            return;
        }

        AffairMaker affairMaker;
        String account = null;
        try(Jedis jedis = jedisPool.getResource()) {
            account = jedis.hget(jedis.hget(CONFIG,SERVICE_NAME)+"_"+ PARAMS, ACCOUNT);
        }catch (Exception e){
            log.error("Get service account wrong. Check redis.");
        }

        File unsignedTxFile = new File(UNSIGNED_REWARD_TX_FILE);

        try {
            if(unsignedTxFile.exists()){
                if(!unsignedTxFile.delete())
                    log.debug("Delete unsigned tx file failed.");
            }
            if(!unsignedTxFile.createNewFile())
                log.debug("Create unsigned tx file failed.");
        } catch (IOException e) {
            log.error("Create {} wrong.",UNSIGNED_REWARD_TX_FILE);
            return;
        }

        FileOutputStream fos;
        try {
            fos = new FileOutputStream(unsignedTxFile);
        } catch (FileNotFoundException e) {
            log.error("Create fileOutputStream wrong.");
            return;
        }

        for(RewardInfo rewardInfo: unpaidRewardList){
            affairMaker = new AffairMaker(sid,account, rewardInfo,esClient,jedisPool);
            String affairSignTxJson = affairMaker.makeAffair();
            byte[] txBytes = (affairSignTxJson+"\n\n").getBytes();
            try {
                fos.write(txBytes);
            } catch (IOException e) {
                log.error("Write tx into {} wrong.",UNSIGNED_REWARD_TX_FILE);
                return;
            }
        }

        try {
            fos.close();
        } catch (IOException e) {
            log.error("Close fileOutputStream wrong.");
        }

        System.out.println("All unpaid reward write into file: "+ UNSIGNED_REWARD_TX_FILE+".");
        Menu.anyKeyToContinue(br);
    }

    private void showAllUnpaidRewards(ElasticsearchClient esClient, BufferedReader br) {
        ArrayList<RewardInfo> unpaidRewardList = getUnpaidRewardList(esClient);
        if(unpaidRewardList==null|| unpaidRewardList.size()==0){
            System.out.println("No unpaid reward.");
            Menu.anyKeyToContinue(br);
            return;
        }
        System.out.println("Unpaid rewards: ");
        System.out.print(Shower.formatString("State",12));
        System.out.print(Shower.formatString("Time",22));
        System.out.print(Shower.formatString("Amount total",20));
        System.out.print(Shower.formatString("Reward ID",66));
        System.out.println();

        for(RewardInfo rewardInfo : unpaidRewardList){
            System.out.print(Shower.formatString(rewardInfo.getState().name(),12));

            String time = ParseTools.convertTimestampToDate(rewardInfo.getTime());
            System.out.print(Shower.formatString(time,22));

            String rewardT = String.valueOf((double) rewardInfo.getRewardT()/FchToSatoshi);
            System.out.print(Shower.formatString(rewardT,20));

            System.out.print(Shower.formatString(rewardInfo.getRewardId(),66));
            System.out.println();
        }
        Menu.anyKeyToContinue(br);
    }

    private ArrayList<RewardInfo> getUnpaidRewardList(ElasticsearchClient esClient) {
        ArrayList<RewardInfo> unpaidRewardList = new ArrayList<>();
        ArrayList<Sort> sortList = Sort.makeSortList(TIME,false,REWARD_ID,true,null,false);
        List<SortOptions> sortOptionsList = Sort.getSortList(sortList);
        SearchResponse<RewardInfo> result;
        try{
            result = esClient.search(s -> s
                            .index(addSidBriefToName(sid,REWARD).toLowerCase())
                            .query(q->q.term(t->t.field(STATE).value(UNPAID)))
                            .size(200)
                            .sort(sortOptionsList)
                    , RewardInfo.class);
            if(result.hits().hits().size()==0){
                log.debug("No rewardInfo found in ES.");
                return null;
            }
        }catch (Exception e){
            log.error("Get unpaidRewardList wrong.",e);
            return null;
        }

        for(Hit<RewardInfo> hit : result.hits().hits()) unpaidRewardList.add(hit.source());

        return unpaidRewardList;
    }

    private void showLastReward(ElasticsearchClient esClient, BufferedReader br) {
        RewardInfo reward = getLastRewardInfo(sid, esClient);
        System.out.println(JsonTools.toNiceJson(reward));
        Menu.anyKeyToContinue(br);
    }

    private void deleteRewards(BufferedReader br, ElasticsearchClient esClient) {
        while (true){
            try {
                System.out.println("""
                    Delete reward!!
                    'q' to quit;
                    'last' to delete the last reward;
                    'all' to delete all rewards;
                    or the rewardId to delete it.""");
                String input = br.readLine();
                switch (input){
                    case "q" -> {return;}
                    case "last" -> deleteLastReward(esClient);
                    case "all" -> deleteAllReward(esClient);
                    default -> {
                        if(input.length()==64)deleteByRewardId(esClient,input);
                    }
                }
            }catch (Exception e){
                log.error("DeleteRewards wrong.",e);
            }
        }
    }

    private void deleteLastReward(ElasticsearchClient esClient) {
        RewardInfo reward = getLastRewardInfo(sid, esClient);
        String lastId = null;
        if(reward!=null){
            lastId = reward.getRewardId();
        }
        deleteByRewardId(esClient,lastId);
    }

    public static RewardInfo getLastRewardInfo(String sid, ElasticsearchClient esClient) {
        ArrayList<Sort> sortList = Sort.makeSortList(TIME,false,REWARD_ID,true,null,false);
        List<SortOptions> sortOptionsList = Sort.getSortList(sortList);
        Jedis jedis1 = new Jedis();
        try{
            SearchResponse<RewardInfo> result = esClient.search(s -> s
                            .index(addSidBriefToName(sid,REWARD).toLowerCase())
                            .size(1)
                            .sort(sortOptionsList)
                    , RewardInfo.class);
            if(result.hits().hits().size()==0){
                log.debug("No rewardInfo found in ES.");
                return null;
            }
            return result.hits().hits().get(0).source();
        }catch (Exception e){
            log.error("Delete last reward wrong.",e);
            jedis1.close();
        }
        jedis1.close();
        return null;
    }

    private void deleteAllReward(ElasticsearchClient esClient)  {
        try {
            recreateApipIndex(sid,br, esClient, REWARD, Indices.rewardMappingJsonStr);
        } catch (Exception e) {
            log.error("Delete all rewards by recreating reward index error.",e);
        }
    }

    private void deleteByRewardId(ElasticsearchClient esClient, String rewardId) {
        try{
            DeleteResponse result = esClient.delete(d -> d
                    .index(addSidBriefToName(sid,REWARD).toLowerCase())
                    .id(rewardId)
            );

            log.debug(result.result().jsonValue());

        }catch (Exception e){
            log.error("Delete reward {} wrong.",rewardId,e);
        }
    }
//
//    private void makeFixedIncomeTReward(BufferedReader br,String account) {
//        long incomeT;
//        try {
//            long balance = getFidBalance(account, esClient);
//            System.out.println("The balance of "+account+" is "+ (double)balance/FchToSatoshi);
//        }catch (Exception ignore){}
//        while (true){
//            System.out.println("Input the fixed income in FCH, 'q' to quit:");
//            try {
//                String input = br.readLine();
//                if("q".equals(input))return;
//                incomeT = (long)Double.parseDouble(input)*FchToSatoshi;
//                break;
//            }catch (Exception e){
//                System.out.println("It's not a number. Try again.");
//            }
//        }
//        rewarder.setIncomeT(incomeT);
//        RewardReturn result = rewarder.doReward1(account);
//        if(result.getCode()>0){
//            System.out.println("Make fixed income reward failed:");
//            System.out.println(result.getMsg());
//        }
//        Menu.anyKeyToContinue(br);
//    }

    private long getFidBalance(String fid,ElasticsearchClient esClient) {
        try {
            Address addr = esClient.get(g->g.index(ADDRESS).id(fid), Address.class).source();
            if(addr!=null){
                return addr.getBalance();
            }else return 0;
        } catch (IOException e) {
            log.debug("Get fid balance wrong.");
        }
        return 0;
    }
}
