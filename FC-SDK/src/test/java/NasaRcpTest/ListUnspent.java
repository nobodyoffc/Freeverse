package NasaRcpTest;

import nasa.RpcRequest;
import nasa.data.UTXO;
import com.google.gson.Gson;
import javaTools.JsonTools;
import javaTools.NumberTools;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ListUnspent {
    public static final String LISTUNSPENT = "listunspent";
    private ListUnspentParams params;
    private UTXO[] result;

    @Test
    public void test() {
        String urlDoge = "http://127.0.0.1:22555";
        String username = "username";
        String password = "password";
        String addrDoge = "DS8M937nHLtmeNef6hnu17ZXAwmVpM6TXY";
        String urlFch = "http://127.0.0.1:8332";
        String addrFch = "FSpN4acDqFD9jYM6DoTVRjvTKU4DPXgQHx";

        UTXO[] utxos2 = listUnspent(urlFch, username, password);
        JsonTools.printJson(utxos2);

        String minConf = "0";
        boolean includeUnsafe = true;
        UTXO[] utxos = listUnspent(addrFch, minConf, urlFch, username, password);
        JsonTools.printJson(utxos);

        //String minconf, String maxconf,String[]addrs , boolean includeUnsafe,String minimumAmount, String maximumAmount, String maximumCount, String minimumSumAmount
        String maxconf = "99999999";
        String[] addrs = {addrFch};

        String minimumAmount = null;
        String maximumAmount = null;
        String maximumCount = null;
        String minimumSumAmount = null;//"25";

        UTXO[] utxos1 = listUnspent(minConf, maxconf, addrs, includeUnsafe, minimumAmount, maximumAmount, maximumCount, minimumSumAmount, urlFch, username, password);
        JsonTools.printJson(utxos1);
    }

    public UTXO[] listUnspent(String url, String username, String password) {
        ListUnspentParams listUnspentParams = new ListUnspentParams(null, (String[]) null);
        RpcRequest jsonRPC2Request = new RpcRequest(LISTUNSPENT, listUnspentParams.toParams());
        Object result = RpcRequest.requestRpc(url, username, password, "listUnspent", jsonRPC2Request);
        return getUtxos(result);
    }

    public UTXO[] listUnspent(@Nullable String addr, @Nullable String minConf, String url, String username, String password) {
        ListUnspentParams listUnspentParams = new ListUnspentParams(minConf, new String[]{addr});
        RpcRequest jsonRPC2Request = new RpcRequest(LISTUNSPENT, listUnspentParams.toParams());
        Object result = RpcRequest.requestRpc(url, username, password, "listUnspent", jsonRPC2Request);
        return getUtxos(result);
    }

    public UTXO[] listUnspent(@Nullable String addr, @Nullable String minConf, boolean includeUnsafe, String url, String username, String password) {
        ListUnspentParams listUnspentParams = new ListUnspentParams(minConf, new String[]{addr}, includeUnsafe);
        RpcRequest jsonRPC2Request = new RpcRequest(LISTUNSPENT, listUnspentParams.toParams());
        Object result = RpcRequest.requestRpc(url, username, password, "listUnspent", jsonRPC2Request);
        return getUtxos(result);
    }

    /*
    class [Ldata.Utxo;: [
  {
    "txid": "1afc8bfa6a3bde000a912c322710d613d9530ff5a36661e28448674c0ee00324",
    "vout": 0,
    "address": "DJgizn89UvzNAW2wYAgN4qot82H9hjmAHM",
    "account": "swap",
    "scriptPubKey": "76a9149494f686025f04afe4c837139ea849b58ff3b99988ac",
    "amount": 19.89,
    "confirmations": 4,
    "spendable": true,
    "solvable": true
  }
]
     */

    public UTXO[] listUnspent(@Nullable String minConf,
                              @Nullable String maxconf,
                              @Nullable String[] addrs,
                              boolean includeUnsafe,
                              @Nullable String minimumAmount,
                              @Nullable String maximumAmount,
                              @Nullable String maximumCount,
                              @Nullable String minimumSumAmount,
                              String url,
                              String username,
                              String password) {
        ListUnspentParams listUnspentParams = new ListUnspentParams(minConf, maxconf, addrs, includeUnsafe, minimumAmount, maximumAmount, maximumCount, minimumSumAmount);
        RpcRequest jsonRPC2Request = new RpcRequest(LISTUNSPENT, listUnspentParams.toParams());
        Object result = RpcRequest.requestRpc(url, username, password, "listUnspent", jsonRPC2Request);
        return getUtxos(result);
    }

    private UTXO[] getUtxos(Object res) {
        Gson gson = new Gson();
        result = gson.fromJson(gson.toJson(res), UTXO[].class);
        return result;
    }

    public ListUnspentParams getParams() {
        return params;
    }

    public void setParams(ListUnspentParams params) {
        this.params = params;
    }

    public UTXO[] getResult() {
        return result;
    }

    public void setResult(UTXO[] result) {
        this.result = result;
    }

    public static class ListUnspentParams {
        private String minconf;
        private String maxconf;
        private String[] addresses;
        private boolean includeUnsafe = true;
        private String minimumAmount;
        private String maximumAmount;
        private String maximumCount;
        private String minimumSumAmount;
        private Map<String, Object> optionMap;

        public ListUnspentParams(String... addresses) {
            this.addresses = addresses;
        }

        public ListUnspentParams(String minconf, String... addresses) {
            this.minconf = minconf;
            this.addresses = addresses;
        }

        public ListUnspentParams(String minconf, String[] addresses, boolean includeUnsafe) {
            this.minconf = minconf;
            this.addresses = addresses;
            this.includeUnsafe = includeUnsafe;
        }

        public ListUnspentParams(String minconf, String maxconf, String[] addresses, boolean includeUnsafe, String minimumAmount, String maximumAmount, String maximumCount, String minimumSumAmount) {
            this.minconf = minconf;
            this.maxconf = maxconf;
            this.addresses = addresses;
            this.includeUnsafe = includeUnsafe;
            this.minimumAmount = minimumAmount;
            this.maximumAmount = maximumAmount;
            this.maximumCount = maximumCount;
            this.minimumSumAmount = minimumSumAmount;
        }

        public Object[] toParams() {
            List<Object> objects = new ArrayList<>();
            if (minconf != null) {
                objects.add(Long.parseLong(minconf));

                if (maxconf != null) objects.add(Long.parseLong(maxconf));
                else objects.add(999999999);

                if (addresses != null && addresses.length > 0) objects.add(addresses);
                else objects.add(new String[0]);

                objects.add(includeUnsafe);

                optionMap = new HashMap<>();
                if (minimumAmount != null)
                    optionMap.put("minimumAmount", NumberTools.roundDouble8(Double.valueOf(minimumAmount)));
                if (maximumAmount != null)
                    optionMap.put("maximumAmount", NumberTools.roundDouble8(Double.valueOf(maximumAmount)));
                if (maximumCount != null) optionMap.put("maximumCount", Long.parseLong(maximumCount));
                if (minimumSumAmount != null)
                    optionMap.put("minimumSumAmount", NumberTools.roundDouble8(Double.valueOf(minimumSumAmount)));
            }
            if (optionMap != null && !optionMap.isEmpty()) objects.add(optionMap);

            return objects.toArray();

//            {"jsonrpc": "1.0", "id":"curltest", "method": "listunspent", "params": [6, 9999999, [] , true, { "minimumAmount": 5 }
        }

        public String toJson() {
            return new Gson().toJson(toParams());
        }

        public String getMinconf() {
            return minconf;
        }

        public void setMinconf(String minconf) {
            this.minconf = minconf;
        }

        public String getMaxconf() {
            return maxconf;
        }

        public void setMaxconf(String maxconf) {
            this.maxconf = maxconf;
        }

        public String[] getAddresses() {
            return addresses;
        }

        public void setAddresses(String[] addresses) {
            this.addresses = addresses;
        }

        public boolean isIncludeUnsafe() {
            return includeUnsafe;
        }

        public void setIncludeUnsafe(boolean includeUnsafe) {
            this.includeUnsafe = includeUnsafe;
        }

        public String getMinimumAmount() {
            return minimumAmount;
        }

        public void setMinimumAmount(String minimumAmount) {
            this.minimumAmount = minimumAmount;
        }

        public String getMaximumAmount() {
            return maximumAmount;
        }

        public void setMaximumAmount(String maximumAmount) {
            this.maximumAmount = maximumAmount;
        }

        public String getMaximumCount() {
            return maximumCount;
        }

        public void setMaximumCount(String maximumCount) {
            this.maximumCount = maximumCount;
        }

        public String getMinimumSumAmount() {
            return minimumSumAmount;
        }

        public void setMinimumSumAmount(String minimumSumAmount) {
            this.minimumSumAmount = minimumSumAmount;
        }

        public Map<String, Object> getOptionMap() {
            return optionMap;
        }

        public void setOptionMap(Map<String, Object> optionMap) {
            this.optionMap = optionMap;
        }
    }

    public static class ListUnspentResult {
        private String txid;
        private int vout;
        private String address;
        private String account;
        private String scriptPubKey;
        private double amount;
        private int confirmations;
        private String redeemScript;
        private boolean spendable;
        private boolean solvable;

        // Constructor
        public ListUnspentResult(
                String txid,
                int vout,
                String address,
                String account,
                String scriptPubKey,
                double amount,
                int confirmations,
                String redeemScript,
                boolean spendable,
                boolean solvable) {
            this.txid = txid;
            this.vout = vout;
            this.address = address;
            this.account = account;
            this.scriptPubKey = scriptPubKey;
            this.amount = amount;
            this.confirmations = confirmations;
            this.redeemScript = redeemScript;
            this.spendable = spendable;
            this.solvable = solvable;
        }

        // Getters and setters (optional)
        // You can generate getters and setters for the private fields using your IDE.
        // For brevity, they are not included here.

        public ListUnspentResult[] parseResults(Object[] result) {
            Gson gson = new Gson();
            return gson.fromJson(gson.toJson(result), ListUnspentResult[].class);
        }

        public String getTxid() {
            return txid;
        }

        public void setTxid(String txid) {
            this.txid = txid;
        }

        public int getVout() {
            return vout;
        }

        public void setVout(int vout) {
            this.vout = vout;
        }

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public String getAccount() {
            return account;
        }

        public void setAccount(String account) {
            this.account = account;
        }

        public String getScriptPubKey() {
            return scriptPubKey;
        }

        public void setScriptPubKey(String scriptPubKey) {
            this.scriptPubKey = scriptPubKey;
        }

        public double getAmount() {
            return amount;
        }

        public void setAmount(double amount) {
            this.amount = amount;
        }

        public int getConfirmations() {
            return confirmations;
        }

        public void setConfirmations(int confirmations) {
            this.confirmations = confirmations;
        }

        public String getRedeemScript() {
            return redeemScript;
        }

        public void setRedeemScript(String redeemScript) {
            this.redeemScript = redeemScript;
        }

        public boolean isSpendable() {
            return spendable;
        }

        public void setSpendable(boolean spendable) {
            this.spendable = spendable;
        }

        public boolean isSolvable() {
            return solvable;
        }

        public void setSolvable(boolean solvable) {
            this.solvable = solvable;
        }
    }

}
