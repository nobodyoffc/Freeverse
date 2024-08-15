package feip.feipData;

public class CodeHistory {
	
	private String txId;
	private long height;
	private int index;
	private long time;
	private String signer;

	private String codeId;
	private String op;
	private String name;
	private String version;
	private String did;
	private String desc;
	private String[] langs;
	private String[] urls;
	private String[] protocols;
	private String[] waiters;
	private int rate;
	private String closeStatement;
	
	private long cdd;

	public String getTxId() {
		return txId;
	}

	public void setTxId(String txId) {
		this.txId = txId;
	}

	public long getHeight() {
		return height;
	}

	public void setHeight(long height) {
		this.height = height;
	}

	public int getIndex() {
		return index;
	}

	public void setIndex(int index) {
		this.index = index;
	}

	public long getTime() {
		return time;
	}

	public void setTime(long time) {
		this.time = time;
	}

	public String getSigner() {
		return signer;
	}

	public void setSigner(String signer) {
		this.signer = signer;
	}

	public String getCodeId() {
		return codeId;
	}

	public void setCodeId(String codeId) {
		this.codeId = codeId;
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

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public String getDid() {
		return did;
	}

	public void setDid(String did) {
		this.did = did;
	}

	public String getDesc() {
		return desc;
	}

	public void setDesc(String desc) {
		this.desc = desc;
	}

	public String[] getLangs() {
		return langs;
	}

	public void setLangs(String[] langs) {
		this.langs = langs;
	}

	public String[] getUrls() {
		return urls;
	}

	public void setUrls(String[] urls) {
		this.urls = urls;
	}

	public String[] getProtocols() {
		return protocols;
	}

	public void setProtocols(String[] protocols) {
		this.protocols = protocols;
	}

	public String[] getWaiters() {
		return waiters;
	}

	public void setWaiters(String[] waiters) {
		this.waiters = waiters;
	}

	public int getRate() {
		return rate;
	}

	public void setRate(int rate) {
		this.rate = rate;
	}

	public long getCdd() {
		return cdd;
	}

	public void setCdd(long cdd) {
		this.cdd = cdd;
	}

	public String getCloseStatement() {
		return closeStatement;
	}

	public void setCloseStatement(String closeStatement) {
		this.closeStatement = closeStatement;
	}
	
	
}
