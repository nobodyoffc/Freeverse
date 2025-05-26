package handlers;

import data.fcData.FcEntity;
import core.fch.RawTxParser;
import data.fchData.Cash;
import data.fchData.Tx;
import data.fchData.TxHasInfo;
import data.feipData.Service.ServiceType;
import clients.NaSaClient.NaSaRpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import data.apipData.UnconfirmedInfo;
import config.Settings;
import clients.ApipClient;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import utils.FchUtils;

import static constants.Strings.LISTEN_PATH;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class MempoolHandler extends Handler<FcEntity>{
    private static final Logger log = LoggerFactory.getLogger(MempoolHandler.class);
    
    private final NaSaRpcClient nasaClient;
    private final ApipClient apipClient;
    private final ElasticsearchClient esClient;
    private final String listenPath;
    private final AtomicBoolean running;
    
    private final List<String> txIdList;
    private final List<Tx> txList;
    private final List<Cash> inCashList;
    private final List<Cash> outCashList;
    
    public MempoolHandler(NaSaRpcClient nasaClient, ApipClient apipClient, 
                         ElasticsearchClient esClient, String listenPath) {
        super(HandlerType.MEMPOOL);
        this.nasaClient = nasaClient;
        this.apipClient = apipClient;
        this.esClient = esClient;
        this.listenPath = listenPath;
        this.running = new AtomicBoolean(true);
        
        // Initialize lists
        this.txIdList = new ArrayList<>();
        this.txList = new ArrayList<>();
        this.inCashList = new ArrayList<>();
        this.outCashList = new ArrayList<>();
        
        // Start directory listener
        scanNewBlockToClearLists();
    }

    public MempoolHandler(Settings settings) {
        super(settings,HandlerType.MEMPOOL);
        this.nasaClient = (NaSaRpcClient) settings.getClient(ServiceType.NASA_RPC);
        this.apipClient = (ApipClient) settings.getClient(ServiceType.APIP);
        this.esClient = (ElasticsearchClient) settings.getClient(ServiceType.ES);
        this.listenPath = (String) settings.getSettingMap().get(LISTEN_PATH);
        this.running = new AtomicBoolean(true);
        
        this.txIdList = new ArrayList<>();
        this.txList = new ArrayList<>();
        this.inCashList = new ArrayList<>();
        this.outCashList = new ArrayList<>();
        
        // Start directory listener
        scanNewBlockToClearLists();
    }

    public void updateUnconfirmedValidCash(List<Cash> cashList, String fid) {
        List<Cash> addingList = null;
        List<String> removingIdList = null;
        if(fid!=null) {
            List<Cash> unconfirmedCashList = checkUnconfirmedCash(fid);
            if (unconfirmedCashList == null) return;
            addingList = unconfirmedCashList.stream()
                    .filter(Cash::isValid)
                    .toList();

            removingIdList = unconfirmedCashList.stream()
                    .filter(cash -> !cash.isValid())
                    .map(Cash::getId)
                    .toList();
        }else {
            removingIdList = inCashList.stream()
                    .map(Cash::getId)
                    .toList();
        }

        if(addingList!=null)cashList.addAll(addingList);
        
        if(removingIdList!=null){
            for(String id : removingIdList){
                cashList.removeIf(cash -> cash.getId().equals(id));
            }
        }  
    }

    private void scanNewBlockToClearLists() {
        Thread listenerThread = new Thread(() -> {
            while (running.get()) {
                FchUtils.waitForChangeInDirectory(listenPath, running);
                if (running.get()) {
                    clearLists();
                }
            }
        });
        listenerThread.setDaemon(true);
        listenerThread.start();
    }
    
    public void checkMempool() {
        try {
            // Get mempool transaction IDs
            String[] mempoolTxIds = nasaClient.getRawMempoolIds();
            if (mempoolTxIds == null || mempoolTxIds.length == 0) {
                return;
            }
            List<String> newMempoolTxIds = new ArrayList<>(Arrays.asList(mempoolTxIds));
            newMempoolTxIds.removeAll(txIdList);
            if(newMempoolTxIds.size() == 0){
                return;
            }
            // Process each transaction
            for (String txId : newMempoolTxIds) {
                try {
                    // Get raw transaction
                    String rawTxHex = nasaClient.getRawTx(txId);
                    if (rawTxHex == null) {
                        continue;
                    }
                    
                    // Parse transaction
                    TxHasInfo txInfo = RawTxParser.parseMempoolTx(rawTxHex, txId, apipClient, esClient);

                    // Add to lists
                    txIdList.add(txId);
                    txList.add(txInfo.getTx());
                    if (txInfo.getInCashList() != null) {
                        inCashList.addAll(txInfo.getInCashList());
                    }
                    if (txInfo.getOutCashList() != null) {
                        outCashList.addAll(txInfo.getOutCashList());
                    }
                    
                } catch (Exception e) {
                    log.error("Error processing mempool tx {}: {}", txId, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Error checking mempool: {}", e.getMessage());
        }
    }
    
    private void clearLists() {
        String[] mempoolTxIds = nasaClient.getRawMempoolIds();
        if(mempoolTxIds == null || mempoolTxIds.length == 0){
            txIdList.clear();
            txList.clear();
            inCashList.clear();
            outCashList.clear();
            return;
        }
        for(String txId : mempoolTxIds){
            txIdList.remove(txId);
            txList.removeIf(tx -> tx.getId().equals(txId));
            inCashList.removeIf(cash -> cash.getSpendTxId().equals(txId));
            outCashList.removeIf(cash -> cash.getBirthTxId().equals(txId));
        }
    }
    
    public void shutdown() {
        running.set(false);
    }
    
    // Getters for the lists
    public List<String> getTxIdList() {
        return new ArrayList<>(txIdList);
    }
    
    public List<Tx> getTxList() {
        return new ArrayList<>(txList);
    }
    
    public List<Cash> getInCashList() {
        return new ArrayList<>(inCashList);
    }
    
    public List<Cash> getOutCashList() {
        return new ArrayList<>(outCashList);
    }
    
    public List<Cash> checkUnconfirmedCash(String fid) {
        checkMempool();
        List<Cash> fidCashList = new ArrayList<>();
        
        for (Cash cash : inCashList) {
            if (cash.getOwner().equals(fid)) {
                fidCashList.add(cash);
            }
        }
        
        for (Cash cash : outCashList) {
            if (cash.getOwner().equals(fid)) {
                fidCashList.add(cash);
            }
        }
        
        return fidCashList;
    }
    
    public long checkUnconfirmedBalance(String fid) {
        checkMempool();
        long balance = 0;
        
        // Add values from outCashList where FID is owner (receiving)
        for (Cash cash : outCashList) {
            if (cash.getOwner().equals(fid)) {
                balance += cash.getValue();
            }
        }
        
        // Subtract values from inCashList where FID is owner (spending)
        for (Cash cash : inCashList) {
            if (cash.getOwner().equals(fid)) {
                balance -= cash.getValue();
            }
        }
        
        return balance;
    }
    
    public List<TxHasInfo> checkUnconfirmedTx(String fid) {
        checkMempool();
        List<TxHasInfo> txHasInfoList = new ArrayList<>();
        
        // Check each transaction
        for (Tx tx : txList) {
            boolean isRelevant = false;
            TxHasInfo txHasInfo = new TxHasInfo();
            ArrayList<Cash> relevantInCash = new ArrayList<>();
            ArrayList<Cash> relevantOutCash = new ArrayList<>();
            
            // Check inCashList
            for (Cash cash : inCashList) {
                if (cash.getSpendTxId().equals(tx.getId()) && cash.getOwner().equals(fid)) {
                    relevantInCash.add(cash);
                    isRelevant = true;
                }
            }
            
            // Check outCashList
            for (Cash cash : outCashList) {
                if (cash.getBirthTxId().equals(tx.getId()) && cash.getOwner().equals(fid)) {
                    relevantOutCash.add(cash);
                    isRelevant = true;
                }
            }
            
            // If transaction is relevant to the FID, add it to the result list
            if (isRelevant) {
                txHasInfo.setTx(tx);
                txHasInfo.setInCashList(relevantInCash);
                txHasInfo.setOutCashList(relevantOutCash);
                txHasInfoList.add(txHasInfo);
            }
        }
        
        return txHasInfoList;
    }

    public Map<String, UnconfirmedInfo> getUnconfirmedInfo(List<String> idList) {
        checkMempool();
        Map<String, UnconfirmedInfo> resultMap = new HashMap<>();
        for(String id : idList){
            UnconfirmedInfo unconfirmedInfo = new UnconfirmedInfo();
            Map<String,Long> txValueMap = new HashMap<>();
            int incomeCount = 0;
            int spendCount = 0;
            long incomeValue = 0;
            long spendValue = 0;
            long net = 0;

            List<Cash> fidCashList = checkUnconfirmedCash(id);
            for(Cash cash : fidCashList){
                if(cash.isValid()){
                    incomeCount++;
                    incomeValue += cash.getValue();
                    net += cash.getValue();
                    txValueMap.merge(cash.getBirthTxId(), cash.getValue(), Long::sum);
                }else{
                    spendCount++;
                    spendValue += cash.getValue();
                    net -= cash.getValue();
                    txValueMap.merge(cash.getSpendTxId(), -cash.getValue(), Long::sum);
                }
            }

            unconfirmedInfo.setFid(id);
            unconfirmedInfo.setIncomeCount(incomeCount);
            unconfirmedInfo.setIncomeValue(incomeValue);
            unconfirmedInfo.setSpendCount(spendCount);
            unconfirmedInfo.setSpendValue(spendValue);
            unconfirmedInfo.setNet(net);
            if(txValueMap.size() > 0){
                unconfirmedInfo.setTxValueMap(txValueMap);
            }
            resultMap.put(id, unconfirmedInfo);
        }
        return resultMap;
    }

    public Map<String, List<Cash>> checkUnconfirmedCash(List<String> fidList) {
        checkMempool();
        Map<String,List<Cash>> resultMap = new HashMap<>();


        for(String fid : fidList){
            List<Cash> fidCashList = new ArrayList<>();
            List<Cash> fidInCashList = inCashList.stream().filter(cash -> cash.getOwner().equals(fid)).collect(Collectors.toList());
            List<Cash> fidOutCashList = outCashList.stream().filter(cash -> cash.getOwner().equals(fid)).collect(Collectors.toList());
            fidCashList.addAll(fidInCashList);
            fidCashList.addAll(fidOutCashList);
            
            resultMap.put(fid, fidCashList);
        }
        return resultMap;
    }
} 