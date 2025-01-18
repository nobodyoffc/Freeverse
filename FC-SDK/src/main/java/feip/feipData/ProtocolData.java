package feip.feipData;

import java.util.HashMap;
import java.util.Map;

public class ProtocolData {

	private String pid;
	private String[] pids;
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

	public enum Op {
		PUBLISH("publish"),
		UPDATE("update"),
		STOP("stop"),
		CLOSE("close"),
		RECOVER("recover"),
		RATE("rate");

		private final String value;

		Op(String value) {
			this.value = value;
		}

		public String getValue() {
			return value;
		}

		public static Op fromString(String text) {
			for (Op op : Op.values()) {
				if (op.getValue().equalsIgnoreCase(text)) {
					return op;
				}
			}
			throw new IllegalArgumentException("No constant with text " + text + " found");
		}
	}

	public static final Map<String, String[]> OP_FIELDS = new HashMap<>();

	static {
		OP_FIELDS.put(Op.PUBLISH.getValue(), new String[]{"sn", "name", "type", "ver", "did", "desc", "lang", "fileUrls", "preDid", "waiters"});
		OP_FIELDS.put(Op.UPDATE.getValue(), new String[]{"pid", "sn", "name", "type", "ver", "did", "desc", "lang", "fileUrls", "preDid", "waiters"});
		OP_FIELDS.put(Op.STOP.getValue(), new String[]{"pids"});
		OP_FIELDS.put(Op.CLOSE.getValue(), new String[]{"pids", "closeStatement"});
		OP_FIELDS.put(Op.RECOVER.getValue(), new String[]{ "pids"});
		OP_FIELDS.put(Op.RATE.getValue(), new String[]{"pid", "rate"});
	}

	public static ProtocolData makePublish(String sn, String name, String type, String ver, String did,
			String desc, String lang, String[] fileUrls, String preDid, String[] waiters) {
		ProtocolData data = new ProtocolData();
		data.setOp(Op.PUBLISH.getValue());
		data.setSn(sn);
		data.setName(name);
		data.setType(type);
		data.setVer(ver);
		data.setDid(did);
		data.setDesc(desc);
		data.setLang(lang);
		data.setFileUrls(fileUrls);
		data.setPreDid(preDid);
		data.setWaiters(waiters);
		return data;
	}

	public static ProtocolData makeUpdate(String pid, String sn, String name, String type, String ver, 
			String did, String desc, String lang, String[] fileUrls, String preDid, String[] waiters) {
		ProtocolData data = new ProtocolData();
		data.setOp(Op.UPDATE.getValue());
		data.setPid(pid);
		data.setSn(sn);
		data.setName(name);
		data.setType(type);
		data.setVer(ver);
		data.setDid(did);
		data.setDesc(desc);
		data.setLang(lang);
		data.setFileUrls(fileUrls);
		data.setPreDid(preDid);
		data.setWaiters(waiters);
		return data;
	}

	public static ProtocolData makeStop(String[] pids) {
		ProtocolData data = new ProtocolData();
		data.setOp(Op.STOP.getValue());
		data.setPids(pids);
		return data;
	}

	public static ProtocolData makeClose(String[] pids, String closeStatement) {
		ProtocolData data = new ProtocolData();
		data.setOp(Op.CLOSE.getValue());
		data.setPids(pids);
		data.setCloseStatement(closeStatement);
		return data;
	}

	public static ProtocolData makeRecover(String[] pids) {
		ProtocolData data = new ProtocolData();
		data.setOp(Op.RECOVER.getValue());
		data.setPids(pids);
		return data;
	}

	public static ProtocolData makeRate(String pid, int rate) {
		ProtocolData data = new ProtocolData();
		data.setOp(Op.RATE.getValue());
		data.setPid(pid);
		data.setRate(rate);
		return data;
	}

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

	public String[] getPids() {
		return pids;
	}

	public void setPids(String[] pids) {
		this.pids = pids;
	}
}
