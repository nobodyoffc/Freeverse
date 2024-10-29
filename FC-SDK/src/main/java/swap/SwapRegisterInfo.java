package swap;

public class SwapRegisterInfo {
    private String sid;
    private String registerer;
    private long registerTime;

    public String getSid() {
        return sid;
    }

    public void setSid(String sid) {
        this.sid = sid;
    }

    public String getRegisterer() {
        return registerer;
    }

    public void setRegisterer(String registerer) {
        this.registerer = registerer;
    }

    public long getRegisterTime() {
        return registerTime;
    }

    public void setRegisterTime(long registerTime) {
        this.registerTime = registerTime;
    }
}
