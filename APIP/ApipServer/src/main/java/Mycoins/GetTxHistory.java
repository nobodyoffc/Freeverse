package Mycoins;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import constants.ApipApiNames;
import data.fchData.Cash;
import data.feipData.ServiceType;
import managers.CashManager;
import managers.CashManager.SearchResult;
import managers.Manager.ManagerType;
import managers.MempoolManager;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.*;

@WebServlet(name = ApipApiNames.GET_TX_HISTORY + ApipApiNames.MYCOINS, value = ApipApiNames.MycoinsPath + ApipApiNames.GET_TX_HISTORY)
public class GetTxHistory extends HttpServlet {

    private final CommonApiBase apiBase = new CommonApiBase() {
        @Override
        protected Object doApiRequest(Map<String, String> params) throws Exception {
            System.out.println("[GetTxHistory] doApiRequest called, params=" + params);
            String coin = params.getOrDefault("coin", "FCH");
            String address = params.get("address");
            if (address == null || address.isEmpty()) {
                throw new Exception("Missing 'address' parameter");
            }

            if ("FCH".equalsIgnoreCase(coin)) {
                return getTxHistoryFch(address);
            } else {
                throw new Exception("Coin '" + coin + "' is not supported by this server.");
            }
        }
    };

    private List<Map<String, Object>> getTxHistoryFch(String address) {
        System.out.println("[GetTxHistory] getTxHistoryFch called for " + address);
        try {
            ElasticsearchClient esClient = (ElasticsearchClient) apiBase.settings.getClient(ServiceType.ES);
            MempoolManager mempoolHandler = (MempoolManager) apiBase.settings.getManager(ManagerType.MEMPOOL);

            SearchResult<Cash> searchResult = CashManager.getValidCashes(address, null, null, 0L, 0, 0, null, esClient, mempoolHandler);

            Map<String, Map<String, Object>> txMap = new LinkedHashMap<>();
            if (searchResult != null && searchResult.getData() != null) {
                for (Cash cash : searchResult.getData()) {
                    String txid = cash.getBirthTxId();
                    Map<String, Object> tx = txMap.get(txid);
                    if (tx == null) {
                        tx = new HashMap<>();
                        tx.put("txid", txid);
                        tx.put("value", 0L);
                        tx.put("direction", "in");
                        tx.put("time", cash.getBirthTime());
                        tx.put("height", cash.getBirthHeight());
                        txMap.put(txid, tx);
                    }
                    if (address.equals(cash.getOwner())) {
                        tx.put("value", (Long) tx.get("value") + cash.getValue());
                    }
                }
            }

            List<Map<String, Object>> result = new ArrayList<>(txMap.values());
            System.out.println("[GetTxHistory] Found " + result.size() + " transactions");
            return result;
        } catch (Exception e) {
            System.out.println("[GetTxHistory] ERROR: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        System.out.println("[GetTxHistory] doPost called");
        try {
            apiBase.handleEncryptedPost(request, response, ApipApiNames.GET_TX_HISTORY);
        } catch (Exception e) {
            System.out.println("[GetTxHistory] ERROR in doPost: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
