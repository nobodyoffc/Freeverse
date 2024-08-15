package feip.feipData;

public class ProofData {
    private String proofId;
    private String op;
    private String title;
    private String content;
    private String[] cosigners;
    private boolean transferable;
    private boolean allSignsRequired;


    public String getProofId() {
        return proofId;
    }

    public void setProofId(String proofId) {
        this.proofId = proofId;
    }

    public String getOp() {
        return op;
    }

    public void setOp(String op) {
        this.op = op;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String[] getCosigners() {
        return cosigners;
    }

    public void setCosigners(String[] cosigners) {
        this.cosigners = cosigners;
    }

    public boolean isTransferable() {
        return transferable;
    }

    public void setTransferable(boolean transferable) {
        this.transferable = transferable;
    }

    public boolean isAllSignsRequired() {
        return allSignsRequired;
    }

    public void setAllSignsRequired(boolean allSignsRequired) {
        this.allSignsRequired = allSignsRequired;
    }
}
