package NasaRcpTest;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import javaTools.JsonTools;
import nasa.RpcRequest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

public class GetRawMempool {
    public static final String GETRAWMEMPOOL = "getrawmempool";
    private Map<String, Transaction> transactions;
    private String[] txIds;

    @Test
    public void test() {
//        String txId = "bf9f366d8e207928c90c819e81276c69864dbd42466798f53d33ad974cb0fb6b";
//        params = txId;
        String url = "http://127.0.0.1:8332";
//        String url = "http://127.0.0.1:22555";

        getRawMempool(url, false,"username", "password");
        JsonTools.printJson(txIds);


        getRawMempool(url, true,"username", "password");
        JsonTools.printJson(transactions);
    }


    public Object getRawMempool(String url, boolean verbose,String username, String password) {
        RpcRequest jsonRPC2Request = new RpcRequest(GETRAWMEMPOOL, new Object[]{verbose});
        Object result = RpcRequest.requestRpc(url, username, password, GETRAWMEMPOOL, jsonRPC2Request);
        Gson gson = new Gson();
        if(verbose) {
            this.transactions = txsFromJson(result);
            return this.transactions ;
        }else{
            this.txIds = gson.fromJson(gson.toJson(result),String[].class);
            return this.txIds;
        }
    }
    public static Map<String, Transaction> txsFromJson(Object data) {
        Gson gson = new Gson();
        Type type = new TypeToken<Map<String, Transaction>>(){}.getType();
        return gson.fromJson(gson.toJson(data), type);
    }

    public static String getGetrawmempool() {
        return GETRAWMEMPOOL;
    }



    public String[] getTxIds() {
        return txIds;
    }

    public void setTxIds(String[] txIds) {
        this.txIds = txIds;
    }


    public static class Transaction {
        private long size;
        private double fee;
        private double modifiedfee;
        private long time;
        private long height;
        private double startingpriority;
        private double currentpriority;
        private int descendantcount;
        private int descendantsize;
        private double descendantfees;
        private int ancestorcount;
        private long ancestorsize;
        private double ancestorfees;
        private List<String> depends;

        public long getSize() {
            return size;
        }

        public void setSize(long size) {
            this.size = size;
        }

        public double getFee() {
            return fee;
        }

        public void setFee(double fee) {
            this.fee = fee;
        }

        public double getModifiedfee() {
            return modifiedfee;
        }

        public void setModifiedfee(double modifiedfee) {
            this.modifiedfee = modifiedfee;
        }

        public long getTime() {
            return time;
        }

        public void setTime(long time) {
            this.time = time;
        }

        public long getHeight() {
            return height;
        }

        public void setHeight(long height) {
            this.height = height;
        }

        public double getStartingpriority() {
            return startingpriority;
        }

        public void setStartingpriority(double startingpriority) {
            this.startingpriority = startingpriority;
        }

        public double getCurrentpriority() {
            return currentpriority;
        }

        public void setCurrentpriority(double currentpriority) {
            this.currentpriority = currentpriority;
        }

        public int getDescendantcount() {
            return descendantcount;
        }

        public void setDescendantcount(int descendantcount) {
            this.descendantcount = descendantcount;
        }

        public int getDescendantsize() {
            return descendantsize;
        }

        public void setDescendantsize(int descendantsize) {
            this.descendantsize = descendantsize;
        }

        public double getDescendantfees() {
            return descendantfees;
        }

        public void setDescendantfees(double descendantfees) {
            this.descendantfees = descendantfees;
        }

        public int getAncestorcount() {
            return ancestorcount;
        }

        public void setAncestorcount(int ancestorcount) {
            this.ancestorcount = ancestorcount;
        }

        public long getAncestorsize() {
            return ancestorsize;
        }

        public void setAncestorsize(long ancestorsize) {
            this.ancestorsize = ancestorsize;
        }

        public double getAncestorfees() {
            return ancestorfees;
        }

        public void setAncestorfees(double ancestorfees) {
            this.ancestorfees = ancestorfees;
        }

        public List<String> getDepends() {
            return depends;
        }

        public void setDepends(List<String> depends) {
            this.depends = depends;
        }
    }
}
