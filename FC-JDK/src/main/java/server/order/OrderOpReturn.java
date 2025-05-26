package server.order;

public class OrderOpReturn {
    private String type;
    private String sn;
    private String ver;
    private String name;
    private String pid;
    private OrderOpReturnData data;

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

    public OrderOpReturnData getData() {
        return data;
    }

    public void setData(OrderOpReturnData data) {
        this.data = data;
    }
}
