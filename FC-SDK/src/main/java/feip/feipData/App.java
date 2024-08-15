package feip.feipData;

public class App {
	private String aid;
	private String stdName;
	private String[] localNames;
	private String[] types;
	private String desc;
	private String[] urls;
	private Download[] downloads;
	private String[] waiters;
	private String[] protocols;
	private String[] codes;
	private String[] services;
	
	private String owner;
	private long birthTime;
	private long birthHeight;
	private String lastTxId;
	private long lastTime;
	private long lastHeight;
	private long tCdd;
	private float tRate;
	private boolean active;
	private boolean closed;
	private String closeStatement;
	
	public String getAid() {
		return aid;
	}
	public void setAid(String aid) {
		this.aid = aid;
	}
	public String getStdName() {
		return stdName;
	}
	public void setStdName(String stdName) {
		this.stdName = stdName;
	}
	public String[] getLocalNames() {
		return localNames;
	}
	public void setLocalNames(String[] localNames) {
		this.localNames = localNames;
	}
	public String[] getTypes() {
		return types;
	}
	public void setTypes(String[] types) {
		this.types = types;
	}
	public String getDesc() {
		return desc;
	}
	public void setDesc(String desc) {
		this.desc = desc;
	}
	public String[] getUrls() {
		return urls;
	}
	public void setUrls(String[] urls) {
		this.urls = urls;
	}
	public String[] getWaiters() {
		return waiters;
	}
	public void setWaiters(String[] waiters) {
		this.waiters = waiters;
	}
	public String[] getProtocols() {
		return protocols;
	}
	public void setProtocols(String[] protocols) {
		this.protocols = protocols;
	}
	public String[] getServices() {
		return services;
	}
	public void setServices(String[] services) {
		this.services = services;
	}
	public String getOwner() {
		return owner;
	}
	public void setOwner(String owner) {
		this.owner = owner;
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
	public long gettCdd() {
		return tCdd;
	}
	public void settCdd(long tCdd) {
		this.tCdd = tCdd;
	}
	public float gettRate() {
		return tRate;
	}
	public void settRate(float tRate) {
		this.tRate = tRate;
	}
	public boolean isActive() {
		return active;
	}
	public void setActive(boolean active) {
		this.active = active;
	}
	public String[] getCodes() {
		return codes;
	}
	public void setCodes(String[] codes) {
		this.codes = codes;
	}
	public boolean isClosed() {
		return closed;
	}
	public void setClosed(boolean closed) {
		this.closed = closed;
	}
	public String getCloseStatement() {
		return closeStatement;
	}
	public void setCloseStatement(String closeStatement) {
		this.closeStatement = closeStatement;
	}
	public Download[] getDownloads() {
		return downloads;
	}
	public void setDownloads(Download[] downloads) {
		this.downloads = downloads;
	}

	public static class Download {

		private String os;
		private String link;
		private String did;

		public String getOs() {
			return os;
		}
		public void setOs(String os) {
			this.os = os;
		}
		public String getLink() {
			return link;
		}
		public void setLink(String link) {
			this.link = link;
		}

		public String getDid() {
			return did;
		}

		public void setDid(String did) {
			this.did = did;
		}
	}
}
