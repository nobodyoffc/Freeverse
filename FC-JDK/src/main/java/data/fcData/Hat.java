package data.fcData;

import core.crypto.Hash;
import utils.Hex;

import java.util.List;

public class Hat extends FcObject {
    //basic
    private String hAlg;
    private Long size;
    private Long born;
    private Long last; //last time being used.

    //extend
    private String name;
    private String desc;
    private List<String> types;
    private List<String> aids;
    private List<String> pids;

    //version
    private String srcDid;
    private String preDid;

    //slice
    private String tDid;
    private Long tSize;
    private Long offset;

    //crypto
    private String rawDid;
    private String key;
    private String kCipher;
    private Boolean Leaked;
    private List<String> cipherIds;

    //manage
    private Integer rank;
    private DataState state;
    private List<String> locas;



    public enum DataState{
        ACTIVE((byte) 1),
        DELETED((byte) 0),
        OUTDATED((byte) 2),
        ARCHIVED((byte) 3)
        ;

        public final byte number;
        DataState(byte number){
            this.number = number;
        }
    }
    public void checkIdWithCreate() {
        if(id == null){
            byte[] idBytes = Hash.sha256x2(this.toBytes());
            id = Hex.toHex(idBytes);
        }
    }
    // ---------------------------------------------------------------------------------------
    // HAT from a published work (FEIP21 Text, FEIP23 Sound, FEIP24 Image, FEIP25 Video)
    //
    // A reader who downloads a work by its did gets bytes with no description. These build the
    // HAT that describes them, from the work's on-chain fields. Mirrored field for field in
    // FC-AJDK Hat.java and FreerForMac FCDomain Hat+Work.swift: the three must agree, because
    // HATs travel between devices as JSON. Change one, change all three and their test vectors.
    //
    // Dropped on purpose: the work's id (the did finds the work again), authors, cover, ver and
    // lang -- a HAT describes bytes, not the publication.
    // ---------------------------------------------------------------------------------------

    /** The work protocols whose entities carry a content did. */
    public enum WorkKind {
        TEXT("9fe31de9ef0e5af4637738af41f68ccaf93ff2f1b0c4264133cee9254772b144", "text/plain"),
        SOUND("983ffb819e2793ba85b9171dc8d879abac352199f7e982ebecefc962245e6a72", "audio/*"),
        IMAGE("34f8e6637506ea309c413b99296360101b12467f0b5e7db32051acddb4b6410f", "image/*"),
        VIDEO("89c23e636e93e6feb6776720688f02a138a036db338ed72df5510d5129b9ac00", "video/*");

        /** PID of the protocol document on chain. */
        public final String pid;
        /** MIME type used when the work's format names none. */
        public final String defaultMime;

        WorkKind(String pid, String defaultMime) {
            this.pid = pid;
            this.defaultMime = defaultMime;
        }
    }

    public static final String SHA256X2 = "sha256x2";

    /** MIME type ↔ file extension, for the formats works commonly declare. Order matters for
     *  the reverse lookup: the first MIME listed for an extension wins. */
    private static final String[][] MIME_EXTENSIONS = {
            {"text/plain", "txt"}, {"text/markdown", "md"}, {"text/html", "html"},
            {"application/pdf", "pdf"}, {"application/epub+zip", "epub"},
            {"application/rtf", "rtf"}, {"application/msword", "doc"},
            {"application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx"},
            {"application/vnd.oasis.opendocument.text", "odt"},
            {"image/jpeg", "jpg"}, {"image/png", "png"}, {"image/gif", "gif"},
            {"image/webp", "webp"}, {"image/svg+xml", "svg"},
            {"audio/mpeg", "mp3"}, {"audio/mp4", "m4a"}, {"audio/aac", "aac"},
            {"audio/wav", "wav"}, {"audio/ogg", "ogg"}, {"audio/flac", "flac"},
            {"video/mp4", "mp4"}, {"video/webm", "webm"}, {"video/quicktime", "mov"},
            {"video/x-matroska", "mkv"},
    };
    private static final String[][] EXTENSION_ALIASES = {{"jpeg", "jpg"}, {"markdown", "md"}};

    public static Hat fromText(data.feipData.Text w) {
        return fromWork(WorkKind.TEXT, w.getDid(), w.getTitle(), w.getFormat(), w.getSummary(), w.getLocas());
    }

    public static Hat fromSound(data.feipData.Sound w) {
        return fromWork(WorkKind.SOUND, w.getDid(), w.getTitle(), w.getFormat(), w.getSummary(), w.getLocas());
    }

