package data.feipData;

import constants.FieldNames;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SoundOpData {

	private String soundId;
	private String[] soundIds;

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
		OP_FIELDS.put(Op.UPDATE.toLowerCase(), new String[]{"soundId", FieldNames.TITLE,  FieldNames.DID, FieldNames.LANG, FieldNames.AUTHORS, FieldNames.SUMMARY});
		OP_FIELDS.put(Op.DELETE.toLowerCase(), new String[]{"soundIds"});
		OP_FIELDS.put(Op.RECOVER.toLowerCase(), new String[]{"soundIds"});
		OP_FIELDS.put(Op.RATE.toLowerCase(), new String[]{"soundId", FieldNames.RATE});
	}

	// Factory methods
	public static SoundOpData makePublish(String title, String version, String did, String desc,
                                          String lang, String[] urls, String[] protocols, String[] waiters) {
		SoundOpData data = new SoundOpData();
		data.setOp(Op.PUBLISH.toLowerCase());
		data.setTitle(title);
		data.setDid(did);
		data.setVer(version);
		data.setLang(lang);
		return data;
	}

	public static SoundOpData makeUpdate(String soundId, String name, String version, String did, String desc,
                                         String[] langs, String[] urls, String[] protocols, String[] waiters) {
		SoundOpData data = new SoundOpData();
		data.setOp(Op.UPDATE.toLowerCase());
		data.setSoundId(soundId);
		data.setTitle(name);
		data.setDid(did);
		data.setVer(version);
		return data;
	}

	public static SoundOpData makeDelete(String[] soundIds) {
		SoundOpData data = new SoundOpData();
		data.setOp(Op.DELETE.toLowerCase());
		data.setSoundIds(soundIds);
		return data;
	}


	public static SoundOpData makeRecover(String[] soundIds) {
		SoundOpData data = new SoundOpData();
		data.setOp(Op.RECOVER.toLowerCase());
		data.setSoundIds(soundIds);
		return data;
	}

	public static SoundOpData makeRate(String soundId, Integer rate) {
		SoundOpData data = new SoundOpData();
		data.setOp(Op.RATE.toLowerCase());
		data.setSoundId(soundId);
		data.setRate(rate);
		return data;
	}

	public String getSoundId() {
		return soundId;
	}
	public void setSoundId(String soundId) {
		this.soundId = soundId;
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

	public String[] getSoundIds() {
		return soundIds;
	}

	public void setSoundIds(String[] soundIds) {
		this.soundIds = soundIds;
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
