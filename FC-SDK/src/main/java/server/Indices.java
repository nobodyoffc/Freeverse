package server;

import appTools.Menu;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.elasticsearch.indices.DeleteIndexResponse;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import constants.IndicesNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import static constants.IndicesNames.*;
import static constants.Strings.BALANCE;
import static constants.Strings.REWARD;
import static server.Settings.addSidBriefToName;


public class Indices {
    private static final Logger log = LoggerFactory.getLogger(Indices.class);
    private final ElasticsearchClient esClient;
    private final BufferedReader br;
    private String sid;
    public static  final  String  orderMappingJsonStr = "{\"mappings\":{\"properties\":{\"amount\":{\"type\":\"long\"},\"orderId\":{\"type\":\"keyword\"},\"fromFid\":{\"type\":\"wildcard\"},\"height\":{\"type\":\"long\"},\"time\":{\"type\":\"long\"},\"toFid\":{\"type\":\"wildcard\"},\"txId\":{\"type\":\"keyword\"},\"txIndex\":{\"type\":\"long\"},\"txid\":{\"type\":\"keyword\"},\"via\":{\"type\":\"wildcard\"}}}}";
    public static  final  String  balanceMappingJsonStr = "{\"mappings\":{\"properties\":{\"user\":{\"type\":\"text\"},\"consumeVia\":{\"type\":\"text\"},\"orderVia\":{\"type\":\"text\"},\"bestHeight\":{\"type\":\"keyword\"}}}}";
    public static final String  rewardMappingJsonStr = "{\"mappings\":{\"properties\":{\"rewardId\":{\"type\":\"keyword\"},\"rewardT\":{\"type\":\"long\"},\"time\":{\"type\":\"long\"},\"txId\":{\"type\":\"keyword\"},\"state\":{\"type\":\"keyword\"},\"bestHeight\":{\"type\":\"keyword\"},\"builderList\":{\"type\":\"nested\",\"properties\":{\"fid\":{\"type\":\"keyword\"},\"share\":{\"type\":\"float\"},\"amount\":{\"type\":\"long\"},\"fixed\":{\"type\":\"long\"}}},\"orderViaList\":{\"type\":\"nested\",\"properties\":{\"fid\":{\"type\":\"keyword\"},\"share\":{\"type\":\"float\"},\"amount\":{\"type\":\"long\"},\"fixed\":{\"type\":\"long\"}}},\"consumeViaList\":{\"type\":\"nested\",\"properties\":{\"fid\":{\"type\":\"keyword\"},\"share\":{\"type\":\"float\"},\"amount\":{\"type\":\"long\"},\"fixed\":{\"type\":\"long\"}}},\"costList\":{\"type\":\"nested\",\"properties\":{\"fid\":{\"type\":\"keyword\"},\"share\":{\"type\":\"float\"},\"amount\":{\"type\":\"long\"},\"fixed\":{\"type\":\"long\"}}}}}}";
    public static final String  webhookMappingJsonStr = "{\"mappings\":{\"properties\":{\"hookId\":{\"type\":\"keyword\"},\"owner\":{\"type\":\"wildcard\"},\"endpoint\":{\"type\":\"text\"},\"data\":{\"type\":\"object\"},\"method\":{\"type\":\"wildcard\"},\"op\":{\"type\":\"keyword\"}}}}";
    public static final String  swapStateJsonStr = "{\"mappings\":{\"properties\":{\"sid\":{\"type\":\"keyword\"},\"gSum\":{\"type\":\"double\"},\"mSum\":{\"type\":\"double\"},\"gBestBlockId\":{\"type\":\"keyword\"},\"gBestHeight\":{\"type\":\"long\"},\"mBestBlockId\":{\"type\":\"keyword\"},\"mBestHeight\":{\"type\":\"long\"},\"lastTime\":{\"type\":\"long\"},\"lastSn\":{\"type\":\"long\"},\"lastId\":{\"type\":\"keyword\"},\"gPendingSum\":{\"type\":\"double\"},\"mPendingSum\":{\"type\":\"double\"}}}}";
    public static final String  swapPendingMappingJsonStr = "{\"mappings\":{\"properties\":{\"sid\":{\"type\":\"keyword\"},\"pendingList\":{\"type\":\"nested\",\"properties\":{\"id\":{\"type\":\"keyword\"},\"sid\":{\"type\":\"keyword\"},\"sn\":{\"type\":\"long\"},\"act\":{\"type\":\"keyword\"},\"g\":{\"type\":\"object\",\"properties\":{\"txId\":{\"type\":\"keyword\"},\"refundTxId\":{\"type\":\"keyword\"},\"withdrawTxId\":{\"type\":\"keyword\"},\"refundAmt\":{\"type\":\"double\"},\"addr\":{\"type\":\"keyword\"},\"amt\":{\"type\":\"double\"},\"sum\":{\"type\":\"double\"},\"blockTime\":{\"type\":\"long\"},\"blockHeight\":{\"type\":\"long\"},\"blockIndex\":{\"type\":\"long\"},\"txFee\":{\"type\":\"double\"}}},\"m\":{\"type\":\"object\",\"properties\":{\"txId\":{\"type\":\"keyword\"},\"refundTxId\":{\"type\":\"keyword\"},\"withdrawTxId\":{\"type\":\"keyword\"},\"refundAmt\":{\"type\":\"double\"},\"addr\":{\"type\":\"keyword\"},\"amt\":{\"type\":\"double\"},\"sum\":{\"type\":\"double\"},\"blockTime\":{\"type\":\"long\"},\"blockHeight\":{\"type\":\"long\"},\"blockIndex\":{\"type\":\"long\"},\"txFee\":{\"type\":\"double\"}}},\"sendTime\":{\"type\":\"long\"},\"getTime\":{\"type\":\"long\"},\"state\":{\"type\":\"keyword\"},\"error\":{\"type\":\"text\"}}}}}}";
    public static final String  swapLpMappingJsonStr = "{\"mappings\":{\"properties\":{\"sid\":{\"type\":\"keyword\"},\"gLpRawMap\":{\"type\":\"object\",\"properties\":{\"key\":{\"type\":\"keyword\"},\"value\":{\"type\":\"double\"}}},\"gLpNetMap\":{\"type\":\"object\",\"properties\":{\"key\":{\"type\":\"keyword\"},\"value\":{\"type\":\"double\"}}},\"gLpShareMap\":{\"type\":\"object\",\"properties\":{\"key\":{\"type\":\"keyword\"},\"value\":{\"type\":\"double\"}}},\"mLpRawMap\":{\"type\":\"object\",\"properties\":{\"key\":{\"type\":\"keyword\"},\"value\":{\"type\":\"double\"}}},\"mLpNetMap\":{\"type\":\"object\",\"properties\":{\"key\":{\"type\":\"keyword\"},\"value\":{\"type\":\"double\"}}},\"mLpShareMap\":{\"type\":\"object\",\"properties\":{\"key\":{\"type\":\"keyword\"},\"value\":{\"type\":\"double\"}}},\"gLpRawSum\":{\"type\":\"double\"},\"mLpRawSum\":{\"type\":\"double\"},\"gServiceFee\":{\"type\":\"double\"},\"mServiceFee\":{\"type\":\"double\"}}}}";
    public static final String  swapFinishedMappingJsonStr = "{\"mappings\":{\"properties\":{\"id\":{\"type\":\"keyword\"},\"sid\":{\"type\":\"keyword\"},\"sn\":{\"type\":\"long\"},\"act\":{\"type\":\"keyword\"},\"g\":{\"type\":\"object\",\"properties\":{\"txId\":{\"type\":\"keyword\"},\"refundTxId\":{\"type\":\"keyword\"},\"withdrawTxId\":{\"type\":\"keyword\"},\"refundAmt\":{\"type\":\"double\"},\"addr\":{\"type\":\"keyword\"},\"amt\":{\"type\":\"double\"},\"sum\":{\"type\":\"double\"},\"blockTime\":{\"type\":\"long\"},\"blockHeight\":{\"type\":\"long\"},\"blockIndex\":{\"type\":\"long\"},\"txFee\":{\"type\":\"double\"}}},\"m\":{\"type\":\"object\",\"properties\":{\"txId\":{\"type\":\"keyword\"},\"refundTxId\":{\"type\":\"keyword\"},\"withdrawTxId\":{\"type\":\"keyword\"},\"refundAmt\":{\"type\":\"double\"},\"addr\":{\"type\":\"keyword\"},\"amt\":{\"type\":\"double\"},\"sum\":{\"type\":\"double\"},\"blockTime\":{\"type\":\"long\"},\"blockHeight\":{\"type\":\"long\"},\"blockIndex\":{\"type\":\"long\"},\"txFee\":{\"type\":\"double\"}}},\"sendTime\":{\"type\":\"long\"},\"getTime\":{\"type\":\"long\"},\"state\":{\"type\":\"keyword\"},\"error\":{\"type\":\"text\"}}}}";
    public static final String  swapPriceMappingJsonStr = "{\"mappings\":{\"properties\":{\"id\":{\"type\":\"keyword\"},\"sid\":{\"type\":\"keyword\"},\"gTick\":{\"type\":\"keyword\"},\"mTick\":{\"type\":\"keyword\"},\"gAmt\":{\"type\":\"double\"},\"mAmt\":{\"type\":\"double\"},\"price\":{\"type\":\"double\"},\"time\":{\"type\":\"long\"}}}}";
    public Indices(String sid,ElasticsearchClient esClient, BufferedReader br){
        this.br = br;
        this.esClient = esClient;
        this.sid = sid;
    }

