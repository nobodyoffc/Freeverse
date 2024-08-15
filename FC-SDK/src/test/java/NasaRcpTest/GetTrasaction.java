package NasaRcpTest;

import nasa.RpcRequest;
import nasa.data.TransactionBrief;
import nasa.data.TransactionRPC;
import com.google.gson.Gson;
import javaTools.JsonTools;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class GetTrasaction {
    public static final String GETTRANSACTION = "gettransaction";
    private TransactionParams params;
    private TransactionRPC result;

    @Test
    public void test() {
        String url = "http://127.0.0.1:22555";
        String txId = "9537b79cb72d3a3fb4378b3cc17494078c25c1d4a6991785a9c565599b6d4640";
//        String url = "http://127.0.0.1:8332";
//        String txId = "ba15e9eb90790bc97dcc6860468a9fb66968586384746b48b32f5d122e651d80";
        result = getTransaction(txId, true, url, "username", "password");
        JsonTools.printJson(result);
    }

    public TransactionRPC getTransaction(String txId, boolean includeWatchOnly, String url, String username, String password) {
        TransactionParams txParams = new TransactionParams(txId, includeWatchOnly);
        RpcRequest jsonRPC2Request = new RpcRequest(GETTRANSACTION, txParams.toParams());

        JsonTools.printJson(jsonRPC2Request);

        Object result = RpcRequest.requestRpc(url, username, password, "listSinceBlock", jsonRPC2Request);
        return getTransactions(result);
    }
    /*
      FCH:
{
  "amount": 10.00000000,
  "confirmations": 3313,
  "blockhash": "0000000000000531b2905a607cb35d87a8d0da03e7ec1f390e931c456a3176b0",
  "blockindex": 1,
  "blocktime": 1702805494,
  "txid": "ba15e9eb90790bc97dcc6860468a9fb66968586384746b48b32f5d122e651d80",
  "walletconflicts": [
  ],
  "time": 1702805443,
  "timereceived": 1702805443,
  "details": [
    {
      "involvesWatchonly": true,
      "account": "",
      "address": "FSpN4acDqFD9jYM6DoTVRjvTKU4DPXgQHx",
      "category": "receive",
      "amount": 10.00000000,
      "label": "",
      "vout": 0
    }
  ],
  "hex": "0200000002fe5c2b08d517855a0ffea1aa592dceccf3ce9399772c6ee96fd6b1e26c137414020000006441f23dbbfee548daa0a1cd0c846dae938518c88efb587eed7e9900b9ab6601a7cf53cf9a3afc7200e9a66520825c7947510f010b82374741802e13412d16d7e6f0412103c731d4db424e15920bf801e73a81a0e33c36391ab00c88ae95cc3e8fae5ed101fffffffffe5c2b08d517855a0ffea1aa592dceccf3ce9399772c6ee96fd6b1e26c13741400000000644121dcd76bf4f05e168515369fd84910574292579671a90e95b8bc8409c2d50e37c5382f8aea5fc682c1dbd940a5bdc1b70681dd75e241d4351e40450372e05bc9412103c731d4db424e15920bf801e73a81a0e33c36391ab00c88ae95cc3e8fae5ed101ffffffff0200ca9a3b000000001976a914e63669a411d4fddc3e4906fa76e0cc3cf945558688ace02e255f270000001976a9148b6ebb34f066bc0ec4f2d3e5d021a56ffc1ef71d88ac00000000"
}

    DOGE:
    {
  "amount": -5.00000000,
  "fee": -0.00226000,
  "confirmations": 2511,
  "blockhash": "ba53ecdf87377d3a007f633a4375aa0ce4f68653081aba43f0239545a6072650",
  "blockindex": 25,
  "blocktime": 1702871989,
  "txid": "9537b79cb72d3a3fb4378b3cc17494078c25c1d4a6991785a9c565599b6d4640",
  "walletconflicts": [
  ],
  "time": 1702871249,
  "timereceived": 1702871249,
  "bip125-replaceable": "no",
  "details": [
    {
      "involvesWatchonly": true,
      "account": "",
      "address": "DS8M937nHLtmeNef6hnu17ZXAwmVpM6TXY",
      "category": "send",
      "amount": -5.00000000,
      "label": "",
      "vout": 1,
      "fee": -0.00226000,
      "abandoned": false
    }
  ],
  "hex": "01000000012403e00e4c674884e26166a3f50f53d913d61027322c910a00de3b6afa8bfc1a000000006a473044022022ee811f6bed2dbb511bd4acc0630003623bea4375d41d7862bacbf068206b7102200f24d9b08a28ace915395b5f3dec6a05518c47781311b7fc3e1ee60575fadd5a012103d93516665542cb3646514ce94eb6c61d2e4a6abc35fdf510e77630d76a187589feffffff0270e3bc58000000001976a914232bf8c59c5d0ff483bd04ed4c06d883f1383fa888ac0065cd1d000000001976a914e63669a411d4fddc3e4906fa76e0cc3cf945558688ace1744c00"
}
     */

    private TransactionRPC getTransactions(Object res) {
        Gson gson = new Gson();
        result = gson.fromJson(gson.toJson(res), TransactionRPC.class);
        if (result != null) {
            return result;
        }
        return null;
    }

    public TransactionParams getParams() {
        return params;
    }

    public void setParams(TransactionParams params) {
        this.params = params;
    }

    public TransactionRPC getResult() {
        return result;
    }

    public void setResult(TransactionRPC result) {
        this.result = result;
    }

    public static class TransactionParams {
        private String txId;
        private boolean includeWatchOnly;

        public TransactionParams(String txId, boolean includeWatchOnly) {
            this.txId = txId;
            this.includeWatchOnly = includeWatchOnly;
        }

        public TransactionParams(String block, String minconf, boolean includeWatchOnly) {
            this.txId = block;
            this.includeWatchOnly = includeWatchOnly;
        }

        public Object[] toParams() {
            List<Object> objects = new ArrayList<>();
            objects.add(txId);
            if (includeWatchOnly) objects.add(includeWatchOnly);
            Object[] params = objects.toArray();
            JsonTools.printJson(params);
            return params;
        }

        public String toJson() {
            return new Gson().toJson(toParams());
        }

        public String getTxId() {
            return txId;
        }

        public void setTxId(String txId) {
            this.txId = txId;
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
