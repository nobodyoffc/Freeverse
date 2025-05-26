package data.feipData;


import constants.Values;
import data.fchData.SendTo;

import java.util.List;
import java.util.Map;

import constants.FieldNames;

import java.util.HashMap;

public class TokenOpData {

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
        REGISTER(FeipOp.REGISTER),
        ISSUE(FeipOp.ISSUE),
        TRANSFER(FeipOp.TRANSFER),
        CLOSE(FeipOp.CLOSE);

		private final FeipOp feipOp;

		Op(FeipOp feipOp) {
			this.feipOp = feipOp;
		}

		public FeipOp getFeipOp() {
			return feipOp;
		}

        public static Op fromValue(String value) {
            for (Op op : Op.values()) {
                if (op.getFeipOp().getValue().equals(value)) {
                    return op;
                }
            }
            return null;
        }
        public String toLowerCase() {
			return feipOp.getValue().toLowerCase();
		}
    }

    public static final Map<String, String[]> OP_FIELDS = new HashMap<>();
    static {
        OP_FIELDS.put(Op.REGISTER.toLowerCase(), new String[]{FieldNames.TOKEN_ID, FieldNames.NAME, Values.DESC, FieldNames.CONSENSUS_ID, FieldNames.CAPACITY,
            FieldNames.DECIMAL, FieldNames.TRANSFERABLE, FieldNames.CLOSABLE, FieldNames.OPEN_ISSUE, FieldNames.MAX_AMT_PER_ISSUE, FieldNames.MIN_CDD_PER_ISSUE, FieldNames.MAX_ISSUES_PER_ADDR});
        OP_FIELDS.put(Op.ISSUE.toLowerCase(), new String[]{FieldNames.TOKEN_ID, FieldNames.ISSUE_TO});
        OP_FIELDS.put(Op.TRANSFER.toLowerCase(), new String[]{FieldNames.TOKEN_ID, FieldNames.TRANSFER_TO});
        OP_FIELDS.put(Op.CLOSE.toLowerCase(), new String[]{FieldNames.TOKEN_IDS});
    }

    public static TokenOpData makeRegister(String tokenId, String name, String desc, String consensusId,
                                           String capacity, String decimal, String transferable, String closable, String openIssue,
                                           String maxAmtPerIssue, String minCddPerIssue, String maxIssuesPerAddr) {
        TokenOpData data = new TokenOpData();
        data.setOp(Op.REGISTER.toLowerCase());
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

    public static TokenOpData makeIssue(String tokenId, List<SendTo> issueTo) {
        TokenOpData data = new TokenOpData();
        data.setOp(Op.ISSUE.toLowerCase());
        data.setTokenId(tokenId);
        data.setIssueTo(issueTo);
        return data;
    }

    public static TokenOpData makeTransfer(String tokenId, List<SendTo> transferTo) {
        TokenOpData data = new TokenOpData();
        data.setOp(Op.TRANSFER.toLowerCase());
        data.setTokenId(tokenId);
        data.setTransferTo(transferTo);
        return data;
    }

    public static TokenOpData makeClose(List<String> tokenIds) {
        TokenOpData data = new TokenOpData();
        data.setOp(Op.CLOSE.toLowerCase());
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
