package data.feipData;

import constants.FieldNames;
import constants.Values;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReportOpData {
	
	private String reportId;
	private String[] reportIds;

	private String op;

	private String title;
	private String did;
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
		OP_FIELDS.put(Op.PUBLISH.toLowerCase(), new String[]{FieldNames.NAME, FieldNames.VER, FieldNames.DID, Values.DESC, FieldNames.LANGS, FieldNames.URLS, FieldNames.PROTOCOLS, FieldNames.WAITERS});
		OP_FIELDS.put(Op.UPDATE.toLowerCase(), new String[]{FieldNames.CODE_ID, FieldNames.NAME, FieldNames.VER, FieldNames.DID, Values.DESC, FieldNames.LANGS, FieldNames.URLS, FieldNames.PROTOCOLS, FieldNames.WAITERS});
		OP_FIELDS.put(Op.DELETE.toLowerCase(), new String[]{FieldNames.CODE_IDS});
		OP_FIELDS.put(Op.RECOVER.toLowerCase(), new String[]{FieldNames.CODE_IDS});
		OP_FIELDS.put(Op.RATE.toLowerCase(), new String[]{FieldNames.CODE_ID, FieldNames.RATE});
	}

	// Factory methods
	public static ReportOpData makePublish(String title, String version, String did, String desc,
                                          String lang, String[] urls, String[] protocols, String[] waiters) {
		ReportOpData data = new ReportOpData();
		data.setOp(Op.PUBLISH.toLowerCase());
		data.setTitle(title);
		data.setDid(did);
		data.setVer(version);
		data.setLang(lang);
		return data;
	}

	public static ReportOpData makeUpdate(String reportId, String name, String version, String did, String desc,
                                         String[] langs, String[] urls, String[] protocols, String[] waiters) {
		ReportOpData data = new ReportOpData();
		data.setOp(Op.UPDATE.toLowerCase());
		data.setReportId(reportId);
		data.setTitle(name);
		data.setDid(did);
		data.setVer(version);
		return data;
	}

	public static ReportOpData makeDelete(String[] reportIds) {
		ReportOpData data = new ReportOpData();
		data.setOp(Op.DELETE.toLowerCase());
		data.setReportIds(reportIds);
		return data;
	}


	public static ReportOpData makeRecover(String[] reportIds) {
		ReportOpData data = new ReportOpData();
		data.setOp(Op.DELETE.toLowerCase());
		data.setReportIds(reportIds);
		return data;
	}

	public static ReportOpData makeRate(String reportId, Integer rate) {
		ReportOpData data = new ReportOpData();
		data.setOp(Op.RATE.toLowerCase());
		data.setReportId(reportId);
		data.setRate(rate);
		return data;
	}

	public String getReportId() {
		return reportId;
	}
	public void setReportId(String reportId) {
		this.reportId = reportId;
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

	public String[] getReportIds() {
		return reportIds;
	}

	public void setReportIds(String[] reportIds) {
		this.reportIds = reportIds;
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