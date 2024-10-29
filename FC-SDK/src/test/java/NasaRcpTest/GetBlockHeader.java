package NasaRcpTest;

import com.google.gson.Gson;
import javaTools.JsonTools;
import nasa.RpcRequest;
import org.junit.jupiter.api.Test;

public class GetBlockHeader {
    public static final String GETBLOCKHEADER = "getblockheader";
    private String params;
    private BlockHeader result;

    @Test
    public void test() {
//        String blockId="00000000000000013adfaeeea4fe4e59f950af69aa7fcc7c7d1b5a11e92fb302";
        String blockId = "76005fa9b83a3099605624349870d75fdd25ec30a92c36cb76aea2e270485c47";
        params = blockId;
//        String url = "http://127.0.0.1:8332";
        String url = "http://127.0.0.1:22555";
        BlockHeader blockHeader = getBlockHeader(url, "username", "password", params);
        JsonTools.printJson(blockHeader);
    }

    public BlockHeader getBlockHeader(String blockId, String url, String username, String password) {

        RpcRequest jsonRPC2Request = new RpcRequest(GETBLOCKHEADER, new Object[]{blockId});
        Object result = RpcRequest.requestRpc(url, username, password, GETBLOCKHEADER, jsonRPC2Request);
        return makeBlockHeader(result);
    }

    private BlockHeader makeBlockHeader(Object res) {
        Gson gson = new Gson();
        result = gson.fromJson(gson.toJson(res), BlockHeader.class);
        return result;
    }

    public String getParams() {
        return params;
    }

    public void setParams(String params) {
        this.params = params;
    }

    public BlockHeader getResult() {
        return result;
    }

    public void setResult(BlockHeader result) {
        this.result = result;
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
}
