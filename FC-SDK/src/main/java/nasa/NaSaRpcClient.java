package nasa;

import javaTools.ObjectTools;
import javaTools.Hex;
import javaTools.JsonTools;
import javaTools.NumberTools;
import nasa.data.TransactionBrief;
import nasa.data.TransactionRPC;
import nasa.data.UTXO;
import com.google.gson.Gson;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static nasa.NasaRpcNames.*;

public class NaSaRpcClient {
    String url;
    String username;
    String password;
    String bestBlockId;
    long bestHeight;

    public NaSaRpcClient(String url, String username, String password) {
        this.url = url;
        this.username = username;
        this.password = password;
    }

    public NaSaRpcClient(String url, String username, byte[] password) {
        this.url = url;
        this.username = username;
        this.password = new String(password,StandardCharsets.UTF_8);
    }

    public boolean freshBestBlock(){
        BlockchainInfo blockchainInfo = getBlockchainInfo();
        if(blockchainInfo==null) return false;
        this.bestHeight = blockchainInfo.getBlocks()-1;
        this.bestBlockId = blockchainInfo.getBestblockhash();
        return true;
    }

    public String createRawTransactionFch(String toAddr, double amount, String opreturn) {
        CreateRawTransactionParamsFch createRawTransactionParams = new CreateRawTransactionParamsFch(toAddr, amount, opreturn);
        RpcRequest jsonRPC2Request = new RpcRequest(CREATERAWTRANSACTION, createRawTransactionParams.toParams());
        Object result = RpcRequest.requestRpc(url, username, password, "listSinceBlock", jsonRPC2Request);
        return (String) result;
    }

    public String createRawTransactionDoge(String toAddr, double amount, String opreturn) {
        CreateRawTransactionParamsDoge createRawTransactionParams = new CreateRawTransactionParamsDoge(toAddr, amount, opreturn);
        RpcRequest jsonRPC2Request = new RpcRequest(CREATERAWTRANSACTION, createRawTransactionParams.toParams());
        Object result = RpcRequest.requestRpc(url, username, password, "listSinceBlock", jsonRPC2Request);
        return (String) result;
    }
    private static class CreateRawTransactionParamsFch {
        private List<CreateRawTransactionParamsFch.Input> inputs;
        private List<Map<String, Object>> outputs; // addr:amount or data:hex
        private String lockTime;

        public CreateRawTransactionParamsFch(String addr, double amount, String opreturn) {
            this.inputs = new ArrayList<CreateRawTransactionParamsFch.Input>();
            this.outputs = new ArrayList<>();
            Map<String, Object> output1 = new HashMap<>();
            output1.put(addr, amount);
            outputs.add(output1);
            if (opreturn != null && !opreturn.isBlank()) {
                Map<String, Object> output2 = new HashMap<>();
                output2.put("data", Hex.toHex(opreturn.getBytes()));
                outputs.add(output2);
            }
        }

        public CreateRawTransactionParamsFch(List<CreateRawTransactionParamsFch.Input> inputs, List<Map<String, Object>> outputs, String lockTime) {
            this.inputs = inputs;
            this.outputs = outputs;
            this.lockTime = lockTime;
        }
        public List<CreateRawTransactionParamsFch.Input> getInputs() {
            return inputs;
        }

        public void setInputs(List<CreateRawTransactionParamsFch.Input> inputs) {
            this.inputs = inputs;
        }

        public List<Map<String, Object>> getOutputs() {
            return outputs;
        }

        public void setOutputs(List<Map<String, Object>> outputs) {
            this.outputs = outputs;
        }

        public String getLockTime() {
            return lockTime;
        }

        public void setLockTime(String lockTime) {
            this.lockTime = lockTime;
        }

        public Object[] toParams() {
            List<Object> objects = new ArrayList<>();
            objects.add(inputs);
            objects.add(outputs);
            if (lockTime != null) {
                objects.add(Long.parseLong(lockTime));
            }

            Object[] params = objects.toArray();
            //TODO
            JsonTools.printJson(params);
            return params;
        }