    public void menu() throws IOException, InterruptedException {

        Menu menu = new Menu();

        ArrayList<String> menuItemList = new ArrayList<>();
        menuItemList.add("List All Indices in ES");
        menuItemList.add("Recreate Order index");
        menuItemList.add("Recreate Balance Backup index");
        menuItemList.add("Recreate Reward index");
        menuItemList.add("Recreate Webhook index");
        menuItemList.add("Recreate Order, Balance and Reward indices");
        menuItemList.add("Recreate All Swap indices");

        menu.add(menuItemList);
        while(true) {
            menu.show();
            int choice = menu.choose(br);
            switch (choice) {
                case 1 -> listIndices(br);
                case 2 -> recreateApipIndex(sid,br, esClient, ORDER, orderMappingJsonStr);
                case 3 -> recreateApipIndex(sid,br, esClient, BALANCE, balanceMappingJsonStr);
                case 4 -> recreateApipIndex(sid,br, esClient, REWARD, rewardMappingJsonStr);
                case 5 -> recreateApipIndex(sid,br, esClient, WEBHOOK, webhookMappingJsonStr);
                case 6 -> recreateAllApipIndex(br, esClient);
                case 7 -> recreateAllSwapIndex(br, esClient);
                case 0 -> {
                    return;
                }
            }
        }
    }

