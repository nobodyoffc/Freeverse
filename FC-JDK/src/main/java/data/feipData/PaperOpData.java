package data.feipData;

import constants.FieldNames;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PaperOpData {
	
	private String paperId;
	private String[] paperIds;

	private String op;

	private String title;
	private String summary;
	private List<String> keywords;
	private String did;
	private String ver;
	private List<String> authors;
	private String lang;

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
		OP_FIELDS.put(Op.PUBLISH.toLowerCase(), new String[]{FieldNames.TITLE, FieldNames.DID, FieldNames.LANG, FieldNames.AUTHORS, FieldNames.KEYWORDS, FieldNames.SUMMARY});
		OP_FIELDS.put(Op.UPDATE.toLowerCase(), new String[]{FieldNames.PAPER_ID, FieldNames.TITLE, FieldNames.DID, FieldNames.LANG, FieldNames.AUTHORS, FieldNames.KEYWORDS, FieldNames.SUMMARY});
		OP_FIELDS.put(Op.DELETE.toLowerCase(), new String[]{FieldNames.PAPER_IDS});
		OP_FIELDS.put(Op.RECOVER.toLowerCase(), new String[]{FieldNames.PAPER_IDS});
		OP_FIELDS.put(Op.RATE.toLowerCase(), new String[]{FieldNames.PAPER_ID, FieldNames.RATE});
	}

	// Factory methods
	public static PaperOpData makePublish(String title, String version, String did, String desc,
                                          String lang, String[] urls, String[] protocols, String[] waiters) {
		PaperOpData data = new PaperOpData();
		data.setOp(Op.PUBLISH.toLowerCase());
		data.setTitle(title);
		data.setDid(did);
		data.setVer(version);
		data.setLang(lang);
		return data;
	}

	public static PaperOpData makeUpdate(String paperId, String name, String version, String did, String desc,
                                         String[] langs, String[] urls, String[] protocols, String[] waiters) {
		PaperOpData data = new PaperOpData();
		data.setOp(Op.UPDATE.toLowerCase());
		data.setPaperId(paperId);
		data.setTitle(name);
		data.setDid(did);
		data.setVer(version);
		return data;
	}

	public static PaperOpData makeDelete(String[] paperIds) {
		PaperOpData data = new PaperOpData();
		data.setOp(Op.DELETE.toLowerCase());
		data.setPaperIds(paperIds);
		return data;
	}


	public static PaperOpData makeRecover(String[] paperIds) {
		PaperOpData data = new PaperOpData();
		data.setOp(Op.DELETE.toLowerCase());
		data.setPaperIds(paperIds);
		return data;
	}

	public static PaperOpData makeRate(String paperId, Integer rate) {
		PaperOpData data = new PaperOpData();
		data.setOp(Op.RATE.toLowerCase());
		data.setPaperId(paperId);
		data.setRate(rate);
		return data;
	}

	public String getPaperId() {
		return paperId;
	}
	public void setPaperId(String paperId) {
		this.paperId = paperId;
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

	public String[] getPaperIds() {
		return paperIds;
	}

	public void setPaperIds(String[] paperIds) {
		this.paperIds = paperIds;
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

	public List<String> getKeywords() {
		return keywords;
	}

	public void setKeywords(List<String> keywords) {
		this.keywords = keywords;
	}

	public String getSummary() {
		return summary;
	}

	public void setSummary(String summary) {
		this.summary = summary;
	}
} 