    public static Hat fromImage(data.feipData.Image w) {
        return fromWork(WorkKind.IMAGE, w.getDid(), w.getTitle(), w.getFormat(), w.getSummary(), w.getLocas());
    }

    public static Hat fromVideo(data.feipData.Video w) {
        return fromWork(WorkKind.VIDEO, w.getDid(), w.getTitle(), w.getFormat(), w.getSummary(), w.getLocas());
    }

    public static Hat fromWork(WorkKind kind, String did, String title, String format,
                               String summary, List<String> locas) {
        return fromWork(kind, did, title, format, summary, locas, System.currentTimeMillis());
    }

    /**
     * Build the HAT describing a work's content. Pure: touches no store, no network.
     *
     * {@code size} is left null -- works do not carry it; set it from the downloaded file.
     * Add the local location after download too: {@code locas} here are the work's own.
     *
     * @throws IllegalArgumentException if {@code did} is not 64 hex characters
     */
    public static Hat fromWork(WorkKind kind, String did, String title, String format,
                               String summary, List<String> locas, long now) {
        if (kind == null) throw new IllegalArgumentException("kind is required");
        String id = did == null ? null : did.trim().toLowerCase(java.util.Locale.ROOT);
        if (id == null || !id.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("A work's did must be 64 hex characters: " + did);
        }

        String mime = workMime(kind, format);
        Hat hat = new Hat();
        hat.setId(id);
        hat.sethAlg(SHA256X2);
        hat.setBorn(now);
        hat.setLast(now);
        hat.setName(workFileName(id, title, mime, format));
        hat.setDesc(blankToNull(summary));
        hat.setTypes(new java.util.ArrayList<>(List.of(mime)));
        hat.setPids(new java.util.ArrayList<>(List.of(kind.pid)));
        hat.setState(DataState.ACTIVE);
        hat.setLocas(cleanLocas(locas));
        return hat;
    }

    /**
     * Fold a work's HAT into one the user already holds for the same did. What the user set
     * stays: name (unless it is still the did-prefix placeholder), desc and types are only
     * filled when empty; pids and locas are unioned, existing entries first; {@code last}
     * moves forward. Size, born, crypto, version and slice fields are never touched.
     *
     * @return {@code existing}, changed in place
     */
    public static Hat mergeWork(Hat existing, Hat work) {
        if (existing == null) return work;
        if (work == null) return existing;
        if (existing.getId() == null || !existing.getId().equalsIgnoreCase(work.getId())) {
            throw new IllegalArgumentException("Cannot merge HATs of different dids");
        }
        String placeholder = existing.getId().substring(0, Math.min(8, existing.getId().length()));
        if (blankToNull(existing.getName()) == null || existing.getName().equals(placeholder)) {
            existing.setName(work.getName());
        }
        if (blankToNull(existing.getDesc()) == null) existing.setDesc(work.getDesc());
        if (existing.getTypes() == null || existing.getTypes().isEmpty()) existing.setTypes(work.getTypes());
        if (existing.gethAlg() == null) existing.sethAlg(work.gethAlg());
        if (existing.getState() == null) existing.setState(work.getState());
        existing.setPids(union(existing.getPids(), work.getPids()));
        existing.setLocas(union(existing.getLocas(), work.getLocas()));
        if (work.getLast() != null && (existing.getLast() == null || existing.getLast() < work.getLast())) {
            existing.setLast(work.getLast());
        }
        if (existing.getBorn() == null) existing.setBorn(work.getBorn());
        return existing;
    }

    /** The work's format if it is a MIME type, else the MIME its extension maps to, else the
     *  kind's default. */
    static String workMime(WorkKind kind, String format) {
        String f = blankToNull(format);
        if (f == null) return kind.defaultMime;
        f = f.toLowerCase(java.util.Locale.ROOT);
        int semi = f.indexOf(';');
        if (semi >= 0) f = f.substring(0, semi).trim();
        if (f.matches("(text|image|audio|video|application|font|model)/[a-z0-9.+*-]+")) return f;
        String ext = canonicalExtension(f.startsWith(".") ? f.substring(1) : f);
        for (String[] pair : MIME_EXTENSIONS) {
            if (pair[1].equals(ext)) return pair[0];
        }
        return kind.defaultMime;
    }

