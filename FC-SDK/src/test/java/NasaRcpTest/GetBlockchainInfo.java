package NasaRcpTest;

import com.google.gson.Gson;
import javaTools.JsonTools;
import nasa.RpcRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

public class GetBlockchainInfo {
    public static final String GETBLOCKCHAININFO = "getblockchaininfo";
    private BlockchainInfo result;

    @Test
    public void test() {
        BlockchainInfo blockchainInfo = getBlockchainInfo("http://127.0.0.1:8332", "username", "password");
        JsonTools.printJson(blockchainInfo);
    }

    public BlockchainInfo getBlockchainInfo(String url, String username, String password) {

        RpcRequest jsonRPC2Request = new RpcRequest(GETBLOCKCHAININFO, null);
        Object result = RpcRequest.requestRpc(url, username, password, "getBlockchainInfo", jsonRPC2Request);
        return makeBlockchainInfo(result);
    }

    private BlockchainInfo makeBlockchainInfo(Object res) {
        Gson gson = new Gson();
        result = gson.fromJson(gson.toJson(res), BlockchainInfo.class);
        return result;
    }


    public BlockchainInfo getResult() {
        return result;
    }

    public void setResult(BlockchainInfo result) {
        this.result = result;
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
        private List<Softfork> softforks;
        private Bip9Softfork bip9_softforks;
        private String warnings;

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

        public List<Softfork> getSoftforks() {
            return softforks;
        }

        public void setSoftforks(List<Softfork> softforks) {
            this.softforks = softforks;
        }

        public Bip9Softfork getBip9_softforks() {
            return bip9_softforks;
        }

        public void setBip9_softforks(Bip9Softfork bip9_softforks) {
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
            private Reject reject;

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

            public Reject getReject() {
                return reject;
            }

            public void setReject(Reject reject) {
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
            private Csv csv;

            public Csv getCsv() {
                return csv;
            }

            public void setCsv(Csv csv) {
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
}
