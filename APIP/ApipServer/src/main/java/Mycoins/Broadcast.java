package Mycoins;

import clients.NaSaClient.NaSaRpcClient;
import constants.ApipApiNames;
import data.feipData.ServiceType;
import utils.Hex;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.*;

@WebServlet(name = ApipApiNames.BROADCAST + ApipApiNames.MYCOINS, value = ApipApiNames.MycoinsPath + ApipApiNames.BROADCAST)
public class Broadcast extends HttpServlet {

    private final CommonApiBase apiBase = new CommonApiBase() {
        @Override
        protected Object doApiRequest(Map<String, String> params) throws Exception {
            System.out.println("[Broadcast] doApiRequest called, params=" + params);
            String coin = params.getOrDefault("coin", "FCH");
            String rawTx = params.get("rawTx");
            if (rawTx == null || rawTx.isEmpty()) {
                throw new Exception("Missing 'rawTx' parameter");
            }

            if ("FCH".equalsIgnoreCase(coin)) {
                return broadcastFch(rawTx);
            } else {
                throw new Exception("Coin '" + coin + "' is not supported by this server.");
            }
        }
    };

    private Map<String, Object> broadcastFch(String rawTx) throws Exception {
        System.out.println("[Broadcast] broadcastFch called, rawTx length=" + rawTx.length());
        try {
            NaSaRpcClient naSaRpcClient = (NaSaRpcClient) apiBase.settings.getClient(ServiceType.NASA_RPC);
            String result = naSaRpcClient.sendRawTransaction(rawTx);
            if (result.startsWith("\"")) result = result.substring(1);
            if (result.endsWith("\"")) result = result.substring(0, result.length() - 1);
            System.out.println("[Broadcast] RPC result: " + result);

            if (!Hex.isHexString(result)) {
                throw new Exception(result);
            }

            Map<String, Object> data = new HashMap<>();
            data.put("txid", result);
            return data;
        } catch (Exception e) {
            throw e;
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        System.out.println("[Broadcast] doPost called");
        try {
            apiBase.handleEncryptedPost(request, response, ApipApiNames.BROADCAST);
        } catch (Exception e) {
            System.out.println("[Broadcast] ERROR in doPost: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
