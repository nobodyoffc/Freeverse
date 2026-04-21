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

@WebServlet(name = ApipApiNames.GET_UTXOS + ApipApiNames.MYCOINS, value = ApipApiNames.MycoinsPath + ApipApiNames.GET_UTXOS)
public class GetUtxos extends HttpServlet {

    private final CommonApiBase apiBase = new CommonApiBase() {
        @Override
        protected Object doApiRequest(Map<String, String> params) throws Exception {
            System.out.println("[GetUtxos] doApiRequest called, params=" + params);
            String coin = params.getOrDefault("coin", "FCH");
            String address = params.get("address");
            if (address == null || address.isEmpty()) {
                throw new Exception("Missing 'address' parameter");
            }

            if ("FCH".equalsIgnoreCase(coin)) {
                return getUtxosFch(address);
            } else {
                throw new Exception("Coin '" + coin + "' is not supported by this server.");
            }
        }
    };

    private List<Map<String, Object>> getUtxosFch(String address) {
        System.out.println("[GetUtxos] getUtxosFch called for " + address);
        try {
            ElasticsearchClient esClient = (ElasticsearchClient) apiBase.settings.getClient(ServiceType.ES);
            MempoolManager mempoolHandler = (MempoolManager) apiBase.settings.getManager(ManagerType.MEMPOOL);

            SearchResult<Cash> searchResult = CashManager.getValidCashes(address, null, null, 0L, 0, 0, null, esClient, mempoolHandler);

            List<Map<String, Object>> utxos = new ArrayList<>();
            if (searchResult != null && searchResult.getData() != null) {
                for (Cash cash : searchResult.getData()) {
                    Map<String, Object> utxo = new HashMap<>();
                    utxo.put("txid", cash.getBirthTxId());
                    utxo.put("vout", cash.getBirthIndex());
                    utxo.put("value", cash.getValue());
                    utxo.put("script", cash.getLockScript() != null ? cash.getLockScript() : "");
                    utxos.add(utxo);
                }
            }
            System.out.println("[GetUtxos] Found " + utxos.size() + " UTXOs");
            return utxos;
        } catch (Exception e) {
            System.out.println("[GetUtxos] ERROR: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        System.out.println("[GetUtxos] doPost called");
        try {
            apiBase.handleEncryptedPost(request, response, ApipApiNames.GET_UTXOS);
        } catch (Exception e) {
            System.out.println("[GetUtxos] ERROR in doPost: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
