package server.reward;

public class RewardOpReturn {
    private String type="FBBP";
    private String sn = "1";
    private String ver = "1";
    private String name = "Reward";
    private String pid;
    private String did;
    private RewardData data;

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

    public String getDid() {
        return did;
    }

    public void setDid(String did) {
        this.did = did;
    }

    public RewardData getData() {
        return data;
    }

    public void setData(RewardData data) {
        this.data = data;
    }
}
