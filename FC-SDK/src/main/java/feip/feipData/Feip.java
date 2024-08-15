package feip.feipData;

public class Feip {

    private String type;
    private String sn;
    private String ver;
    private String name;
    private String pid;
    private String did;
    private Object data;

    public Feip() {
    }

    public Feip(String type, String sn, String ver, String name) {
        this.type = type;
        this.sn = sn;
        this.ver = ver;
        this.name = name;
    }

    public String getDid() {
        return did;
    }

    public void setDid(String did) {
        this.did = did;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSn() {
        return sn;
    }

    public void setSn(String sn) {
        this.sn = sn;
    }

    public String getVer() {
        return ver;
    }

    public void setVer(String ver) {
        this.ver = ver;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPid() {
        return pid;
    }

    public void setPid(String pid) {
        this.pid = pid;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }


}
