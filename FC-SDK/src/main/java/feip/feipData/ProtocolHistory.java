package feip.feipData;

public class ProtocolHistory {
	
	private String txId;
	private long height;
	private int index;
	private long time;
	private String signer;

	
	private String type;
	private String sn;
	private String ver;
	private String did;
	private String name;
	private String desc;
	private String lang;
	private String prePid;
	private String[] fileUrls;
	private String pid;
	private String op;
	private int rate;
	private long cdd;
	private String closeStatement;

	private String[] waiters;

	public String[] getWaiters() {
		return waiters;
	}

	public void setWaiters(String[] waiters) {
		this.waiters = waiters;
	}

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
	public String getType() {
		return type;
	}
	public void setType(String type) {
		this.type = type;
	}
	public String getSn() {
		return sn;
	}
	public void setSn(String sn) {
		this.sn = sn;
	}
	public String getVer() {
		return ver;
	}
	public void setVer(String ver) {
		this.ver = ver;
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
	public String getLang() {
		return lang;
	}
	public void setLang(String lang) {
		this.lang = lang;
	}
	public String getPrePid() {
		return prePid;
	}
	public void setPrePid(String prePid) {
		this.prePid = prePid;
	}
	public String[] getFileUrls() {
		return fileUrls;
	}
	public void setFileUrls(String[] fileUrls) {
		this.fileUrls = fileUrls;
	}
	public String getSigner() {
		return signer;
	}
	public void setSigner(String signer) {
		this.signer = signer;
	}
	public String getPid() {
		return pid;
	}
	public void setPid(String pid) {
		this.pid = pid;
	}
	public String getOp() {
		return op;
	}
	public void setOp(String op) {
		this.op = op;
	}
	public long getCdd() {
		return cdd;
	}
	public void setCdd(long cdd) {
		this.cdd = cdd;
	}
	public int getRate() {
		return rate;
	}
	public void setRate(int rate) {
		this.rate = rate;
	}
	public String getDid() {
		return did;
	}
	public void setDid(String did) {
		this.did = did;
	}
	public String getCloseStatement() {
		return closeStatement;
	}
	public void setCloseStatement(String closeStatement) {
		this.closeStatement = closeStatement;
	}

}
