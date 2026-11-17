package Mycoins;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import constants.ApipApiNames;
import constants.CodeMessage;
import constants.FieldNames;
import data.fchData.Cash;
import data.feipData.ServiceType;
import managers.CashManager;
import managers.CashManager.SearchResult;
import managers.Manager.ManagerType;
import managers.MempoolManager;
import utils.FchUtils;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
                return getUtxosFch(address, params);
            } else {
                throw new Exception("Coin '" + coin + "' is not supported by this server.");
            }
        }
    };

    /** Outputs assumed for the fee when the client does not say: the payment and the change. */
    static final int DEFAULT_OUTPUT_SIZE = 2;

    /**
     * Optional params:
     *  amount     - FCH to spend. Returns enough cashes to cover it plus the fee, largest first
     *               when needed, at most CashManager.MAX_INPUT_SIZE inputs.
     *  outputSize - outputs of the tx being built, for the fee (default 2). With amount only.
     *  msgSize    - OP_RETURN bytes of the tx, for the fee (default 0). With amount only.
     *  size       - page size without amount (default 1000, max 5000).
     *  after      - the previous reply's "last", to get the next page.
     * The reply carries got, total and, when more cashes follow, last.
     */
    private Object getUtxosFch(String address, Map<String, String> params) throws Exception {
        ElasticsearchClient esClient = (ElasticsearchClient) apiBase.settings.getClient(ServiceType.ES);
        MempoolManager mempoolHandler = (MempoolManager) apiBase.settings.getManager(ManagerType.MEMPOOL);

        SearchResult<Cash> searchResult;
        String amountStr = params.get(FieldNames.AMOUNT);
        if (amountStr != null && !amountStr.isBlank()) {
            long amount;
            int outputSize;
            int msgSize;
            try {
                Long sat = FchUtils.coinStrToSatoshi(amountStr.trim());
                if (sat == null || sat < 0) throw new IllegalArgumentException("amount must be a non-negative FCH number");
                amount = sat;
                outputSize = intParam(params, FieldNames.OUTPUT_SIZE, DEFAULT_OUTPUT_SIZE);
                msgSize = intParam(params, FieldNames.MSG_SIZE, 0);
            } catch (Exception e) {
                throw new CommonApiBase.ApiException(CodeMessage.Code1013BadRequest, "Bad amount, outputSize or msgSize: " + e.getMessage());
            }
            searchResult = CashManager.getValidCashes(address, amount, null, null, outputSize, msgSize, null, esClient, mempoolHandler);
            if (searchResult.hasError() && searchResult.getCode() != CodeMessage.Code2007CashNoFound) {
                int code = searchResult.getCode() == 0 ? CodeMessage.Code1020OtherError : searchResult.getCode();
                throw new CommonApiBase.ApiException(code, searchResult.getMessage());
            }
        } else {
            int size;
            try {
                size = intParam(params, "size", CashManager.DEFAULT_CASH_PAGE_SIZE);
            } catch (Exception e) {
                throw new CommonApiBase.ApiException(CodeMessage.Code1013BadRequest, "Bad size: " + e.getMessage());
            }
            List<String> after = CommonApiBase.parseCursor(params.get("after"));
            searchResult = CashManager.getValidCashPage(address, null, size, after, esClient, mempoolHandler);
            if (searchResult.hasError()) {
                int code = searchResult.getCode() == 0 ? CodeMessage.Code1020OtherError : searchResult.getCode();
                throw new CommonApiBase.ApiException(code, searchResult.getMessage());
            }
        }

        // No cash: an empty list, as before.
        List<Map<String, Object>> utxos = new ArrayList<>();
        if (searchResult.getData() != null) {
            for (Cash cash : searchResult.getData()) {
                Map<String, Object> utxo = new HashMap<>();
                utxo.put("txid", cash.getBirthTxId());
                utxo.put("vout", cash.getBirthIndex());
                utxo.put("value", cash.getValue());
                utxo.put("script", cash.getLockScript() != null ? cash.getLockScript() : "");
                utxos.add(utxo);
            }
        }
        Long total = searchResult.getTotal() == null ? (long) utxos.size() : searchResult.getTotal();
        return new CommonApiBase.Paged(utxos, (long) utxos.size(), total, searchResult.getLast());
    }

    static int intParam(Map<String, String> params, String name, int dflt) {
        String v = params.get(name);
        if (v == null || v.isBlank()) return dflt;
        return (int) Double.parseDouble(v.trim());
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        try {
            apiBase.handleEncryptedPost(request, response, ApipApiNames.GET_UTXOS);
        } catch (Exception e) {
            System.out.println("[GetUtxos] ERROR in doPost: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