    /** Title made safe as a file name, with an extension from the MIME (or the bare format)
     *  appended unless the title already ends with it. The did's first 8 characters stand in
     *  for a missing title. */
    static String workFileName(String did, String title, String mime, String format) {
        String base = blankToNull(title);
        base = base == null ? did.substring(0, 8) : base.replaceAll("[/\\\\:]", "_");
        String ext = null;
        for (String[] pair : MIME_EXTENSIONS) {
            if (pair[0].equals(mime)) { ext = pair[1]; break; }
        }
        if (ext == null) {
            String f = blankToNull(format);
            if (f != null) {
                f = f.toLowerCase(java.util.Locale.ROOT);
                if (f.startsWith(".")) f = f.substring(1);
                if (f.matches("[a-z0-9]{1,5}")) ext = canonicalExtension(f);
            }
        }
        if (ext == null) return base;
        String lower = base.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith("." + ext)) return base;
        for (String[] alias : EXTENSION_ALIASES) {
            if (alias[1].equals(ext) && lower.endsWith("." + alias[0])) return base;
        }
        return base + "." + ext;
    }

    private static String canonicalExtension(String ext) {
        for (String[] alias : EXTENSION_ALIASES) {
            if (alias[0].equals(ext)) return alias[1];
        }
        return ext;
    }

    private static List<String> cleanLocas(List<String> locas) {
        return union(null, locas);
    }

    /** Ordered union of non-blank, trimmed entries; null when empty. */
    private static List<String> union(List<String> first, List<String> second) {
        java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
        for (List<String> list : java.util.Arrays.asList(first, second)) {
            if (list == null) continue;
            for (String s : list) {
                String t = blankToNull(s);
                if (t != null) set.add(t);
            }
        }
        return set.isEmpty() ? null : new java.util.ArrayList<>(set);
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    // ---------------------------------------------------------------------------------------
    // The reverse: a work draft from a HAT the user holds -- what a "Publish..." action opens the
    // composer with. A draft, not a carve: the user reviews it; authors, lang, cover and type
    // are theirs to add. Mirrored in FreerForMac Hat+Work.swift (toWorkDraft).
    // ---------------------------------------------------------------------------------------

    /** The fields of a work prefilled from a HAT. */
    public static final class WorkDraft {
        /** Which protocol fits the data; null when its type fits none. */
        public final WorkKind kind;
        public final String did;
        public final String title;
        public final String format;
        public final String summary;
        /** Where a reader can fetch the plaintext. Never local paths. */
        public final List<String> locas;

        public WorkDraft(WorkKind kind, String did, String title, String format, String summary,
                         List<String> locas) {
            this.kind = kind;
            this.did = did;
            this.title = title;
            this.format = format;
            this.summary = summary;
            this.locas = locas;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof WorkDraft)) return false;
            WorkDraft d = (WorkDraft) o;
            return kind == d.kind && java.util.Objects.equals(did, d.did)
                    && java.util.Objects.equals(title, d.title) && java.util.Objects.equals(format, d.format)
                    && java.util.Objects.equals(summary, d.summary) && java.util.Objects.equals(locas, d.locas);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(kind, did, title, format, summary, locas);
        }

        @Override
        public String toString() {
            return "WorkDraft{" + kind + ", " + did + ", " + title + ", " + format + ", " + summary + ", " + locas + "}";
        }
    }

    /** The most locations a carve lists (FEIP21-25). */
    public static final int MAX_WORK_LOCAS = 3;

    private static final java.util.Set<String> TEXT_DOCUMENT_MIMES = new java.util.HashSet<>(java.util.Arrays.asList(
            "application/pdf", "application/epub+zip", "application/rtf", "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.oasis.opendocument.text"));

    /**
     * Prefill a work from this HAT.
     * <ul>
     * <li>title: the name without its extension;</li>
     * <li>format: the first type if it is a concrete MIME (not application/octet-stream), else
     *     the MIME of the name's extension;</li>
     * <li>kind: from the format, else from the first type even if it is a wildcard;</li>
     * <li>locas: DISK ((sid), fudp://) and https:// entries, at most three -- none at all when the
     *     HAT carries a plaintext key: it came over IM, and its DISK entries hold the encrypted
     *     copy.</li>
     * </ul>
     * @throws IllegalStateException for a cipher HAT
     * @throws IllegalArgumentException when the id is not a content DID
     */
    public WorkDraft toWorkDraft() {
        if (rawDid != null) {
            throw new IllegalStateException("This record is an encrypted copy; publish the original file instead");
        }
        String did = id == null ? "" : id.toLowerCase(java.util.Locale.ROOT);
        if (!did.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Not a content DID (64 hex characters): " + id);
        }
        String n = blankToNull(name);
        String firstType = types == null || types.isEmpty() ? null : blankToNull(types.get(0));
        String format = draftFormat(firstType, n);
        return new WorkDraft(
                workKind(format != null ? format : firstType),
                did,
                n == null ? null : stripExtension(n),
                format,
                blankToNull(desc),
                key == null ? publishableLocas(locas) : null);
    }

    /** The protocol a MIME type belongs to, or null. */
    public static WorkKind workKind(String mime) {
        if (mime == null) return null;
        String m = mime.toLowerCase(java.util.Locale.ROOT);
        if (m.startsWith("text/") || TEXT_DOCUMENT_MIMES.contains(m)) return WorkKind.TEXT;
        if (m.startsWith("image/")) return WorkKind.IMAGE;
        if (m.startsWith("audio/")) return WorkKind.SOUND;
        if (m.startsWith("video/")) return WorkKind.VIDEO;
        return null;
    }

    static String draftFormat(String firstType, String name) {
        if (firstType != null) {
            String t = firstType.toLowerCase(java.util.Locale.ROOT);
            if (t.matches("(text|image|audio|video|application|font|model)/[a-z0-9.+*-]+")
                    && !t.contains("*") && !t.equals("application/octet-stream")) {
                return t;
            }
        }
        if (name == null) return null;
        int dot = name.lastIndexOf('.');
        if (dot <= 0) return null;
        String ext = canonicalExtension(name.substring(dot + 1).toLowerCase(java.util.Locale.ROOT));
        for (String[] pair : MIME_EXTENSIONS) {
            if (pair[1].equals(ext)) return pair[0];
        }
        return null;
    }

    /** "name.ext" to "name", for an extension of 1-5 letters or digits. */
    static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot <= 0) return name;
        return name.substring(dot + 1).matches("[A-Za-z0-9]{1,5}") ? name.substring(0, dot) : name;
    }

    /** The entries of locas a reader elsewhere can use, in order, at most MAX_WORK_LOCAS: DISK
     *  by SID or FUDP address (not the fudp://unknown placeholder) and https:// mirrors. */
    public static List<String> publishableLocas(List<String> locas) {
        List<String> all = union(null, locas);
        if (all == null) return null;
        List<String> kept = new java.util.ArrayList<>();
        for (String loca : all) {
            String l = loca.toLowerCase(java.util.Locale.ROOT);
            if (l.equals("fudp://unknown")) continue;
            if (loca.startsWith("(sid)") || l.startsWith("fudp://") || l.startsWith("https://")) {
                kept.add(loca);
                if (kept.size() == MAX_WORK_LOCAS) break;
            }
        }
        return kept.isEmpty() ? null : kept;
    }

    public String gethAlg() {
        return hAlg;
    }

    public void sethAlg(String hAlg) {
        this.hAlg = hAlg;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getTypes() {
        return types;
    }

    public void setTypes(List<String> types) {
        this.types = types;
    }

    public List<String> getAids() {
        return aids;
    }

    public void setAids(List<String> aids) {
        this.aids = aids;
    }

    public List<String> getPids() {
        return pids;
    }

    public void setPids(List<String> pids) {
        this.pids = pids;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public Long getBorn() {
        return born;
    }

    public void setBorn(Long born) {
        this.born = born;
    }

    public String getSrcDid() {
        return srcDid;
    }

    public void setSrcDid(String srcDid) {
        this.srcDid = srcDid;
    }

    public String getPreDid() {
        return preDid;
    }

    public void setPreDid(String preDid) {
        this.preDid = preDid;
    }

    public String gettDid() {
        return tDid;
    }

    public void settDid(String tDid) {
        this.tDid = tDid;
    }

    public Long gettSize() {
        return tSize;
    }

    public void settSize(Long tSize) {
        this.tSize = tSize;
    }

    public Long getOffset() {
        return offset;
    }

    public void setOffset(Long offset) {
        this.offset = offset;
    }

    public String getkCipher() {
        return kCipher;
    }

    public void setkCipher(String kCipher) {
        this.kCipher = kCipher;
    }

    public Integer getRank() {
        return rank;
    }

    public void setRank(Integer rank) {
        this.rank = rank;
    }

    public DataState getState() {
        return state;
    }

    public void setState(DataState state) {
        this.state = state;
    }

    public List<String> getLocas() {
        return locas;
    }

    public void setLocas(List<String> locas) {
        this.locas = locas;
    }

    public Long getLast() {
        return last;
    }

    public void setLast(Long last) {
        this.last = last;
    }

    public Boolean getLeaked() {
        return Leaked;
    }

    public void setLeaked(Boolean leaked) {
        Leaked = leaked;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public List<String> getCipherIds() {
        return cipherIds;
    }

    public void setCipherIds(List<String> cipherIds) {
        this.cipherIds = cipherIds;
    }

    public String getRawDid() {
        return rawDid;
    }

    public void setRawDid(String rawDid) {
        this.rawDid = rawDid;
    }

}
