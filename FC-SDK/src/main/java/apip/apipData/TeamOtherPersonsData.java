package apip.apipData;

public class TeamOtherPersonsData {
    private String tid;
    private String transferee;
    private String [] invitees;
    private String[] notAgreeMembers;

    public String getTid() {
        return tid;
    }

    public void setTid(String tid) {
        this.tid = tid;
    }

    public String getTransferee() {
        return transferee;
    }

    public void setTransferee(String transferee) {
        this.transferee = transferee;
    }

    public String[] getInvitees() {
        return invitees;
    }

    public void setInvitees(String[] invitees) {
        this.invitees = invitees;
    }

    public String[] getNotAgreeMembers() {
        return notAgreeMembers;
    }

    public void setNotAgreeMembers(String[] notAgreeMembers) {
        this.notAgreeMembers = notAgreeMembers;
    }
}
