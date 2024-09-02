import NasaRcpTest.*;
import com.google.gson.Gson;
import javaTools.Hex;
import javaTools.JsonTools;
import nasa.RpcRequest;
import nasa.data.TransactionRPC;
import nasa.data.UTXO;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static nasa.NasaRpcNames.CREATERAWTRANSACTION;
import static nasa.NasaRpcNames.ESTIMATE_FEE;

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
        GetBlockchainInfo.BlockchainInfo blockchainInfo = getBlockchainInfo();
        if(blockchainInfo==null) return false;
        this.bestHeight = blockchainInfo.getBlocks()-1;
        this.bestBlockId = blockchainInfo.getBestblockhash();
        return true;
    }

    public String createRawTransactionFch(String toAddr, double amount, String opreturn) {
        createRawTransactionParamsFch createRawTransactionParams = new createRawTransactionParamsFch(toAddr, amount, opreturn);
        RpcRequest jsonRPC2Request = new RpcRequest(CREATERAWTRANSACTION, createRawTransactionParams.toParams());

        JsonTools.printJson(jsonRPC2Request);

        Object result = RpcRequest.requestRpc(url, username, password, "listSinceBlock", jsonRPC2Request);
        return (String) result;
//        return new CreateRawTransaction().createRawTransactionFch(toAddr,amount,opreturn,url,username,password);
    }

    public String createRawTransactionDoge(String toAddr, double amount, String opreturn) {
        CreateRawTransactionParamsDoge createRawTransactionParams = new CreateRawTransactionParamsDoge(toAddr, amount, opreturn);
        RpcRequest jsonRPC2Request = new RpcRequest(CREATERAWTRANSACTION, createRawTransactionParams.toParams());
        JsonTools.printJson(jsonRPC2Request);
        Object result = RpcRequest.requestRpc(url, username, password, "listSinceBlock", jsonRPC2Request);
        return (String) result;
//        return new CreateRawTransaction().createRawTransactionDoge(toAddr,amount,opreturn,url,username,password);
    }
    public static class createRawTransactionParamsFch {
        private List<CreateRawTransaction.createRawTransactionParamsFch.Input> inputs;
        private List<Map<String, Object>> outputs; // addr:amount or data:hex
        private String lockTime;

        public createRawTransactionParamsFch(String addr, double amount, String opreturn) {
            this.inputs = new ArrayList<>();
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

        public createRawTransactionParamsFch(List<CreateRawTransaction.createRawTransactionParamsFch.Input> inputs, List<Map<String, Object>> outputs, String lockTime) {
            this.inputs = inputs;
            this.outputs = outputs;
            this.lockTime = lockTime;
        }

        public List<CreateRawTransaction.createRawTransactionParamsFch.Input> getInputs() {
            return inputs;
        }

        public void setInputs(List<CreateRawTransaction.createRawTransactionParamsFch.Input> inputs) {
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

    public static class CreateRawTransactionParamsDoge {
        private List<CreateRawTransaction.CreateRawTransactionParamsDoge.Input> inputs;
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

        public CreateRawTransactionParamsDoge(List<CreateRawTransaction.CreateRawTransactionParamsDoge.Input> inputs, Map<String, Object> outputs, String lockTime) {
            this.inputs = inputs;
            this.outputs = outputs;
            this.lockTime = lockTime;
        }

        public List<CreateRawTransaction.CreateRawTransactionParamsDoge.Input> getInputs() {
            return inputs;
        }

        public void setInputs(List<CreateRawTransaction.CreateRawTransactionParamsDoge.Input> inputs) {
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

    public double estimateFee(String url, String username, String password){
        return new EstimateFee().estimatefee(url,username,password);
    }

    public double estimateFee(int nBlocks){
        Object[] params = new Object[]{nBlocks};
        RpcRequest jsonRPC2Request = new RpcRequest(ESTIMATE_FEE, params);
        JsonTools.printJson(jsonRPC2Request);
        return (double) RpcRequest.requestRpc(url, username, password, ESTIMATE_FEE, jsonRPC2Request);
    }

    public EstimateFee.ResultSmart estimateSmartFee(int nBlocks){
        return new EstimateFee().estimatesmartfee(nBlocks,url,username,password);
    }

    public FundRawTransaction.FundRawTransactionResult fundRawTransaction(String changeAddr, String rawTxHex, boolean includeWatchOnly, boolean receiverPayFee) {
        return new FundRawTransaction().fundRawTransaction(changeAddr,rawTxHex,includeWatchOnly,receiverPayFee,url,username,password);
    }

    public FundRawTransaction.FundRawTransactionResult fundRawTransaction(String rawTxHex) {
        return new FundRawTransaction().fundRawTransaction(rawTxHex,url,username,password);
    }

    public double balance(String minConf, boolean includeWatchOnly){
        return new GetBalance().getBalance(minConf,includeWatchOnly,url,username,password);
    }

    public GetBlockchainInfo.BlockchainInfo getBlockchainInfo(){
        return new GetBlockchainInfo().getBlockchainInfo(url, username, password);
    }

    public String blockHash(long height){
        return new GetBlockHash().getBlockHash(height,url,username,password);
    }

    public GetBlockHeader.BlockHeader blockHeader(String blockId){
        return new GetBlockHeader().getBlockHeader(blockId,url,username,password);
    }

    public String getRawTx(String txId){
        return new GetRawTx().getRawTx(txId,url,username,password);
    }
    public String[] getRawMempoolIds(){
        Object result = new GetRawMempool().getRawMempool(url,false,username,password);
        Gson gson = new Gson();
        return gson.fromJson(gson.toJson(result),String[].class);
    }

    public Map<String, GetRawMempool.Transaction> getRawMempoolTxs(){
        GetRawMempool getRawMempool = new GetRawMempool();
        Object result = getRawMempool.getRawMempool(url,true,username,password);
        return GetRawMempool.txsFromJson(result);
    }


    public TransactionRPC getTransaction(String txId, boolean includeWatchOnly){
        return new GetTrasaction().getTransaction(txId,includeWatchOnly,url,username,password);
    }
    public ListSinceBlock.ListSinceBlockResult listSinceBlock(String block, String minConf, boolean includeWatchOnly){
        return new ListSinceBlock().listSinceBlock(block,minConf,includeWatchOnly,url,username,password);
    }

    public UTXO[] listUnspent(){
        return new ListUnspent().listUnspent(url,username,password);
    }

    public UTXO[] listUnspent(@Nullable String addr, @Nullable String minConf){
        return new ListUnspent().listUnspent(addr,minConf,url,username,password);
    }

    public UTXO[] listUnspent(@Nullable String addr, @Nullable String minConf, boolean includeUnsafe){
        return new ListUnspent().listUnspent(addr,minConf,includeUnsafe,url,username,password);
    }

    public String sendRawTransaction(String hex){
        return new SendRawTransaction().sendRawTransaction(hex,url,username,password);
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
