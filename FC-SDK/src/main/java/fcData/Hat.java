package fcData;

public class Hat {
    //basic
    private String did;
    private String hAlg;
    private Long size;
    private Long born;
    private Long last; //last time being used.

    //extend
    private String name;
    private String desc;
    private String[] types;
    private String[] aids;
    private String[] pids;

    //version
    private String srcDid;
    private String priDid;

    //slice
    private String tDid;
    private Long tSize;
    private Long offset;

    //crypto
    private String cAlg;
    private String pKey;
    private String kCipher;
    private String kName;
    private Boolean Leaked;

    //manage
    private Integer rank;
    private DataState state;
    private String[] locas;



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

    public String getDid() {
        return did;
    }

    public void setDid(String did) {
        this.did = did;
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

    public String[] getTypes() {
        return types;
    }

    public void setTypes(String[] types) {
        this.types = types;
    }

    public String[] getAids() {
        return aids;
    }

    public void setAids(String[] aids) {
        this.aids = aids;
    }

    public String[] getPids() {
        return pids;
    }

    public void setPids(String[] pids) {
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

    public String getPriDid() {
        return priDid;
    }

    public void setPriDid(String priDid) {
        this.priDid = priDid;
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

    public String getcAlg() {
        return cAlg;
    }

    public void setcAlg(String cAlg) {
        this.cAlg = cAlg;
    }

    public String getpKey() {
        return pKey;
    }

    public void setpKey(String pKey) {
        this.pKey = pKey;
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

    public String[] getLocas() {
        return locas;
    }

    public void setLocas(String[] locas) {
        this.locas = locas;
    }

    public Long getLast() {
        return last;
    }

    public void setLast(Long last) {
        this.last = last;
    }

    public String getkName() {
        return kName;
    }

    public void setkName(String kName) {
        this.kName = kName;
    }

    public Boolean getLeaked() {
        return Leaked;
    }

    public void setLeaked(Boolean leaked) {
        Leaked = leaked;
    }
}