        public String toJson() {
            return new Gson().toJson(toParams());
        }

        public static class Input {
            private String txid;
            private int vout;
            private Integer sequence; // Optional, represented as Integer to allow null value

            public Input(String txid, int vout) {
                this.txid = txid;
                this.vout = vout;
            }

            public Input(String txid, int vout, Integer sequence) {
                this.txid = txid;
                this.vout = vout;
                this.sequence = sequence;
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

            public Integer getSequence() {
                return sequence;
            }

            public void setSequence(Integer sequence) {
                this.sequence = sequence;
            }
        }
    }

    private static class CreateRawTransactionParamsDoge {
        private List<CreateRawTransactionParamsDoge.Input> inputs;
        private Map<String, Object> outputs; // addr:amount or data:hex
        private String lockTime;
        public CreateRawTransactionParamsDoge(String addr, double amount, String opreturn) {
            this.inputs = new ArrayList<>();
            this.outputs = new HashMap<>();
            outputs.put(addr, amount);
            if (opreturn != null && !opreturn.isBlank()) {
                outputs.put("data", Hex.toHex(opreturn.getBytes()));
            }
        }

        public CreateRawTransactionParamsDoge(List<CreateRawTransactionParamsDoge.Input> inputs, Map<String, Object> outputs, String lockTime) {
            this.inputs = inputs;
            this.outputs = outputs;
            this.lockTime = lockTime;
        }

        public List<CreateRawTransactionParamsDoge.Input> getInputs() {
            return inputs;
        }

        public void setInputs(List<CreateRawTransactionParamsDoge.Input> inputs) {
            this.inputs = inputs;
        }

        public Map<String, Object> getOutputs() {
            return outputs;
        }

        public void setOutputs(Map<String, Object> outputs) {
            this.outputs = outputs;
        }

        public String getLockTime() {
            return lockTime;
        }

        public void setLockTime(String lockTime) {
            this.lockTime = lockTime;
        }

        public Object[] toParams() {
            List<Object> objects = new ArrayList<>();
            objects.add(inputs);
            objects.add(outputs);
            if (lockTime != null) {
                objects.add(Long.parseLong(lockTime));
            }

            Object[] params = objects.toArray();
            return params;
        }

        public String toJson() {
            return new Gson().toJson(toParams());
        }

        public class Input {
            private String txid;
            private int vout;
            private Integer sequence; // Optional, represented as Integer to allow null value

            public Input(String txid, int vout) {
                this.txid = txid;
                this.vout = vout;
            }

            public Input(String txid, int vout, Integer sequence) {
                this.txid = txid;
                this.vout = vout;
                this.sequence = sequence;
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

            public Integer getSequence() {
                return sequence;
            }

            public void setSequence(Integer sequence) {
                this.sequence = sequence;
            }
        }
    }

    public double estimateFee(){
        return estimateFee(null);
    }

    public double estimateFee(Integer nBlocks){
        Object[] params=null;
        if(nBlocks!=null)params = new Object[]{nBlocks};
        RpcRequest jsonRPC2Request = new RpcRequest(ESTIMATE_FEE, params);
        JsonTools.printJson(jsonRPC2Request);
        return (double) RpcRequest.requestRpc(url, username, password, ESTIMATE_FEE, jsonRPC2Request);
    }

    public ResultEstimateSmartFee estimateSmartFee(Integer nBlocks){
        Object[] params = new Object[]{nBlocks};
        RpcRequest jsonRPC2Request = new RpcRequest(NasaRpcNames.ESTIMATE_SMART_FEE, params);
        JsonTools.printJson(jsonRPC2Request);
        Object result1 = RpcRequest.requestRpc(url, username, password, NasaRpcNames.ESTIMATE_SMART_FEE, jsonRPC2Request);
        Gson gson = new Gson();
        return gson.fromJson(gson.toJson(result1), ResultEstimateSmartFee.class);
    }
    public static class ResultEstimateSmartFee {
        private double feerate;
        private long blocks;
        public double getFeerate() {
            return feerate;
        }

