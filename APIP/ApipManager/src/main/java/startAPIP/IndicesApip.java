package startAPIP;

import appTools.Menu;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.elasticsearch.indices.DeleteIndexResponse;
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
import static startAPIP.StartApipManager.getNameOfService;

public class IndicesApip {
    private static final Logger log = LoggerFactory.getLogger(IndicesApip.class);
    private final ElasticsearchClient esClient;
    private final BufferedReader br;
    public static  final  String  orderMappingJsonStr = "{\"mappings\":{\"properties\":{\"amount\":{\"type\":\"long\"},\"orderId\":{\"type\":\"keyword\"},\"fromFid\":{\"type\":\"wildcard\"},\"height\":{\"type\":\"long\"},\"time\":{\"type\":\"long\"},\"toFid\":{\"type\":\"wildcard\"},\"txId\":{\"type\":\"keyword\"},\"txIndex\":{\"type\":\"long\"},\"txid\":{\"type\":\"keyword\"},\"via\":{\"type\":\"wildcard\"}}}}";
    public static  final  String  balanceMappingJsonStr = "{\"mappings\":{\"properties\":{\"user\":{\"type\":\"text\"},\"consumeVia\":{\"type\":\"text\"},\"orderVia\":{\"type\":\"text\"},\"bestHeight\":{\"type\":\"keyword\"}}}}";
    public static final String  rewardMappingJsonStr = "{\"mappings\":{\"properties\":{\"rewardId\":{\"type\":\"keyword\"},\"rewardT\":{\"type\":\"long\"},\"time\":{\"type\":\"long\"},\"txId\":{\"type\":\"keyword\"},\"state\":{\"type\":\"keyword\"},\"bestHeight\":{\"type\":\"keyword\"},\"builderList\":{\"type\":\"nested\",\"properties\":{\"fid\":{\"type\":\"keyword\"},\"share\":{\"type\":\"float\"},\"amount\":{\"type\":\"long\"},\"fixed\":{\"type\":\"long\"}}},\"orderViaList\":{\"type\":\"nested\",\"properties\":{\"fid\":{\"type\":\"keyword\"},\"share\":{\"type\":\"float\"},\"amount\":{\"type\":\"long\"},\"fixed\":{\"type\":\"long\"}}},\"consumeViaList\":{\"type\":\"nested\",\"properties\":{\"fid\":{\"type\":\"keyword\"},\"share\":{\"type\":\"float\"},\"amount\":{\"type\":\"long\"},\"fixed\":{\"type\":\"long\"}}},\"costList\":{\"type\":\"nested\",\"properties\":{\"fid\":{\"type\":\"keyword\"},\"share\":{\"type\":\"float\"},\"amount\":{\"type\":\"long\"},\"fixed\":{\"type\":\"long\"}}}}}}";
    public static final String  webhookMappingJsonStr = "{\"mappings\":{\"properties\":{\"hookId\":{\"type\":\"keyword\"},\"owner\":{\"type\":\"wildcard\"},\"endpoint\":{\"type\":\"text\"},\"data\":{\"type\":\"object\"},\"method\":{\"type\":\"wildcard\"},\"op\":{\"type\":\"keyword\"}}}}";
    public static final String  swapStateJsonStr = "{\"mappings\":{\"properties\":{\"sid\":{\"type\":\"keyword\"},\"gSum\":{\"type\":\"double\"},\"mSum\":{\"type\":\"double\"},\"gBestBlockId\":{\"type\":\"keyword\"},\"gBestHeight\":{\"type\":\"long\"},\"mBestBlockId\":{\"type\":\"keyword\"},\"mBestHeight\":{\"type\":\"long\"},\"lastTime\":{\"type\":\"long\"},\"lastSn\":{\"type\":\"long\"},\"lastId\":{\"type\":\"keyword\"},\"gPendingSum\":{\"type\":\"double\"},\"mPendingSum\":{\"type\":\"double\"}}}}";
    public static final String  swapPendingMappingJsonStr = "{\"mappings\":{\"properties\":{\"sid\":{\"type\":\"keyword\"},\"pendingList\":{\"type\":\"nested\",\"properties\":{\"id\":{\"type\":\"keyword\"},\"sid\":{\"type\":\"keyword\"},\"sn\":{\"type\":\"long\"},\"act\":{\"type\":\"keyword\"},\"g\":{\"type\":\"object\",\"properties\":{\"txId\":{\"type\":\"keyword\"},\"refundTxId\":{\"type\":\"keyword\"},\"withdrawTxId\":{\"type\":\"keyword\"},\"refundAmt\":{\"type\":\"double\"},\"addr\":{\"type\":\"keyword\"},\"amt\":{\"type\":\"double\"},\"sum\":{\"type\":\"double\"},\"blockTime\":{\"type\":\"long\"},\"blockHeight\":{\"type\":\"long\"},\"blockIndex\":{\"type\":\"long\"},\"txFee\":{\"type\":\"double\"}}},\"m\":{\"type\":\"object\",\"properties\":{\"txId\":{\"type\":\"keyword\"},\"refundTxId\":{\"type\":\"keyword\"},\"withdrawTxId\":{\"type\":\"keyword\"},\"refundAmt\":{\"type\":\"double\"},\"addr\":{\"type\":\"keyword\"},\"amt\":{\"type\":\"double\"},\"sum\":{\"type\":\"double\"},\"blockTime\":{\"type\":\"long\"},\"blockHeight\":{\"type\":\"long\"},\"blockIndex\":{\"type\":\"long\"},\"txFee\":{\"type\":\"double\"}}},\"sendTime\":{\"type\":\"long\"},\"getTime\":{\"type\":\"long\"},\"state\":{\"type\":\"keyword\"},\"error\":{\"type\":\"text\"}}}}}}";
    public static final String  swapLpMappingJsonStr = "{\"mappings\":{\"properties\":{\"sid\":{\"type\":\"keyword\"},\"gLpRawMap\":{\"type\":\"object\",\"properties\":{\"key\":{\"type\":\"keyword\"},\"value\":{\"type\":\"double\"}}},\"gLpNetMap\":{\"type\":\"object\",\"properties\":{\"key\":{\"type\":\"keyword\"},\"value\":{\"type\":\"double\"}}},\"gLpShareMap\":{\"type\":\"object\",\"properties\":{\"key\":{\"type\":\"keyword\"},\"value\":{\"type\":\"double\"}}},\"mLpRawMap\":{\"type\":\"object\",\"properties\":{\"key\":{\"type\":\"keyword\"},\"value\":{\"type\":\"double\"}}},\"mLpNetMap\":{\"type\":\"object\",\"properties\":{\"key\":{\"type\":\"keyword\"},\"value\":{\"type\":\"double\"}}},\"mLpShareMap\":{\"type\":\"object\",\"properties\":{\"key\":{\"type\":\"keyword\"},\"value\":{\"type\":\"double\"}}},\"gLpRawSum\":{\"type\":\"double\"},\"mLpRawSum\":{\"type\":\"double\"},\"gServiceFee\":{\"type\":\"double\"},\"mServiceFee\":{\"type\":\"double\"}}}}";
    public static final String  swapFinishedMappingJsonStr = "{\"mappings\":{\"properties\":{\"id\":{\"type\":\"keyword\"},\"sid\":{\"type\":\"keyword\"},\"sn\":{\"type\":\"long\"},\"act\":{\"type\":\"keyword\"},\"g\":{\"type\":\"object\",\"properties\":{\"txId\":{\"type\":\"keyword\"},\"refundTxId\":{\"type\":\"keyword\"},\"withdrawTxId\":{\"type\":\"keyword\"},\"refundAmt\":{\"type\":\"double\"},\"addr\":{\"type\":\"keyword\"},\"amt\":{\"type\":\"double\"},\"sum\":{\"type\":\"double\"},\"blockTime\":{\"type\":\"long\"},\"blockHeight\":{\"type\":\"long\"},\"blockIndex\":{\"type\":\"long\"},\"txFee\":{\"type\":\"double\"}}},\"m\":{\"type\":\"object\",\"properties\":{\"txId\":{\"type\":\"keyword\"},\"refundTxId\":{\"type\":\"keyword\"},\"withdrawTxId\":{\"type\":\"keyword\"},\"refundAmt\":{\"type\":\"double\"},\"addr\":{\"type\":\"keyword\"},\"amt\":{\"type\":\"double\"},\"sum\":{\"type\":\"double\"},\"blockTime\":{\"type\":\"long\"},\"blockHeight\":{\"type\":\"long\"},\"blockIndex\":{\"type\":\"long\"},\"txFee\":{\"type\":\"double\"}}},\"sendTime\":{\"type\":\"long\"},\"getTime\":{\"type\":\"long\"},\"state\":{\"type\":\"keyword\"},\"error\":{\"type\":\"text\"}}}}";
    public static final String  swapPriceMappingJsonStr = "{\"mappings\":{\"properties\":{\"id\":{\"type\":\"keyword\"},\"sid\":{\"type\":\"keyword\"},\"gTick\":{\"type\":\"keyword\"},\"mTick\":{\"type\":\"keyword\"},\"gAmt\":{\"type\":\"double\"},\"mAmt\":{\"type\":\"double\"},\"price\":{\"type\":\"double\"},\"time\":{\"type\":\"long\"}}}}";
    public IndicesApip(ElasticsearchClient esClient, BufferedReader br){
        this.br = br;
        this.esClient = esClient;
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
                case 2 -> recreateApipIndex(br, esClient, ORDER, orderMappingJsonStr);
                case 3 -> recreateApipIndex(br, esClient, BALANCE, balanceMappingJsonStr);
                case 4 -> recreateApipIndex(br, esClient, REWARD, rewardMappingJsonStr);
                case 5 -> recreateApipIndex(br, esClient, WEBHOOK, webhookMappingJsonStr);
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
        recreateApipIndex(br, esClient, ORDER, orderMappingJsonStr);
        recreateApipIndex(br, esClient, BALANCE, balanceMappingJsonStr);
        recreateApipIndex(br, esClient,REWARD, rewardMappingJsonStr);
        recreateApipIndex(br, esClient,WEBHOOK, webhookMappingJsonStr);
    }

    public static void recreateApipIndex(BufferedReader br, ElasticsearchClient esClient, String indexName, String mappingJsonStr) {
        String index = getNameOfService(indexName);
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
}
