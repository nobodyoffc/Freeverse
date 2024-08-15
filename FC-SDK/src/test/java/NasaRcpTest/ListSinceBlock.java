package NasaRcpTest;

import nasa.RpcRequest;
import nasa.data.TransactionBrief;
import com.google.gson.Gson;
import javaTools.JsonTools;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class ListSinceBlock {
    public static final String LISTSINCEBLOCK = "listsinceblock";
    private ListSinceBlockParams params;
    private ListSinceBlockResult result;

    @Test
    public void test() {
//        String url = "http://127.0.0.1:22555";
        String url = "http://127.0.0.1:8332";
        result = listSinceBlock("0000000000000217bdaa4bb757a6dfeac279905dde7914b01946e4e3b14e1380", "30", true, url, "username", "password");
        JsonTools.printJson(result);
    }

    public ListSinceBlockResult listSinceBlock(String block, String minConf, boolean includeWatchOnly, String url, String username, String password) {
        ListSinceBlockParams listSinceBlockParams = new ListSinceBlockParams(block, minConf, includeWatchOnly);
        RpcRequest jsonRPC2Request = new RpcRequest(LISTSINCEBLOCK, listSinceBlockParams.toParams());

        JsonTools.printJson(jsonRPC2Request);

        Object result = RpcRequest.requestRpc(url, username, password, "listSinceBlock", jsonRPC2Request);
        ListSinceBlockResult listSinceBlockResult = getTransactions(result);
        if (listSinceBlockResult != null && listSinceBlockResult.getTransactions() != null) {
            listSinceBlockResult.getTransactions().removeIf(tx -> tx.getConfirmations() < Integer.parseInt(minConf));
        }
        return listSinceBlockResult;
    }
    /*
      FCH:
      {
      "account": "",
      "address": "FNizPfVrFBtnXZKC32etR7JYHEVzYowBf6",
      "category": "receive",
      "amount": 29.18000000,
      "label": "",
      "vout": 28,
      "confirmations": 1600372,
      "blockhash": "000000000000001010e15c5f8978873dd44316c003597a8e79fa5acc0525d43a",
      "blockindex": 1,
      "blocktime": 1604136065,
      "txid": "ffb4d8d445ccf2ea61b50d552ae3ff98e1c65717edebe69192c681410f9a48d3",
      "walletconflicts": [
      ],
      "time": 1604136065,
      "timereceived": 1697076604
    }

    DOGE:
    {
  "transactions": [
       {
      "account": "swap",
      "address": "DJgizn89UvzNAW2wYAgN4qot82H9hjmAHM",
      "category": "receive",
      "amount": 10.00000000,
      "label": "swap",
      "vout": 0,
      "confirmations": 7742,
      "blockhash": "76005fa9b83a3099605624349870d75fdd25ec30a92c36cb76aea2e270485c47",
      "blockindex": 44,
      "blocktime": 1702120522,
      "txid": "2c6fdf409cd4b1deb9a30e5f77dc2d0276122393d77f27a1d806aaee35c1cbc4",
      "walletconflicts": [
      ],
      "time": 1702120351,
      "timereceived": 1702120351,
      "bip125-replaceable": "no"
    },
    {
      "account": "",
      "address": "DJgizn89UvzNAW2wYAgN4qot82H9hjmAHM",
      "category": "send",
      "amount": -0.1,
      "label": "swap",
      "vout": 0.0,
      "fee": -2.66E-6,
      "confirmations": -4105.0,
      "trusted": false,
      "txid": "73ba6c535578d5043501c7079c50bb36c2ae4383f50c4ad35f778725e1147e12",
      "walletconflicts": [
        "644bb280a41650c78ed57c8d9b41d53d4308b829279abe48431b86fb0d6124db"
      ],
      "time": 1.702260381E9,
      "timereceived": 1.702260381E9,
      "bip125-replaceable": "unknown",
      "abandoned": false
    }],
  "lastblock": "6f36fc795e5202aa4adfcb03fe529f89ce3e6b0c14dc50f4622932156834711c"
}
     */

    private ListSinceBlockResult getTransactions(Object res) {
        Gson gson = new Gson();
        result = gson.fromJson(gson.toJson(res), ListSinceBlockResult.class);
        if (result != null) {
            return result;
        }
        return null;
    }

    public ListSinceBlockParams getParams() {
        return params;
    }

    public void setParams(ListSinceBlockParams params) {
        this.params = params;
    }

    public ListSinceBlockResult getResult() {
        return result;
    }

    public void setResult(ListSinceBlockResult result) {
        this.result = result;
    }

    public static class ListSinceBlockParams {
        private String block;
        private String minconf;
        private boolean includeWatchOnly = false;

        public ListSinceBlockParams(String block) {
            this.block = block;
        }

        public ListSinceBlockParams(String block, String minconf, boolean includeWatchOnly) {
            this.block = block;
            this.minconf = minconf;
            this.includeWatchOnly = includeWatchOnly;
        }

        public Object[] toParams() {
            List<Object> objects = new ArrayList<>();
            objects.add(block);
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
        private List<TransactionBrief> transactions;
        private String lastblock;

        public List<TransactionBrief> getTransactions() {
            return transactions;
        }

        public void setTransactions(List<TransactionBrief> transactions) {
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