    public void recreateAllSwapIndex(BufferedReader br, ElasticsearchClient esClient) throws IOException, InterruptedException {
        recreateSwapIndex(br, esClient, SWAP_STATE, swapStateJsonStr);
        recreateSwapIndex(br, esClient, SWAP_LP, swapLpMappingJsonStr);
        recreateSwapIndex(br, esClient,SWAP_FINISHED, swapFinishedMappingJsonStr);
        recreateSwapIndex(br, esClient,SWAP_PENDING, swapPendingMappingJsonStr);
        recreateSwapIndex(br, esClient,SWAP_PRICE, swapPriceMappingJsonStr);
    }

    private void recreateAllApipIndex(BufferedReader br, ElasticsearchClient esClient) throws IOException, InterruptedException {
        recreateApipIndex(sid,br, esClient, ORDER, orderMappingJsonStr);
        recreateApipIndex(sid,br, esClient, BALANCE, balanceMappingJsonStr);
        recreateApipIndex(sid,br, esClient,REWARD, rewardMappingJsonStr);
        recreateApipIndex(sid,br, esClient,WEBHOOK, webhookMappingJsonStr);
    }

    public static void recreateApipIndex(String sid,BufferedReader br, ElasticsearchClient esClient, String indexName, String mappingJsonStr) {
        String index = addSidBriefToName(sid,indexName);
        try {
            recreateIndex(index, esClient,mappingJsonStr);
        } catch (InterruptedException e) {
            log.debug("Recreate index {} wrong.",index);
        }
        Menu.anyKeyToContinue(br);
    }

