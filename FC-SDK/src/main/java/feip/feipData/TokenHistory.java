package feip.feipData;

import fch.fchData.SendTo;

import java.util.List;

public class TokenHistory {
	
	private String txId;
	private List<String> tokenIds;
	private Long height;
	private Integer index;
	private Long time;
	private String signer;
	private String recipient;
	private Long cdd;

	private String tokenId;
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


	public String getTxId() {
		return txId;
	}

	public void setTxId(String txId) {
		this.txId = txId;
	}

	public Long getHeight() {
		return height;
	}

	public void setHeight(Long height) {
		this.height = height;
	}

	public Integer getIndex() {
		return index;
	}

	public void setIndex(Integer index) {
		this.index = index;
	}

	public Long getTime() {
		return time;
	}

	public void setTime(Long time) {
		this.time = time;
	}

	public String getSigner() {
		return signer;
	}

	public void setSigner(String signer) {
		this.signer = signer;
	}

	public String getRecipient() {
		return recipient;
	}

	public void setRecipient(String recipient) {
		this.recipient = recipient;
	}

	public String getTokenId() {
		return tokenId;
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

	public String getMaxIssuesPerAddr() {
		return maxIssuesPerAddr;
	}

	public void setMaxIssuesPerAddr(String maxIssuesPerAddr) {
		this.maxIssuesPerAddr = maxIssuesPerAddr;
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

	public Long getCdd() {
		return cdd;
	}

	public void setCdd(Long cdd) {
		this.cdd = cdd;
	}

    public List<String> getTokenIds() {
        return tokenIds;
    }

    public void setTokenIds(List<String> tokenIds) {
        this.tokenIds = tokenIds;
    }
	
}
