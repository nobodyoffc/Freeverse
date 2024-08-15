package feip.feipData;

public class Token {

	private String tokenId;
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
	private String closed;
	
	private String deployer;
	private double circulating;
	private long birthTime;
	private long birthHeight;
	private String lastTxId;
	private long lastTime;
	private long lastHeight;

	public String getTokenId() {
		return tokenId;
	}

	public void setTokenId(String tokenId) {
		this.tokenId = tokenId;
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

	public String getClosed() {
		return closed;
	}

	public void setClosed(String closed) {
		this.closed = closed;
	}

	public String getDeployer() {
		return deployer;
	}

	public void setDeployer(String deployer) {
		this.deployer = deployer;
	}

	public double getCirculating() {
		return circulating;
	}

	public void setCirculating(double circulating) {
		this.circulating = circulating;
	}

	public long getBirthTime() {
		return birthTime;
	}

	public void setBirthTime(long birthTime) {
		this.birthTime = birthTime;
	}

	public long getBirthHeight() {
		return birthHeight;
	}

	public void setBirthHeight(long birthHeight) {
		this.birthHeight = birthHeight;
	}

	public String getLastTxId() {
		return lastTxId;
	}

	public void setLastTxId(String lastTxId) {
		this.lastTxId = lastTxId;
	}

	public long getLastTime() {
		return lastTime;
	}

	public void setLastTime(long lastTime) {
		this.lastTime = lastTime;
	}

	public long getLastHeight() {
		return lastHeight;
	}

	public void setLastHeight(long lastHeight) {
		this.lastHeight = lastHeight;
	}
}
