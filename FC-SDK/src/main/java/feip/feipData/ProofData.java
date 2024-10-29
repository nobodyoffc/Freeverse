package feip.feipData;

public class ProofData {
    private String proofId;
    private String op;
    private String title;
    private String content;
    private String[] cosigners;
    private Boolean transferable;
    private Boolean allSignsRequired;


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

    public Boolean isTransferable() {
        return transferable;
    }

    public void setTransferable(Boolean transferable) {
        this.transferable = transferable;
    }

    public Boolean isAllSignsRequired() {
        return allSignsRequired;
    }

    public void setAllSignsRequired(Boolean allSignsRequired) {
        this.allSignsRequired = allSignsRequired;
    }
}
