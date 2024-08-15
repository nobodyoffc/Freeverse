package fch;

import fch.fchData.SendTo;

import java.util.List;

public class DataForOffLineTx {
    private String fromFid;
    private List<SendTo> sendToList;
    private Long cd;
    private String msg;

    public String getFromFid() {
        return fromFid;
    }

    public void setFromFid(String fromFid) {
        this.fromFid = fromFid;
    }

    public List<SendTo> getSendToList() {
        return sendToList;
    }

    public void setSendToList(List<SendTo> sendToList) {
        this.sendToList = sendToList;
    }

    public Long getCd() {
        return cd;
    }

    public void setCd(Long cd) {
        this.cd = cd;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }
}
