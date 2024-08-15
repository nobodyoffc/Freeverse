package NasaRcpTest;

import nasa.RpcRequest;
import nasa.data.TransactionBrief;
import com.google.gson.Gson;
import javaTools.JsonTools;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class GetBalance {
    public static final String GETBALANCE = "getbalance";
    private GetBalanceParams params;
    private double result;

    @Test
    public void test() {
//        String url = "http://127.0.0.1:22555";
        String url = "http://127.0.0.1:8332";
        result = getBalance("1", true, url, "username", "password");
        JsonTools.printJson(result);
    }

    public double getBalance(String minConf, boolean includeWatchOnly, String url, String username, String password) {
        GetBalanceParams params = new GetBalanceParams("*", minConf, includeWatchOnly);
        RpcRequest jsonRPC2Request = new RpcRequest(GETBALANCE, params.toParams());
        Object result = RpcRequest.requestRpc(url, username, password, "listSinceBlock", jsonRPC2Request);
        if (result == null) throw new RuntimeException("Getting balance for RPC wrong.");
        return (double) result;
    }

    public GetBalanceParams getParams() {
        return params;
    }

    public void setParams(GetBalanceParams params) {
        this.params = params;
    }

    public static class GetBalanceParams {
        private String block;
        private String minconf;
        private boolean includeWatchOnly = false;

        public GetBalanceParams(String block) {
            this.block = block;
        }

        public GetBalanceParams(String block, String minconf, boolean includeWatchOnly) {
            this.block = block;
            this.minconf = minconf;
            this.includeWatchOnly = includeWatchOnly;
        }

        public Object[] toParams() {
            List<Object> objects = new ArrayList<>();
            objects.add("*");
            if (minconf != null) {
                objects.add(Long.parseLong(minconf));
                if (includeWatchOnly) objects.add(includeWatchOnly);
            }

            Object[] params = objects.toArray();
            JsonTools.printJson(params);
            return params;
        }

        public String toJson() {
            return new Gson().toJson(toParams());
        }

        public String getBlock() {
            return block;
        }

        public void setBlock(String block) {
            this.block = block;
        }

        public String getMinconf() {
            return minconf;
        }

        public void setMinconf(String minconf) {
            this.minconf = minconf;
        }

        public boolean isIncludeWatchOnly() {
            return includeWatchOnly;
        }

        public void setIncludeWatchOnly(boolean includeWatchOnly) {
            this.includeWatchOnly = includeWatchOnly;
        }
    }

    public static class ListSinceBlockResult {
        private TransactionBrief[] transactions;
        private String lastblock;

        public TransactionBrief[] getTransactions() {
            return transactions;
        }

        public void setTransactions(TransactionBrief[] transactions) {
            this.transactions = transactions;
        }

        public String getLastblock() {
            return lastblock;
        }

        public void setLastblock(String lastblock) {
            this.lastblock = lastblock;
        }
    }

}
