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
    private String priDid;

    //slice
    private String tDid;
    private Long tSize;
    private Long offset;

    //crypto
    private String rawDid;
    private String kCipher;
    private Boolean Leaked;

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

    public String getRawDid() {
        return rawDid;
    }

    public void setRawDid(String rawDid) {
        this.rawDid = rawDid;
    }

}
