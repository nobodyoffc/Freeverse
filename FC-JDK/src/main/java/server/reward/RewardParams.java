package server.reward;

import java.util.Map;

public class RewardParams {
    private Map<String, String> builderShareMap;
    private Map<String, String> costMap;
    private String orderViaShare;
    private String consumeViaShare;

    public String getOrderViaShare() {
        return orderViaShare;
    }

    public void setOrderViaShare(String orderViaShare) {
        this.orderViaShare = orderViaShare;
    }

    public String getConsumeViaShare() {
        return consumeViaShare;
    }

    public void setConsumeViaShare(String consumeViaShare) {
        this.consumeViaShare = consumeViaShare;
    }

    public Map<String, String> getBuilderShareMap() {
        return builderShareMap;
    }

    public void setBuilderShareMap(Map<String, String> builderShareMap) {
        this.builderShareMap = builderShareMap;
    }

    public Map<String, String> getCostMap() {
        return costMap;
    }

    public void setCostMap(Map<String, String> costMap) {
        this.costMap = costMap;
    }
}