        public void setFeerate(double feerate) {
            this.feerate = feerate;
        }

        public long getBlocks() {
            return blocks;
        }

        public void setBlocks(long blocks) {
            this.blocks = blocks;
        }
    }
    public ResultFundRawTransaction fundRawTransaction(String changeAddr, String rawTxHex, boolean includeWatchOnly, boolean receiverPayFee) {
        ArrayList<Integer> feePayBy = null;
        if (receiverPayFee) {
            feePayBy = new ArrayList<>();
            feePayBy.add(0);
        }
        FundRawTransactionParams fundRawTransactionParams = new FundRawTransactionParams(changeAddr, rawTxHex, includeWatchOnly, feePayBy);
        RpcRequest jsonRPC2Request = new RpcRequest(FUNDRAWTRANSACTION, fundRawTransactionParams.toParams());
        Object result = RpcRequest.requestRpc(url, username, password, FUNDRAWTRANSACTION, jsonRPC2Request);
        Gson gson = new Gson();
        return gson.fromJson(gson.toJson(result), ResultFundRawTransaction.class);
    }
    public ResultFundRawTransaction fundRawTransaction(String rawTxHex) {
        FundRawTransactionParams fundRawTransactionParams = new FundRawTransactionParams(rawTxHex);
        RpcRequest jsonRPC2Request = new RpcRequest(FUNDRAWTRANSACTION, fundRawTransactionParams.toParams());
        Object result = RpcRequest.requestRpc(url, username, password, FUNDRAWTRANSACTION, jsonRPC2Request);
        Gson gson = new Gson();
        return gson.fromJson(gson.toJson(result ), ResultFundRawTransaction.class);
    }
    private static class FundRawTransactionParams {
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
    public static class ResultFundRawTransaction {
        private String hex;
        private double fee;
        private int changePosition;

        public ResultFundRawTransaction() {
            // Default constructor
        }

