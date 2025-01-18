package server.reward;

import java.util.ArrayList;

public class RewardInfo {
    private String rewardId; //lastOrderId
    private Long rewardT;
    private String txId;
    private RewardState state ;
    private Long time;
    private String bestHeight;
    private ArrayList<Payment> builderList;
    private ArrayList<Payment> orderViaList;
    private ArrayList<Payment> consumeViaList;
    private ArrayList<Payment> costList;
    private Long apiCost;

    public static final String MAPPINGS = "{\"mappings\":{\"properties\":{\"rewardId\":{\"type\":\"keyword\"},\"rewardT\":{\"type\":\"long\"},\"apiCost\":{\"type\":\"long\"},\"txId\":{\"type\":\"keyword\"},\"state\":{\"type\":\"keyword\"},\"time\":{\"type\":\"long\"},\"bestHeight\":{\"type\":\"keyword\"},\"builderList\":{\"type\":\"nested\",\"properties\":{\"fid\":{\"type\":\"keyword\"},\"share\":{\"type\":\"integer\"},\"fixed\":{\"type\":\"long\"},\"amount\":{\"type\":\"long\"}}},\"orderViaList\":{\"type\":\"nested\",\"properties\":{\"fid\":{\"type\":\"keyword\"},\"share\":{\"type\":\"integer\"},\"fixed\":{\"type\":\"long\"},\"amount\":{\"type\":\"long\"}}},\"consumeViaList\":{\"type\":\"nested\",\"properties\":{\"fid\":{\"type\":\"keyword\"},\"share\":{\"type\":\"integer\"},\"fixed\":{\"type\":\"long\"},\"amount\":{\"type\":\"long\"}}},\"costList\":{\"type\":\"nested\",\"properties\":{\"fid\":{\"type\":\"keyword\"},\"share\":{\"type\":\"integer\"},\"fixed\":{\"type\":\"long\"},\"amount\":{\"type\":\"long\"}}}}}}";
    public Long getTime() {
        return time;
    }

    public void setTime(Long time) {
        this.time = time;
    }

    public ArrayList<Payment> getConsumeViaList() {
        return consumeViaList;
    }

    public void setConsumeViaList(ArrayList<Payment> consumeViaList) {
        this.consumeViaList = consumeViaList;
    }

    public RewardState getState() {
        return state;
    }

    public void setState(RewardState state) {
        this.state = state;
    }

    public String getBestHeight() {
        return bestHeight;
    }

    public void setBestHeight(String bestHeight) {
        this.bestHeight = bestHeight;
    }

    public String getRewardId() {
        return rewardId;
    }

    public void setRewardId(String rewardId) {
        this.rewardId = rewardId;
    }

    public String getTxId() {
        return txId;
    }

    public void setTxId(String txId) {
        this.txId = txId;
    }

    public Long getRewardT() {
        return rewardT;
    }

    public void setRewardT(Long rewardT) {
        this.rewardT = rewardT;
    }

    public ArrayList<Payment> getBuilderList() {
        return builderList;
    }

    public void setBuilderList(ArrayList<Payment> builderList) {
        this.builderList = builderList;
    }

    public ArrayList<Payment> getOrderViaList() {
        return orderViaList;
    }

    public void setOrderViaList(ArrayList<Payment> orderViaList) {
        this.orderViaList = orderViaList;
    }

    public ArrayList<Payment> getCostList() {
        return costList;
    }

    public void setCostList(ArrayList<Payment> costList) {
        this.costList = costList;
    }

    public Long getApiCost() {
        return apiCost;
    }

    public void setApiCost(Long apiCost) {
        this.apiCost = apiCost;
    }
}
