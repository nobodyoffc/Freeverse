package feip.feipData;

public class ProtocolData {

	private String pid;
	private String op;
	private String type;
	private String sn;
	private String ver;
	private String did;
	private String name;
	private String desc;
	private String[] waiters;
	private String lang;
	private String preDid;
	private String[] fileUrls;
	private int rate;
	private String closeStatement;
	public String[] getWaiters() {
		return waiters;
	}

	public void setWaiters(String[] waiters) {
		this.waiters = waiters;
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
	public String getPreDid() {
		return preDid;
	}
	public void setPreDid(String preDid) {
		this.preDid = preDid;
	}
	public String[] getFileUrls() {
		return fileUrls;
	}
	public void setFileUrls(String[] fileUrls) {
		this.fileUrls = fileUrls;
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
