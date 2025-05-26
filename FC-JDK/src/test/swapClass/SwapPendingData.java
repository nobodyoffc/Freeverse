package appTools.swapClass;

import java.util.List;

public class SwapPendingData {
    private String sid;
    private List<SwapAffair> pendingList;

    public String getSid() {
        return sid;
    }

    public void setSid(String sid) {
        this.sid = sid;
    }

    public List<SwapAffair> getPendingList() {
        return pendingList;
    }

    public void setPendingList(List<SwapAffair> pendingList) {
        this.pendingList = pendingList;
    }
}
