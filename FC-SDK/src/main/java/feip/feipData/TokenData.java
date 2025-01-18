package feip.feipData;


import fch.fchData.SendTo;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class TokenData {

    private String tokenId;
    private List<String> tokenIds;
    private String op;
    private String name;
    private String desc;
    private String consensusId;
    private String capacity;
    private String decimal;
    private String transferable;
    private String closable;
    private String openIssue;
    private String maxAmtPerIssue;
    private String minCddPerIssue;
    private String maxIssuesPerAddr;
    private List<SendTo> issueTo;
    private List<SendTo> transferTo;

    public enum Op {
        REGISTER("register"),
        ISSUE("issue"),
        TRANSFER("transfer"),
        CLOSE("close");

        private final String value;

        Op(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static Op fromValue(String value) {
            for (Op op : Op.values()) {
                if (op.value.equals(value)) {
                    return op;
                }
            }
            return null;
        }
    }

    public static final Map<String, String[]> OP_FIELDS = new HashMap<>();
    static {
        OP_FIELDS.put(Op.REGISTER.getValue(), new String[]{"tokenId", "name", "desc", "consensusId", "capacity", 
            "decimal", "transferable", "closable", "openIssue", "maxAmtPerIssue", "minCddPerIssue", "maxIssuesPerAddr"});
        OP_FIELDS.put(Op.ISSUE.getValue(), new String[]{"tokenId", "issueTo"});
        OP_FIELDS.put(Op.TRANSFER.getValue(), new String[]{"tokenId", "transferTo"});
        OP_FIELDS.put(Op.CLOSE.getValue(), new String[]{"tokenIds"});
    }

    public static TokenData makeRegister(String tokenId, String name, String desc, String consensusId,
            String capacity, String decimal, String transferable, String closable, String openIssue,
            String maxAmtPerIssue, String minCddPerIssue, String maxIssuesPerAddr) {
        TokenData data = new TokenData();
        data.setOp(Op.REGISTER.getValue());
        data.setTokenId(tokenId);
        data.setName(name);
        data.setDesc(desc);
        data.setConsensusId(consensusId);
        data.setCapacity(capacity);
        data.setDecimal(decimal);
        data.setTransferable(transferable);
        data.setClosable(closable);
        data.setOpenIssue(openIssue);
        data.setMaxAmtPerIssue(maxAmtPerIssue);
        data.setMinCddPerIssue(minCddPerIssue);
        data.setMaxIssuesPerAddr(maxIssuesPerAddr);
        return data;
    }

    public static TokenData makeIssue(String tokenId, List<SendTo> issueTo) {
        TokenData data = new TokenData();
        data.setOp(Op.ISSUE.getValue());
        data.setTokenId(tokenId);
        data.setIssueTo(issueTo);
        return data;
    }

    public static TokenData makeTransfer(String tokenId, List<SendTo> transferTo) {
        TokenData data = new TokenData();
        data.setOp(Op.TRANSFER.getValue());
        data.setTokenId(tokenId);
        data.setTransferTo(transferTo);
        return data;
    }

    public static TokenData makeClose(List<String> tokenIds) {
        TokenData data = new TokenData();
        data.setOp(Op.CLOSE.getValue());
        data.setTokenIds(tokenIds);
        return data;
    }
    public String getTokenId() {
        return tokenId;
    }

    public List<String> getTokenIds() {
        return tokenIds;
    }
    public void setTokenIds(List<String> tokenIds) {
        this.tokenIds = tokenIds;
    }   

    public void setTokenId(String tokenId) {
        this.tokenId = tokenId;
    }

    public String getOp() {
        return op;
    }

    public void setOp(String op) {
        this.op = op;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public String getConsensusId() {
        return consensusId;
    }

    public void setConsensusId(String consensusId) {
        this.consensusId = consensusId;
    }

    public String getCapacity() {
        return capacity;
    }

    public void setCapacity(String capacity) {
        this.capacity = capacity;
    }

    public String getDecimal() {
        return decimal;
    }

    public void setDecimal(String decimal) {
        this.decimal = decimal;
    }

    public String getTransferable() {
        return transferable;
    }

    public void setTransferable(String transferable) {
        this.transferable = transferable;
    }

    public String getClosable() {
        return closable;
    }

    public void setClosable(String closable) {
        this.closable = closable;
    }

    public String getOpenIssue() {
        return openIssue;
    }

    public void setOpenIssue(String openIssue) {
        this.openIssue = openIssue;
    }

    public String getMaxAmtPerIssue() {
        return maxAmtPerIssue;
    }

    public void setMaxAmtPerIssue(String maxAmtPerIssue) {
        this.maxAmtPerIssue = maxAmtPerIssue;
    }

    public String getMinCddPerIssue() {
        return minCddPerIssue;
    }

    public void setMinCddPerIssue(String minCddPerIssue) {
        this.minCddPerIssue = minCddPerIssue;
    }

    public List<SendTo> getIssueTo() {
        return issueTo;
    }

    public void setIssueTo(List<SendTo> issueTo) {
        this.issueTo = issueTo;
    }

    public List<SendTo> getTransferTo() {
        return transferTo;
    }

    public void setTransferTo(List<SendTo> transferTo) {
        this.transferTo = transferTo;
    }

    public String getMaxIssuesPerAddr() {
        return maxIssuesPerAddr;
    }

    public void setMaxIssuesPerAddr(String maxIssuesPerAddr) {
        this.maxIssuesPerAddr = maxIssuesPerAddr;
    }
}