        public ResultFundRawTransaction(String hex, double fee, int changePosition) {
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


    public double balance(String minConf, boolean includeWatchOnly){
        GetBalanceParams params = new GetBalanceParams("*", minConf, includeWatchOnly);
        RpcRequest jsonRPC2Request = new RpcRequest(GETBALANCE, params.toParams());
        Object result = RpcRequest.requestRpc(url, username, password, "listSinceBlock", jsonRPC2Request);
        if (result == null) throw new RuntimeException("Getting balance for RPC wrong.");
        return (double) result;
    }
    private static class GetBalanceParams {
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

    public BlockchainInfo getBlockchainInfo(){
        RpcRequest jsonRPC2Request = new RpcRequest(GETBLOCKCHAININFO, null);
        Object result = RpcRequest.requestRpc(url, username, password, "getBlockchainInfo", jsonRPC2Request);
        return ObjectTools.objectToClass(result,BlockchainInfo.class);//BlockchainInfo.makeBlockchainInfo(result);
    }
    public static class BlockchainInfo {
        private String chain;
        private long blocks;
        private long headers;
        private String bestblockhash;
        private double difficulty;
        private int mediantime;
        private double verificationprogress;
        private boolean initialblockdownload;
        private String chainwork;
        private long size_on_disk;
        private boolean pruned;
        private List<BlockchainInfo.Softfork> softforks;
        private BlockchainInfo.Bip9Softfork bip9_softforks;
        private String warnings;

//        private static BlockchainInfo makeBlockchainInfo(Object res) {
//            Gson gson = new Gson();
//            return gson.fromJson(gson.toJson(res), BlockchainInfo.class);
//        }

        public String getChain() {
            return chain;
        }

        public void setChain(String chain) {
            this.chain = chain;
        }

        public long getBlocks() {
            return blocks;
        }

        public void setBlocks(long blocks) {
            this.blocks = blocks;
        }

        public long getHeaders() {
            return headers;
        }

        public void setHeaders(long headers) {
            this.headers = headers;
        }

        public String getBestblockhash() {
            return bestblockhash;
        }

        public void setBestblockhash(String bestblockhash) {
            this.bestblockhash = bestblockhash;
        }

        public double getDifficulty() {
            return difficulty;
        }

        public void setDifficulty(double difficulty) {
            this.difficulty = difficulty;
        }

        public int getMediantime() {
            return mediantime;
        }

        public void setMediantime(int mediantime) {
            this.mediantime = mediantime;
        }

        public double getVerificationprogress() {
            return verificationprogress;
        }

        public void setVerificationprogress(double verificationprogress) {
            this.verificationprogress = verificationprogress;
        }

        public boolean isInitialblockdownload() {
            return initialblockdownload;
        }

        public void setInitialblockdownload(boolean initialblockdownload) {
            this.initialblockdownload = initialblockdownload;
        }

        public String getChainwork() {
            return chainwork;
        }

        public void setChainwork(String chainwork) {
            this.chainwork = chainwork;
        }

        public long getSize_on_disk() {
            return size_on_disk;
        }

        public void setSize_on_disk(long size_on_disk) {
            this.size_on_disk = size_on_disk;
        }

        public boolean isPruned() {
            return pruned;
        }

        public void setPruned(boolean pruned) {
            this.pruned = pruned;
        }

        public List<BlockchainInfo.Softfork> getSoftforks() {
            return softforks;
        }

        public void setSoftforks(List<BlockchainInfo.Softfork> softforks) {
            this.softforks = softforks;
        }

        public BlockchainInfo.Bip9Softfork getBip9_softforks() {
            return bip9_softforks;
        }

        public void setBip9_softforks(BlockchainInfo.Bip9Softfork bip9_softforks) {
            this.bip9_softforks = bip9_softforks;
        }

        public String getWarnings() {
            return warnings;
        }

        public void setWarnings(String warnings) {
            this.warnings = warnings;
        }

        public static class Softfork {
            private String id;
            private int version;
            private BlockchainInfo.Reject reject;

            public String getId() {
                return id;
            }

            public void setId(String id) {
                this.id = id;
            }

            public int getVersion() {
                return version;
            }

            public void setVersion(int version) {
                this.version = version;
            }

            public BlockchainInfo.Reject getReject() {
                return reject;
            }

            public void setReject(BlockchainInfo.Reject reject) {
                this.reject = reject;
            }
        }

        public static class Reject {
            private boolean status;

            public boolean isStatus() {
                return status;
            }

            public void setStatus(boolean status) {
                this.status = status;
            }
        }

        public static class Bip9Softfork {
            private BlockchainInfo.Csv csv;

            public BlockchainInfo.Csv getCsv() {
                return csv;
            }

            public void setCsv(BlockchainInfo.Csv csv) {
                this.csv = csv;
            }
        }

        public static class Csv {
            private String status;
            private long startTime;
            private long timeout;
            private int since;

            public String getStatus() {
                return status;
            }

            public void setStatus(String status) {
                this.status = status;
            }

            public long getStartTime() {
                return startTime;
            }

            public void setStartTime(long startTime) {
                this.startTime = startTime;
            }

            public long getTimeout() {
                return timeout;
            }

            public void setTimeout(long timeout) {
                this.timeout = timeout;
            }

            public int getSince() {
                return since;
            }

            public void setSince(int since) {
                this.since = since;
            }
        }
    }
    public String blockHash(long height){
        RpcRequest jsonRPC2Request = new RpcRequest(GETBLOCKHASH, new Object[]{height});
        return (String) RpcRequest.requestRpc(url, username, password, GETBLOCKHASH, jsonRPC2Request);
    }

    public BlockHeader blockHeader(String blockId){
        RpcRequest jsonRPC2Request = new RpcRequest(GETBLOCKHEADER, new Object[]{blockId});
        Object result = RpcRequest.requestRpc(url, username, password, GETBLOCKHEADER, jsonRPC2Request);
        Gson gson = new Gson();
        return gson.fromJson(gson.toJson(result), BlockHeader.class);
    }

    public static class BlockHeader {

        private String hash;


        private int confirmations;

        private int height;


        private int version;


        private String versionHex;

        private String merkleroot;


        private long time;


        private long mediantime;


        private long nonce;


        private String bits;


        private double difficulty;


        private String chainwork;


        private String nextblockhash;

        // Add getters and setters for each field

        // You can also override toString() for debugging purposes

        @Override
        public String toString() {
            return "BlockInfo{" +
                    "hash='" + hash + '\'' +
                    ", confirmations=" + confirmations +
                    ", height=" + height +
                    ", version=" + version +
                    ", versionHex='" + versionHex + '\'' +
                    ", merkleroot='" + merkleroot + '\'' +
                    ", time=" + time +
                    ", mediantime=" + mediantime +
                    ", nonce=" + nonce +
                    ", bits='" + bits + '\'' +
                    ", difficulty=" + difficulty +
                    ", chainwork='" + chainwork + '\'' +
                    ", nextblockhash='" + nextblockhash + '\'' +
                    '}';
        }

        public String getHash() {
            return hash;
        }

        public void setHash(String hash) {
            this.hash = hash;
        }

        public int getConfirmations() {
            return confirmations;
        }

        public void setConfirmations(int confirmations) {
            this.confirmations = confirmations;
        }

        public int getHeight() {
            return height;
        }

        public void setHeight(int height) {
            this.height = height;
        }

        public int getVersion() {
            return version;
        }

        public void setVersion(int version) {
            this.version = version;
        }

        public String getVersionHex() {
            return versionHex;
        }

        public void setVersionHex(String versionHex) {
            this.versionHex = versionHex;
        }

        public String getMerkleroot() {
            return merkleroot;
        }

        public void setMerkleroot(String merkleroot) {
            this.merkleroot = merkleroot;
        }

        public long getTime() {
            return time;
        }

        public void setTime(long time) {
            this.time = time;
        }

        public long getMediantime() {
            return mediantime;
        }

        public void setMediantime(long mediantime) {
            this.mediantime = mediantime;
        }

        public long getNonce() {
            return nonce;
        }

        public void setNonce(long nonce) {
            this.nonce = nonce;
        }

        public String getBits() {
            return bits;
        }

        public void setBits(String bits) {
            this.bits = bits;
        }

        public double getDifficulty() {
            return difficulty;
        }

        public void setDifficulty(double difficulty) {
            this.difficulty = difficulty;
        }

        public String getChainwork() {
            return chainwork;
        }

        public void setChainwork(String chainwork) {
            this.chainwork = chainwork;
        }

        public String getNextblockhash() {
            return nextblockhash;
        }

        public void setNextblockhash(String nextblockhash) {
            this.nextblockhash = nextblockhash;
        }
    }
    public String getRawTx(String txId){
        RpcRequest jsonRPC2Request = new RpcRequest(GETRAWTRANSACTION, new Object[]{txId});
        Object result = RpcRequest.requestRpc(url, username, password, GETRAWTRANSACTION, jsonRPC2Request);
        return (String) result;
    }
    public String[] getRawMempoolIds(){
        Object result = getRawMempool(url,false,username,password);
        Gson gson = new Gson();
        return gson.fromJson(gson.toJson(result),String[].class);
    }
    public Map<String, TxInMempool> getRawMempoolTxs(){
        Object result = getRawMempool(url,true,username,password);
        return ObjectTools.objectToMap(result,String.class, TxInMempool.class);
    }

    private Object getRawMempool(String url, boolean verbose,String username, String password) {
        RpcRequest jsonRPC2Request = new RpcRequest(GETRAWMEMPOOL, new Object[]{verbose});
        return RpcRequest.requestRpc(url, username, password, GETRAWMEMPOOL, jsonRPC2Request);
    }

    public static class TxInMempool {
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
    
    public TransactionRPC getTransaction(String txId, boolean includeWatchOnly){
        TransactionParams txParams = new TransactionParams(txId, includeWatchOnly);
        RpcRequest jsonRPC2Request = new RpcRequest(GETTRANSACTION, txParams.toParams());
        Object result = RpcRequest.requestRpc(url, username, password, "listSinceBlock", jsonRPC2Request);
        return ObjectTools.objectToClass(result,TransactionRPC.class);
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
    public ListSinceBlockResult listSinceBlock(String block, String minConf, boolean includeWatchOnly){
        ListSinceBlockParams listSinceBlockParams = new ListSinceBlockParams(block, minConf, includeWatchOnly);
        RpcRequest jsonRPC2Request = new RpcRequest(LISTSINCEBLOCK, listSinceBlockParams.toParams());

        JsonTools.printJson(jsonRPC2Request);

        Object result = RpcRequest.requestRpc(url, username, password, "listSinceBlock", jsonRPC2Request);
        ListSinceBlockResult listSinceBlockResult = ObjectTools.objectToClass(result,ListSinceBlockResult.class);
        if (listSinceBlockResult != null && listSinceBlockResult.getTransactions() != null) {
            listSinceBlockResult.getTransactions().removeIf(tx -> tx.getConfirmations() < Integer.parseInt(minConf));
        }
        return listSinceBlockResult;
    }
    private static class ListSinceBlockParams {
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
    
    
    public UTXO[] listUnspent(){
        ListUnspentParams listUnspentParams = new ListUnspentParams(null, (String[]) null);
        RpcRequest jsonRPC2Request = new RpcRequest(LISTUNSPENT, listUnspentParams.toParams());
        Object result = RpcRequest.requestRpc(url, username, password, "listUnspent", jsonRPC2Request);
        return ObjectTools.objectToClass(result, UTXO[].class);
    }

    public UTXO[] listUnspent(@Nullable String addr, @Nullable String minConf){
        ListUnspentParams listUnspentParams = new ListUnspentParams(minConf, new String[]{addr});
        RpcRequest jsonRPC2Request = new RpcRequest(LISTUNSPENT, listUnspentParams.toParams());
        Object result = RpcRequest.requestRpc(url, username, password, LISTUNSPENT, jsonRPC2Request);
        return ObjectTools.objectToClass(result, UTXO[].class);
    }

    public UTXO[] listUnspent(@Nullable String addr, @Nullable String minConf, boolean includeUnsafe){
        ListUnspentParams listUnspentParams = new ListUnspentParams(minConf, new String[]{addr}, includeUnsafe);
        RpcRequest jsonRPC2Request = new RpcRequest(LISTUNSPENT, listUnspentParams.toParams());
        Object result = RpcRequest.requestRpc(url, username, password, LISTUNSPENT, jsonRPC2Request);
        return ObjectTools.objectToClass(result, UTXO[].class);
    }
    private static class ListUnspentParams {
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
    public String sendRawTransaction(String hex){
        String[] params = new String[]{hex};
        RpcRequest jsonRPC2Request = new RpcRequest(SENDRAWTRANSACTION, params);
        Object result = RpcRequest.requestRpc(url, username, password, SENDRAWTRANSACTION, jsonRPC2Request);
        if(Hex.isHexString((String) result) )return (String) result;
        else return JsonTools.toNiceJson(result);
    }

    public Object decodeRawTransaction(String hex){
        String[] params = new String[]{hex};
        RpcRequest jsonRPC2Request = new RpcRequest(DECODERAWTRANSACTION, params);
        return RpcRequest.requestRpc(url, username, password, DECODERAWTRANSACTION, jsonRPC2Request);
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getBestBlockId() {
        return bestBlockId;
    }

    public void setBestBlockId(String bestBlockId) {
        this.bestBlockId = bestBlockId;
    }

    public long getBestHeight() {
        return bestHeight;
    }

    public void setBestHeight(long bestHeight) {
        this.bestHeight = bestHeight;
    }
    //    public  (){
//        return new .g(,url,username,password);
//    }
}
