package Mycoins;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import constants.ApipApiNames;
import data.feipData.ServiceType;
import managers.CashManager;
import managers.Manager.ManagerType;
import managers.MempoolManager;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

    /**
     * Full balance of the address in satoshis: the confirmed balance from the Freer index (the
     * same number freerByIds reports), plus the net of its unconfirmed mempool cashes. This used
     * to sum only the first 200 valid cashes, which undercounted any address with more.
     */
    private Map<String, Object> getBalanceFch(String address) throws Exception {
        ElasticsearchClient esClient = (ElasticsearchClient) apiBase.settings.getClient(ServiceType.ES);
        MempoolManager mempoolHandler = (MempoolManager) apiBase.settings.getManager(ManagerType.MEMPOOL);

        Long confirmed = CashManager.confirmedBalance(esClient, address);
        if (confirmed == null) throw new Exception("Failed to read the balance of " + address);

        long unconfirmed = 0;
        if (mempoolHandler != null) {
            try {
                unconfirmed = mempoolHandler.checkUnconfirmedBalance(address);
            } catch (Exception e) {
                System.out.println("[GetBalance] mempool unavailable: " + e.getMessage());
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("balance", confirmed + unconfirmed);
        return result;
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
