package data.feipData;

import constants.FieldNames;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RemarkOpData {
	
	private String remarkId;
	private String[] remarkIds;

	private String op;

	private String title;
	private String did;
	private String onDid;
	private String ver;
	private List<String> authors;
	private String lang;
	private String summary;

	private Integer rate;

	public enum Op {
		PUBLISH(FeipOp.PUBLISH),
		UPDATE(FeipOp.UPDATE),
		DELETE(FeipOp.DELETE),
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
			return feipOp.getValue().toLowerCase();
		}
	}

	public static final Map<String, String[]> OP_FIELDS = new HashMap<>();

	static {
		OP_FIELDS.put(Op.PUBLISH.toLowerCase(), new String[]{FieldNames.TITLE, FieldNames.DID, FieldNames.ON_DID, FieldNames.LANG, FieldNames.AUTHORS, FieldNames.SUMMARY});
		OP_FIELDS.put(Op.UPDATE.toLowerCase(), new String[]{FieldNames.REMARK_ID, FieldNames.TITLE, FieldNames.DID, FieldNames.ON_DID, FieldNames.LANG, FieldNames.AUTHORS, FieldNames.SUMMARY});
		OP_FIELDS.put(Op.DELETE.toLowerCase(), new String[]{FieldNames.REMARK_IDS});
		OP_FIELDS.put(Op.RECOVER.toLowerCase(), new String[]{FieldNames.REMARK_IDS});
		OP_FIELDS.put(Op.RATE.toLowerCase(), new String[]{FieldNames.REMARK_ID, FieldNames.RATE});
	}

	// Factory methods
	public static RemarkOpData makePublish(String title, String version, String did, String onDid, String desc,
                                          String lang, String[] urls, String[] protocols, String[] waiters) {
		RemarkOpData data = new RemarkOpData();
		data.setOp(Op.PUBLISH.toLowerCase());
		data.setTitle(title);
		data.setDid(did);
		data.setOnDid(onDid);
		data.setVer(version);
		data.setLang(lang);
		return data;
	}

	public static RemarkOpData makeUpdate(String remarkId, String name, String version, String did, String onDid, String desc,
                                         String[] langs, String[] urls, String[] protocols, String[] waiters) {
		RemarkOpData data = new RemarkOpData();
		data.setOp(Op.UPDATE.toLowerCase());
		data.setRemarkId(remarkId);
		data.setTitle(name);
		data.setDid(did);
		data.setOnDid(onDid);
		data.setVer(version);
		return data;
	}

	public static RemarkOpData makeDelete(String[] remarkIds) {
		RemarkOpData data = new RemarkOpData();
		data.setOp(Op.DELETE.toLowerCase());
		data.setRemarkIds(remarkIds);
		return data;
	}

	public static RemarkOpData makeRecover(String[] remarkIds) {
		RemarkOpData data = new RemarkOpData();
		data.setOp(Op.RECOVER.toLowerCase());
		data.setRemarkIds(remarkIds);
		return data;
	}

	public static RemarkOpData makeRate(String remarkId, Integer rate) {
		RemarkOpData data = new RemarkOpData();
		data.setOp(Op.RATE.toLowerCase());
		data.setRemarkId(remarkId);
		data.setRate(rate);
		return data;
	}

	public String getRemarkId() {
		return remarkId;
	}
	public void setRemarkId(String remarkId) {
		this.remarkId = remarkId;
	}
	public String getOp() {
		return op;
	}
	public void setOp(String op) {
		this.op = op;
	}
	public String getTitle() {
		return title;
	}
	public void setTitle(String title) {
		this.title = title;
	}
	public String getDid() {
		return did;
	}
	public void setDid(String did) {
		this.did = did;
	}
	public String getOnDid() {
		return onDid;
	}
	public void setOnDid(String onDid) {
		this.onDid = onDid;
	}
	public String getVer() {
		return ver;
	}
	public void setVer(String ver) {
		this.ver = ver;
	}
	public String[] getRemarkIds() {
		return remarkIds;
	}
	public void setRemarkIds(String[] remarkIds) {
		this.remarkIds = remarkIds;
	}
	public List<String> getAuthors() {
		return authors;
	}
	public void setAuthors(List<String> authors) {
		this.authors = authors;
	}
	public String getLang() {
		return lang;
	}
	public void setLang(String lang) {
		this.lang = lang;
	}
	public Integer getRate() {
		return rate;
	}
	public void setRate(Integer rate) {
		this.rate = rate;
	}
	public String getSummary() {
		return summary;
	}
	public void setSummary(String summary) {
		this.summary = summary;
	}
} 