package feip.feipData;

import java.util.HashMap;
import java.util.Map;

import feip.FeipOp;

public class CodeData {
	
	private String codeId;
	private String[] codeIds;

	private String op;
	private String name;
	private String did;
	private String ver;
	private String desc;
	private String[] langs;
	private String[] urls;
	private String[] protocols;
	private String[] waiters;
	private Integer rate;
	private String closeStatement;

	public enum Op {
		PUBLISH(FeipOp.PUBLISH),
		UPDATE(FeipOp.UPDATE),
		STOP(FeipOp.STOP),
		CLOSE(FeipOp.CLOSE),
		RECOVER(FeipOp.RECOVER),
		RATE(FeipOp.RATE);

		private final FeipOp feipOp;

		Op(FeipOp feipOp) {
			this.feipOp = feipOp;
		}

		public FeipOp getFeipOp() {
			return feipOp;
		}

		public static Op fromString(String text) {
			for (Op op : Op.values()) {
				if (op.name().equalsIgnoreCase(text)) {
					return op;
				}
			}
			throw new IllegalArgumentException("No constant with text " + text + " found");
		}

		public String toLowerCase() {
			return this.name().toLowerCase();
		}
	}

	public static final Map<String, String[]> OP_FIELDS = new HashMap<>();

	static {
		OP_FIELDS.put(Op.PUBLISH.toLowerCase(), new String[]{"name", "ver", "did", "desc", "langs", "urls", "protocols", "waiters"});
		OP_FIELDS.put(Op.UPDATE.toLowerCase(), new String[]{"codeId", "name", "ver", "did", "desc", "langs", "urls", "protocols", "waiters"});
		OP_FIELDS.put(Op.STOP.toLowerCase(), new String[]{"codeIds"});
		OP_FIELDS.put(Op.CLOSE.toLowerCase(), new String[]{"codeIds", "closeStatement"});
		OP_FIELDS.put(Op.RECOVER.toLowerCase(), new String[]{"codeIds"});
		OP_FIELDS.put(Op.RATE.toLowerCase(), new String[]{"codeId", "rate"});
	}

	// Factory methods
	public static CodeData makePublish(String name, String version, String did, String desc, 
			String[] langs, String[] urls, String[] protocols, String[] waiters) {
		CodeData data = new CodeData();
		data.setOp(Op.PUBLISH.toLowerCase());
		data.setName(name);
		data.setDid(did);
		data.setVer(version);
		data.setDesc(desc);
		data.setLangs(langs);
		data.setUrls(urls);
		data.setProtocols(protocols);
		data.setWaiters(waiters);
		return data;
	}

	public static CodeData makeUpdate(String codeId, String name, String version, String did, String desc, 
			String[] langs, String[] urls, String[] protocols, String[] waiters) {
		CodeData data = new CodeData();
		data.setOp(Op.UPDATE.toLowerCase());
		data.setCodeId(codeId);
		data.setName(name);
		data.setDid(did);
		data.setVer(version);
		data.setDesc(desc);
		data.setLangs(langs);
		data.setUrls(urls);
		data.setProtocols(protocols);
		data.setWaiters(waiters);
		return data;
	}

	public static CodeData makeStop(String[] codeIds) {
		CodeData data = new CodeData();
		data.setOp(Op.STOP.toLowerCase());
		data.setCodeIds(codeIds);
		return data;
	}

	public static CodeData makeClose(String[] codeIds, String closeStatement) {
		CodeData data = new CodeData();
		data.setOp(Op.CLOSE.toLowerCase());
		data.setCodeIds(codeIds);
		data.setCloseStatement(closeStatement);
		return data;
	}

	public static CodeData makeRecover(String[] codeIds) {
		CodeData data = new CodeData();
		data.setOp(Op.RECOVER.toLowerCase());
		data.setCodeIds(codeIds);
		return data;
	}

	public static CodeData makeRate(String codeId, Integer rate) {
		CodeData data = new CodeData();
		data.setOp(Op.RATE.toLowerCase());
		data.setCodeId(codeId);
		data.setRate(rate);
		return data;
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
	public Integer getRate() {
		return rate;
	}
	public void setRate(Integer rate) {
		this.rate = rate;
	}
	public String getCloseStatement() {
		return closeStatement;
	}
	public void setCloseStatement(String closeStatement) {
		this.closeStatement = closeStatement;
	}



	public String getVer() {
		return ver;
	}

	public void setVer(String ver) {
		this.ver = ver;
	}

	public String[] getCodeIds() {
		return codeIds;
	}

	public void setCodeIds(String[] codeIds) {
		this.codeIds = codeIds;
	}
}
