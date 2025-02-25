package feip.feipData;

import java.util.HashMap;
import java.util.Map;

import constants.FieldNames;

import java.util.List;

import feip.FeipOp;

public class SecretData {

	private String op;
	private String secretId;
	private List<String> secretIds;
    private String alg;
	private String msg;
	private String cipher;
	
	

	public String getOp() {
		return op;
	}
	public void setOp(String op) {
		this.op = op;
	}
	public String getSecretId() {
		return secretId;
	}
	public void setSecretId(String secretId) {
		this.secretId = secretId;
	}
	public List<String> getSecretIds() {
		return secretIds;
	}
	public void setSecretIds(List<String> secretIds) {
		this.secretIds = secretIds;
	}	
    public String getAlg() {
        return alg;
    }
    public void setAlg(String alg) {
        this.alg = alg;
	}
	public String getCipher() {
		return cipher;
	}
	public void setCipher(String cipher) {
		this.cipher = cipher;
	}
	public String getMsg() {
		return msg;
	}
	public void setMsg(String msg) {
		this.msg = msg;
	}
	
	
	public enum Op implements FeipOp.FeipOpFields {
        ADD(FeipOp.ADD, new String[]{FieldNames.ALG, FieldNames.CIPHER}),
        DELETE(FeipOp.DELETE, new String[]{FieldNames.SECRET_IDS}),
        RECOVER(FeipOp.RECOVER, new String[]{FieldNames.SECRET_IDS});

        private final FeipOp feipOp;
        private final String[] requiredFields;

        Op(FeipOp feipOp, String[] requiredFields) {
            this.feipOp = feipOp;
            this.requiredFields = requiredFields;
        }

        @Override
        public String getValue() {
            return feipOp.getValue();
        }

        @Override
        public String[] getRequiredFields() {
            return requiredFields;
        }

        @Override
        public String toLowerCase() {
            return getValue().toLowerCase();
        }

        public static Op fromString(String text) {
            for (Op op : Op.values()) {
                if (op.getValue().equalsIgnoreCase(text)) {
                    return op;
                }
            }
            throw new IllegalArgumentException("No constant with text " + text + " found");
        }
    }

    public static Map<String, String[]> getOpFields() {
        Map<String, String[]> fields = new HashMap<>();
        for (Op op : Op.values()) {
            fields.put(op.toLowerCase(), op.getRequiredFields());
        }
        return fields;
    }

    // Factory method for ADD operation
    public static SecretData makeAdd(String alg, String cipher) {
        SecretData data = new SecretData();
        data.setOp(Op.ADD.toLowerCase());
        data.setAlg(alg);
        data.setCipher(cipher);
        return data;
    }

    // Factory method for DELETE operation
    public static SecretData makeDelete(List<String> secretIds) {
        SecretData data = new SecretData();
        data.setOp(Op.DELETE.toLowerCase());
        data.setSecretIds(secretIds);
        return data;
    }

    // Factory method for RECOVER operation
    public static SecretData makeRecover(List<String> secretIds) {
        SecretData data = new SecretData();
        data.setOp(Op.RECOVER.toLowerCase());
        data.setSecretIds(secretIds);
        return data;
    }
}