    public static void recreateSwapIndex(BufferedReader br, ElasticsearchClient esClient, String index, String mappingJsonStr) {
        try {
            recreateIndex(index, esClient,mappingJsonStr);
        } catch (InterruptedException e) {
            log.debug("Recreate index {} wrong.",index);
        }
    }
    private static void listIndices(BufferedReader br) throws IOException {
        for (IndicesNames.Indices index : IndicesNames.Indices.values()) {
            System.out.println(index.sn()+". "+index.name().toLowerCase());
        }
        br.readLine();
    }

    public static void recreateIndex(String index, ElasticsearchClient esClient, String mappingJsonStr) throws InterruptedException {

        if(esClient==null) {
            System.out.println("Create a Java client for ES first.");
            return;
        }
        try {
            DeleteIndexResponse req = esClient.indices().delete(c -> c.index(index));

            if(req.acknowledged()) {
                log.debug("Index {} was deleted.", index);
            }
        }catch(ElasticsearchException | IOException e) {
            log.debug("Deleting index {} failed.", index,e);
        }

        TimeUnit.SECONDS.sleep(2);

        createIndex(index,esClient,mappingJsonStr);
    }

    public void checkApipIndices() throws IOException {

        String orderIndex = addSidBriefToName(sid,ORDER);
        if ( noSuchIndex(esClient, orderIndex)) {
            createIndex(orderIndex,esClient,orderMappingJsonStr);
        }

        String balanceIndex = addSidBriefToName(sid, BALANCE);
        if (noSuchIndex(esClient, balanceIndex)) {
            createIndex(balanceIndex,esClient,balanceMappingJsonStr);
        }

        String rewardIndex = addSidBriefToName(sid,REWARD);
        if (noSuchIndex(esClient, rewardIndex)) {
            createIndex(rewardIndex,esClient,rewardMappingJsonStr);
        }

        String webhookIndex = addSidBriefToName(sid, WEBHOOK);
        if (noSuchIndex(esClient, webhookIndex)) {
            createIndex(webhookIndex,esClient,webhookMappingJsonStr);
        }
    }

    public void checkSwapIndices() throws IOException {
        try {
            if ( noSuchIndex(esClient, SWAP_STATE)) {
                createIndex(SWAP_STATE,esClient,swapStateJsonStr);
            }

            if (noSuchIndex(esClient, SWAP_LP)) {
                createIndex(SWAP_LP,esClient,swapLpMappingJsonStr);
            }

            if (noSuchIndex(esClient, SWAP_FINISHED)) {
                createIndex(SWAP_FINISHED,esClient,swapFinishedMappingJsonStr);
            }

            if (noSuchIndex(esClient, SWAP_PENDING)) {
                createIndex(SWAP_PENDING,esClient,swapPendingMappingJsonStr);
            }

            if (noSuchIndex(esClient, SWAP_PRICE)) {
                createIndex(SWAP_PRICE,esClient,swapPriceMappingJsonStr);
            }
        }catch(ElasticsearchException | IOException e) {
            log.debug("Failed to check swap indices: {}",e.getMessage());
        }
    }

    private static void createIndex(String index, ElasticsearchClient esClient, String mappingJsonStr) {

        InputStream orderJsonStrIs = new ByteArrayInputStream(mappingJsonStr.getBytes());
        try {
            CreateIndexResponse req = esClient.indices().create(c -> c.index(index).withJson(orderJsonStrIs));
            orderJsonStrIs.close();
            System.out.println(req.toString());
            if(req.acknowledged()) {
                log.debug("Index {} was created.", index);
            }else {
                log.debug("Creating index {} failed.", index);
            }
        }catch(ElasticsearchException | IOException e) {
            log.debug("Creating index {} failed.", index,e);
        }
    }
    private static boolean noSuchIndex(ElasticsearchClient esClient, String index) throws IOException {
        BooleanResponse result;
        result = esClient.indices().exists(e -> e.index(index));
        return !result.value();
    }
}
