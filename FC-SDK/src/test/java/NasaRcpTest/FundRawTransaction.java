package NasaRcpTest;

import com.google.gson.Gson;
import javaTools.JsonTools;
import nasa.RpcRequest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class FundRawTransaction {
    public static final String FUNDRAWTRANSACTION = "fundrawtransaction";
    private FundRawTransactionParams params;
    private FundRawTransactionResult result;

    @Test
    public void test() {
        String urlDoge = "http://127.0.0.1:22555";
        String urlFch = "http://127.0.0.1:8332";

        result = fundRawTransaction("FSpN4acDqFD9jYM6DoTVRjvTKU4DPXgQHx", "02000000000280969800000000001976a9148b6ebb34f066bc0ec4f2d3e5d021a56ffc1ef71d88ac0000000000000000046a02686900000000", true, true, urlFch, "username", "password");
        JsonTools.printJson(result);
        result = fundRawTransaction("DS8M937nHLtmeNef6hnu17ZXAwmVpM6TXY", "01000000000280969800000000001976a9148b6ebb34f066bc0ec4f2d3e5d021a56ffc1ef71d88ac0000000000000000046a02037800000000", true, true, urlDoge, "username", "password");
        JsonTools.printJson(result);
    }

    public FundRawTransactionResult fundRawTransaction(String changeAddr, String rawTxHex, boolean includeWatchOnly, boolean receiverPayFee, String url, String username, String password) {

        ArrayList<Integer> feePayBy = null;
        if (receiverPayFee) {
            feePayBy = new ArrayList<>();
            feePayBy.add(0);
        }
        FundRawTransactionParams fundRawTransactionParams = new FundRawTransactionParams(changeAddr, rawTxHex, includeWatchOnly, feePayBy);
        RpcRequest jsonRPC2Request = new RpcRequest(FUNDRAWTRANSACTION, fundRawTransactionParams.toParams());

        JsonTools.printJson(jsonRPC2Request);

        Object result = RpcRequest.requestRpc(url, username, password, FUNDRAWTRANSACTION, jsonRPC2Request);
        return getResult(result);
    }

    public FundRawTransactionResult fundRawTransaction(String rawTxHex, String url, String username, String password) {
        FundRawTransactionParams fundRawTransactionParams = new FundRawTransactionParams(rawTxHex);
        RpcRequest jsonRPC2Request = new RpcRequest(FUNDRAWTRANSACTION, fundRawTransactionParams.toParams());

        JsonTools.printJson(jsonRPC2Request);

        Object result = RpcRequest.requestRpc(url, username, password, FUNDRAWTRANSACTION, jsonRPC2Request);
        Gson gson = new Gson();
        return gson.fromJson(gson.toJson(result ), FundRawTransactionResult.class);
    }


    private FundRawTransactionResult getResult(Object res) {
        Gson gson = new Gson();
        result = gson.fromJson(gson.toJson(res), FundRawTransactionResult.class);
        if (result != null) {
            return result;
        }
        return null;
    }

    public FundRawTransactionParams getParams() {
        return params;
    }

    public void setParams(FundRawTransactionParams params) {
        this.params = params;
    }

    public static class FundRawTransactionParams {
        private String rawTxHex;
        private Options options;

        public FundRawTransactionParams(String changeAddr, String rawTxHex, boolean includeWatchOnly, ArrayList<Integer> feePayBy) {
            this.rawTxHex = rawTxHex;
            this.options = new Options();
            options.setChangeAddress(changeAddr);
            options.setIncludeWatching(includeWatchOnly);
            options.setSubtractFeeFromOutputs(feePayBy);
            options.setChangePosition(1);
        }

        public FundRawTransactionParams() {
        }

        public FundRawTransactionParams(String rawTxHex) {
            this.rawTxHex = rawTxHex;
        }

        public Object[] toParams() {
            List<Object> objects = new ArrayList<>();
            objects.add(rawTxHex);
            if (options != null) {
                objects.add(options);
            }

            Object[] params = objects.toArray();
            JsonTools.printJson(params);
            return params;
        }

        public String toJson() {
            return new Gson().toJson(toParams());
        }

    }

    public static class Options {
        private String changeAddress;
        private Integer changePosition;
        private boolean includeWatching;
        private boolean lockUnspents;
        private Double feeRate;
        private List<Integer> subtractFeeFromOutputs; //[vout_index,...] The fee will be equally deducted from the amount of each specified output.

        public Options(String changeAddress, boolean includeWatching, ArrayList<Integer> subtractFeeFromOutputs) {
            // Set default values
            this.changeAddress = changeAddress;
            this.includeWatching = includeWatching;
            if (subtractFeeFromOutputs != null) this.subtractFeeFromOutputs = subtractFeeFromOutputs;
        }

        public Options() {
        }

        // Add getters and setters for each field

        public String getChangeAddress() {
            return changeAddress;
        }

        public void setChangeAddress(String changeAddress) {
            this.changeAddress = changeAddress;
        }

        public Integer getChangePosition() {
            return changePosition;
        }

        public void setChangePosition(Integer changePosition) {
            this.changePosition = changePosition;
        }

        public boolean isIncludeWatching() {
            return includeWatching;
        }

        public void setIncludeWatching(boolean includeWatching) {
            this.includeWatching = includeWatching;
        }

        public boolean isLockUnspents() {
            return lockUnspents;
        }

        public void setLockUnspents(boolean lockUnspents) {
            this.lockUnspents = lockUnspents;
        }

        public Double getFeeRate() {
            return feeRate;
        }

        public void setFeeRate(Double feeRate) {
            this.feeRate = feeRate;
        }

        public List<Integer> getSubtractFeeFromOutputs() {
            return subtractFeeFromOutputs;
        }

        public void setSubtractFeeFromOutputs(List<Integer> subtractFeeFromOutputs) {
            this.subtractFeeFromOutputs = subtractFeeFromOutputs;
        }
    }

    public class FundRawTransactionResult {
        private String hex;
        private double fee;
        private int changePosition;

        public FundRawTransactionResult() {
            // Default constructor
        }

        public FundRawTransactionResult(String hex, double fee, int changePosition) {
            this.hex = hex;
            this.fee = fee;
            this.changePosition = changePosition;
        }

        // Add getters and setters for each field

        public String getHex() {
            return hex;
        }

        public void setHex(String hex) {
            this.hex = hex;
        }

        public double getFee() {
            return fee;
        }

        public void setFee(double fee) {
            this.fee = fee;
        }

        public int getChangePosition() {
            return changePosition;
        }

        public void setChangePosition(int changePosition) {
            this.changePosition = changePosition;
        }

    }
}
