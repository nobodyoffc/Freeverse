package data.feipData;

import constants.FieldNames;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TextOpData {

	private String textId;
	private List<String> textIds;
	private String type;

	private String op;

	private String title;
	private String did;
	private String ver;
	private List<String> authors;
	private String lang;
	private String format;
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
		OP_FIELDS.put(Op.PUBLISH.toLowerCase(), new String[]{FieldNames.TITLE, FieldNames.TYPE, FieldNames.DID, FieldNames.LANG, FieldNames.AUTHORS, FieldNames.SUMMARY});
		OP_FIELDS.put(Op.UPDATE.toLowerCase(), new String[]{FieldNames.TEXT_ID, FieldNames.TITLE, FieldNames.TYPE, FieldNames.DID, FieldNames.LANG, FieldNames.AUTHORS, FieldNames.SUMMARY});
		OP_FIELDS.put(Op.DELETE.toLowerCase(), new String[]{FieldNames.TEXT_IDS});
		OP_FIELDS.put(Op.RECOVER.toLowerCase(), new String[]{FieldNames.TEXT_IDS});
		OP_FIELDS.put(Op.RATE.toLowerCase(), new String[]{FieldNames.TEXT_ID, FieldNames.RATE});
	}

	// Factory methods
	public static TextOpData makePublish(String title, String version, String did, String desc,
										 String lang, String[] urls, String[] protocols, String[] waiters) {
		TextOpData data = new TextOpData();
		data.setOp(Op.PUBLISH.toLowerCase());
		data.setTitle(title);
		data.setDid(did);
		data.setVer(version);
		data.setLang(lang);
		return data;
	}

	public static TextOpData makeUpdate(String textId, String name, String version, String did, String desc,
										String[] langs, String[] urls, String[] protocols, String[] waiters) {
		TextOpData data = new TextOpData();
		data.setOp(Op.UPDATE.toLowerCase());
		data.setTextId(textId);
		data.setTitle(name);
		data.setDid(did);
		data.setVer(version);
		return data;
	}

	public static TextOpData makeDelete(List<String> textIds) {
		TextOpData data = new TextOpData();
		data.setOp(Op.DELETE.toLowerCase());
		data.setTextIds(textIds);
		return data;
	}


	public static TextOpData makeRecover(List<String> textIds) {
		TextOpData data = new TextOpData();
		data.setOp(Op.RECOVER.toLowerCase());
		data.setTextIds(textIds);
		return data;
	}

	public static TextOpData makeRate(String textId, Integer rate) {
		TextOpData data = new TextOpData();
		data.setOp(Op.RATE.toLowerCase());
		data.setTextId(textId);
		data.setRate(rate);
		return data;
	}

	public String getTextId() {
		return textId;
	}
	public void setTextId(String textId) {
		this.textId = textId;
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

	public String getVer() {
		return ver;
	}

	public void setVer(String ver) {
		this.ver = ver;
	}

	public List<String> getTextIds() {
		return textIds;
	}

	public void setTextIds(List<String> textIds) {
		this.textIds = textIds;
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

	public String getFormat() {
		return format;
	}

	public void setFormat(String format) {
		this.format = format;
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

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}
}