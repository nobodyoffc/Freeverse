package swap;

import java.util.List;

public class SwapPendingData {
    private String sid;
    private List<SwapAffair> pendingList;

    public static final String  swapPendingMappingJsonStr = "{\"mappings\":{\"properties\":{\"sid\":{\"type\":\"keyword\"},\"pendingList\":{\"type\":\"nested\",\"properties\":{\"id\":{\"type\":\"keyword\"},\"sid\":{\"type\":\"keyword\"},\"sn\":{\"type\":\"long\"},\"act\":{\"type\":\"keyword\"},\"g\":{\"type\":\"object\",\"properties\":{\"txId\":{\"type\":\"keyword\"},\"refundTxId\":{\"type\":\"keyword\"},\"withdrawTxId\":{\"type\":\"keyword\"},\"refundAmt\":{\"type\":\"double\"},\"addr\":{\"type\":\"keyword\"},\"amt\":{\"type\":\"double\"},\"sum\":{\"type\":\"double\"},\"blockTime\":{\"type\":\"long\"},\"blockHeight\":{\"type\":\"long\"},\"blockIndex\":{\"type\":\"long\"},\"txFee\":{\"type\":\"double\"}}},\"m\":{\"type\":\"object\",\"properties\":{\"txId\":{\"type\":\"keyword\"},\"refundTxId\":{\"type\":\"keyword\"},\"withdrawTxId\":{\"type\":\"keyword\"},\"refundAmt\":{\"type\":\"double\"},\"addr\":{\"type\":\"keyword\"},\"amt\":{\"type\":\"double\"},\"sum\":{\"type\":\"double\"},\"blockTime\":{\"type\":\"long\"},\"blockHeight\":{\"type\":\"long\"},\"blockIndex\":{\"type\":\"long\"},\"txFee\":{\"type\":\"double\"}}},\"sendTime\":{\"type\":\"long\"},\"getTime\":{\"type\":\"long\"},\"state\":{\"type\":\"keyword\"},\"error\":{\"type\":\"text\"}}}}}}";


    public String getSid() {
        return sid;
    }

    public void setSid(String sid) {
        this.sid = sid;
    }

    public List<SwapAffair> getPendingList() {
        return pendingList;
    }

    public void setPendingList(List<SwapAffair> pendingList) {
        this.pendingList = pendingList;
    }
}
