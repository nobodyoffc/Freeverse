package feip.feipData;

import java.util.HashMap;
import java.util.Map;

public class ProofData {
    private String proofId;
    private String op;
    private String title;
    private String content;
    private String[] cosigners;
    private Boolean transferable;
    private Boolean allSignsRequired;
    private String[] proofIds;

    public enum Op {
        ISSUE("issue"),
        SIGN("sign"),
        TRANSFER("transfer"),
        DESTROY("destroy");

        private final String value;

        Op(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static Op fromString(String text) {
            for (Op op : Op.values()) {
                if (op.getValue().equalsIgnoreCase(text)) {
                    return op;
                }
            }
            throw new IllegalArgumentException("No constant with text " + text + " found");
        }

        public String toLowerCase() {
            return this.getValue().toLowerCase();
        }
    }

    public static final Map<String, String[]> OP_FIELDS = new HashMap<>();

    static {
        OP_FIELDS.put(Op.ISSUE.toLowerCase(), new String[]{"title", "content", "cosigners", "transferable", "allSignsRequired"});
        OP_FIELDS.put(Op.SIGN.toLowerCase(), new String[]{"proofId"});
        OP_FIELDS.put(Op.TRANSFER.toLowerCase(), new String[]{"proofId"});
        OP_FIELDS.put(Op.DESTROY.toLowerCase(), new String[]{"proofIds"});
    }

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

    public String[] getProofIds() {
        return proofIds;
    }

    public void setProofIds(String[] proofIds) {
        this.proofIds = proofIds;
    }

    public static ProofData makeIssue(String title, String content, String[] cosigners, Boolean transferable, Boolean allSignsRequired) {
        ProofData data = new ProofData();
        data.setOp(Op.ISSUE.toLowerCase());
        data.setTitle(title);
        data.setContent(content);
        data.setCosigners(cosigners);
        data.setTransferable(transferable);
        data.setAllSignsRequired(allSignsRequired);
        return data;
    }

    public static ProofData makeSign(String proofId) {
        ProofData data = new ProofData();
        data.setOp(Op.SIGN.toLowerCase());
        data.setProofId(proofId);
        return data;
    }

    public static ProofData makeTransfer(String proofId) {
        ProofData data = new ProofData();
        data.setOp(Op.TRANSFER.toLowerCase());
        data.setProofId(proofId);
        return data;
    }

    public static ProofData makeDestroy(String[] proofIds) {
        ProofData data = new ProofData();
        data.setOp(Op.DESTROY.toLowerCase());
        data.setProofIds(proofIds);
        return data;
    }
}
