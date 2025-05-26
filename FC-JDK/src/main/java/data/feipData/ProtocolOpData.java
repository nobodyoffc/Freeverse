package data.feipData;

import java.util.HashMap;
import java.util.Map;

import constants.FieldNames;
import constants.Values;

public class ProtocolOpData {

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
				if (op.getFeipOp().equals(text)) {
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
		OP_FIELDS.put(Op.PUBLISH.toLowerCase(), new String[]{FieldNames.SN, FieldNames.NAME, FieldNames.TYPE, FieldNames.VER, FieldNames.DID, Values.DESC, FieldNames.LANG, FieldNames.FILE_URLS, FieldNames.PRE_DID, FieldNames.WAITERS});
		OP_FIELDS.put(Op.UPDATE.toLowerCase(), new String[]{FieldNames.PID, FieldNames.SN, FieldNames.NAME, FieldNames.TYPE, FieldNames.VER, FieldNames.DID, Values.DESC, FieldNames.LANG, FieldNames.FILE_URLS, FieldNames.PRE_DID, FieldNames.WAITERS});
		OP_FIELDS.put(Op.STOP.toLowerCase(), new String[]{FieldNames.PIDS});
		OP_FIELDS.put(Op.CLOSE.toLowerCase(), new String[]{FieldNames.PIDS, FieldNames.CLOSE_STATEMENT});
		OP_FIELDS.put(Op.RECOVER.toLowerCase(), new String[]{FieldNames.PIDS});
		OP_FIELDS.put(Op.RATE.toLowerCase(), new String[]{FieldNames.PID, FieldNames.RATE});
	}

	public static ProtocolOpData makePublish(String sn, String name, String type, String ver, String did,
                                             String desc, String lang, String[] fileUrls, String preDid, String[] waiters) {
		ProtocolOpData data = new ProtocolOpData();
		data.setOp(Op.PUBLISH.toLowerCase());
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

	public static ProtocolOpData makeUpdate(String pid, String sn, String name, String type, String ver,
                                            String did, String desc, String lang, String[] fileUrls, String preDid, String[] waiters) {
		ProtocolOpData data = new ProtocolOpData();
		data.setOp(Op.UPDATE.toLowerCase());
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

	public static ProtocolOpData makeStop(String[] pids) {
		ProtocolOpData data = new ProtocolOpData();
		data.setOp(Op.STOP.toLowerCase());
		data.setPids(pids);
		return data;
	}

	public static ProtocolOpData makeClose(String[] pids, String closeStatement) {
		ProtocolOpData data = new ProtocolOpData();
		data.setOp(Op.CLOSE.toLowerCase());
		data.setPids(pids);
		data.setCloseStatement(closeStatement);
		return data;
	}

	public static ProtocolOpData makeRecover(String[] pids) {
		ProtocolOpData data = new ProtocolOpData();
		data.setOp(Op.RECOVER.toLowerCase());
		data.setPids(pids);
		return data;
	}

	public static ProtocolOpData makeRate(String pid, int rate) {
		ProtocolOpData data = new ProtocolOpData();
		data.setOp(Op.RATE.toLowerCase());
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
