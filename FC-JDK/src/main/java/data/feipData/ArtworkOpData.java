package data.feipData;

import constants.FieldNames;
import constants.Values;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ArtworkOpData {
    
    private String artworkId;
    private String[] artworkIds;

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
        OP_FIELDS.put(Op.PUBLISH.toLowerCase(), new String[]{FieldNames.TITLE,  FieldNames.DID, FieldNames.LANG, FieldNames.AUTHORS, FieldNames.SUMMARY});
        OP_FIELDS.put(Op.UPDATE.toLowerCase(), new String[]{FieldNames.ARTWORK_ID, FieldNames.TITLE,  FieldNames.DID, FieldNames.LANG, FieldNames.AUTHORS, FieldNames.SUMMARY});
        OP_FIELDS.put(Op.DELETE.toLowerCase(), new String[]{FieldNames.ARTWORK_IDS});
        OP_FIELDS.put(Op.RECOVER.toLowerCase(), new String[]{FieldNames.ARTWORK_IDS});
        OP_FIELDS.put(Op.RATE.toLowerCase(), new String[]{FieldNames.ARTWORK_ID, FieldNames.RATE});
    }

    // Factory methods
    public static ArtworkOpData makePublish(String title, String version, String did, String desc,
                                          String lang, String[] urls, String[] protocols, String[] waiters) {
        ArtworkOpData data = new ArtworkOpData();
        data.setOp(Op.PUBLISH.toLowerCase());
        data.setTitle(title);
        data.setDid(did);
        data.setVer(version);
        data.setLang(lang);
        return data;
    }

    public static ArtworkOpData makeUpdate(String artworkId, String name, String version, String did, String desc,
                                         String[] langs, String[] urls, String[] protocols, String[] waiters) {
        ArtworkOpData data = new ArtworkOpData();
        data.setOp(Op.UPDATE.toLowerCase());
        data.setArtworkId(artworkId);
        data.setTitle(name);
        data.setDid(did);
        data.setVer(version);
        return data;
    }

    public static ArtworkOpData makeDelete(String[] artworkIds) {
        ArtworkOpData data = new ArtworkOpData();
        data.setOp(Op.DELETE.toLowerCase());
        data.setArtworkIds(artworkIds);
        return data;
    }

    public static ArtworkOpData makeRecover(String[] artworkIds) {
        ArtworkOpData data = new ArtworkOpData();
        data.setOp(Op.DELETE.toLowerCase());
        data.setArtworkIds(artworkIds);
        return data;
    }

    public static ArtworkOpData makeRate(String artworkId, Integer rate) {
        ArtworkOpData data = new ArtworkOpData();
        data.setOp(Op.RATE.toLowerCase());
        data.setArtworkId(artworkId);
        data.setRate(rate);
        return data;
    }

    public String getArtworkId() {
        return artworkId;
    }
    public void setArtworkId(String artworkId) {
        this.artworkId = artworkId;
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

    public String[] getArtworkIds() {
        return artworkIds;
    }

    public void setArtworkIds(String[] artworkIds) {
        this.artworkIds = artworkIds;
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