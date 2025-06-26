package data.feipData;

import data.fcData.FcObject;

public class Token extends FcObject {
	private String name;
	private String desc;
	private String consensusId;
	private String capacity;
	private String decimal;
	private Boolean transferable;
	private Boolean closable;
	private Boolean openIssue;
	private String maxAmtPerIssue;
	private String minCddPerIssue;
	private String maxIssuesPerAddr;
	private Boolean closed;
	
	private String deployer;
	private Double circulating;
	private Long birthTime;
	private Long birthHeight;
	private String lastTxId;
	private Long lastTime;
	private Long lastHeight;

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

	public Boolean getTransferable() {
		return transferable;
	}

	public void setTransferable(Boolean transferable) {
		this.transferable = transferable;
	}

	public Boolean getClosable() {
		return closable;
	}

	public void setClosable(Boolean closable) {
		this.closable = closable;
	}

	public Boolean getOpenIssue() {
		return openIssue;
	}

	public void setOpenIssue(Boolean openIssue) {
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

	public Boolean getClosed() {
		return closed;
	}

	public void setClosed(Boolean closed) {
		this.closed = closed;
	}

	public String getDeployer() {
		return deployer;
	}

	public void setDeployer(String deployer) {
		this.deployer = deployer;
	}

	public Double getCirculating() {
		return circulating;
	}

	public void setCirculating(Double circulating) {
		this.circulating = circulating;
	}

	public Long getBirthTime() {
		return birthTime;
	}

	public void setBirthTime(Long birthTime) {
		this.birthTime = birthTime;
	}

	public Long getBirthHeight() {
		return birthHeight;
	}

	public void setBirthHeight(Long birthHeight) {
		this.birthHeight = birthHeight;
	}

	public String getLastTxId() {
		return lastTxId;
	}

	public void setLastTxId(String lastTxId) {
		this.lastTxId = lastTxId;
	}

	public Long getLastTime() {
		return lastTime;
	}

	public void setLastTime(Long lastTime) {
		this.lastTime = lastTime;
	}

	public Long getLastHeight() {
		return lastHeight;
	}

	public void setLastHeight(Long lastHeight) {
		this.lastHeight = lastHeight;
	}
}
