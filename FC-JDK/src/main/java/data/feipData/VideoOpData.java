package data.feipData;

import constants.FieldNames;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VideoOpData {

	private String videoId;
	private String[] videoIds;

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
		OP_FIELDS.put(Op.PUBLISH.toLowerCase(), new String[]{FieldNames.TITLE,  FieldNames.DID, FieldNames.LANG, FieldNames.AUTHORS, FieldNames.SUMMARY});
		OP_FIELDS.put(Op.UPDATE.toLowerCase(), new String[]{"videoId", FieldNames.TITLE,  FieldNames.DID, FieldNames.LANG, FieldNames.AUTHORS, FieldNames.SUMMARY});
		OP_FIELDS.put(Op.DELETE.toLowerCase(), new String[]{"videoIds"});
		OP_FIELDS.put(Op.RECOVER.toLowerCase(), new String[]{"videoIds"});
		OP_FIELDS.put(Op.RATE.toLowerCase(), new String[]{"videoId", FieldNames.RATE});
	}

	// Factory methods
	public static VideoOpData makePublish(String title, String version, String did, String desc,
                                          String lang, String[] urls, String[] protocols, String[] waiters) {
		VideoOpData data = new VideoOpData();
		data.setOp(Op.PUBLISH.toLowerCase());
		data.setTitle(title);
		data.setDid(did);
		data.setVer(version);
		data.setLang(lang);
		return data;
	}

	public static VideoOpData makeUpdate(String videoId, String name, String version, String did, String desc,
                                         String[] langs, String[] urls, String[] protocols, String[] waiters) {
		VideoOpData data = new VideoOpData();
		data.setOp(Op.UPDATE.toLowerCase());
		data.setVideoId(videoId);
		data.setTitle(name);
		data.setDid(did);
		data.setVer(version);
		return data;
	}

	public static VideoOpData makeDelete(String[] videoIds) {
		VideoOpData data = new VideoOpData();
		data.setOp(Op.DELETE.toLowerCase());
		data.setVideoIds(videoIds);
		return data;
	}


	public static VideoOpData makeRecover(String[] videoIds) {
		VideoOpData data = new VideoOpData();
		data.setOp(Op.RECOVER.toLowerCase());
		data.setVideoIds(videoIds);
		return data;
	}

	public static VideoOpData makeRate(String videoId, Integer rate) {
		VideoOpData data = new VideoOpData();
		data.setOp(Op.RATE.toLowerCase());
		data.setVideoId(videoId);
		data.setRate(rate);
		return data;
	}

	public String getVideoId() {
		return videoId;
	}
	public void setVideoId(String videoId) {
		this.videoId = videoId;
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

	public String[] getVideoIds() {
		return videoIds;
	}

	public void setVideoIds(String[] videoIds) {
		this.videoIds = videoIds;
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
}
