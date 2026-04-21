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

@WebServlet(name = ApipApiNames.GET_BALANCE + ApipApiNames.MYCOINS, value = ApipApiNames.MycoinsPath + ApipApiNames.GET_BALANCE)
public class GetBalance extends HttpServlet {

    private final CommonApiBase apiBase = new CommonApiBase() {
        @Override
        protected Object doApiRequest(Map<String, String> params) throws Exception {
            System.out.println("[GetBalance] doApiRequest called, params=" + params);
            String coin = params.getOrDefault("coin", "FCH");
            String address = params.get("address");
            if (address == null || address.isEmpty()) {
                throw new Exception("Missing 'address' parameter");
            }
            System.out.println("[GetBalance] coin=" + coin + ", address=" + address);

            if ("FCH".equalsIgnoreCase(coin)) {
                return getBalanceFch(address);
            } else {
                throw new Exception("Coin '" + coin + "' is not supported by this server.");
            }
        }
    };

    private Map<String, Object> getBalanceFch(String address) {
        System.out.println("[GetBalance] getBalanceFch called for " + address);
        try {
            ElasticsearchClient esClient = (ElasticsearchClient) apiBase.settings.getClient(ServiceType.ES);
            System.out.println("[GetBalance] esClient=" + (esClient != null ? "OK" : "NULL"));
            MempoolManager mempoolHandler = (MempoolManager) apiBase.settings.getManager(ManagerType.MEMPOOL);
            System.out.println("[GetBalance] mempoolHandler=" + (mempoolHandler != null ? "OK" : "NULL"));

            SearchResult<Cash> searchResult = CashManager.getValidCashes(address, null, null, 0L, 0, 0, null, esClient, mempoolHandler);
            System.out.println("[GetBalance] searchResult=" + (searchResult != null ? "got " + (searchResult.getData() != null ? searchResult.getData().size() : 0) + " cashes" : "NULL"));

            long balance = 0;
            if (searchResult != null && searchResult.getData() != null) {
                for (Cash cash : searchResult.getData()) {
                    balance += cash.getValue();
                }
            }
            System.out.println("[GetBalance] balance=" + balance);

            Map<String, Object> result = new HashMap<>();
            result.put("balance", balance);
            return result;
        } catch (Exception e) {
            System.out.println("[GetBalance] ERROR in getBalanceFch: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        System.out.println("[GetBalance] doPost called");
        try {
            apiBase.handleEncryptedPost(request, response, ApipApiNames.GET_BALANCE);
        } catch (Exception e) {
            System.out.println("[GetBalance] ERROR in doPost: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
