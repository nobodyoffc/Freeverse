package feip.feipData;

import java.util.HashMap;
import java.util.Map;

import constants.FieldNames;
import feip.FeipOp;

import java.util.List;
public class SecretOpData {

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
	
	
	public enum Op {
        ADD(FeipOp.ADD),
        DELETE(FeipOp.DELETE),
        RECOVER(FeipOp.RECOVER);

		private final FeipOp feipOp;

		Op(FeipOp feipOp) {
			this.feipOp = feipOp;
		}

		public FeipOp getFeipOp() {
			return feipOp;
		}

        public static Op fromString(String text) {
            for (Op op : Op.values()) {
                if (op.name().equalsIgnoreCase(text)) {
                    return op;
                }
            }
            throw new IllegalArgumentException("No constant with text " + text + " found");
        }

        public String toLowerCase() {
            return this.name().toLowerCase();
        }
    }

    public static final Map<String, String[]> OP_FIELDS = new HashMap<>();

    static {
        OP_FIELDS.put(Op.ADD.toLowerCase(), new String[]{FieldNames.ALG, FieldNames.CIPHER});
        OP_FIELDS.put(Op.DELETE.toLowerCase(), new String[]{FieldNames.SECRET_IDS});
        OP_FIELDS.put(Op.RECOVER.toLowerCase(), new String[]{FieldNames.SECRET_IDS});
    }

    // Factory method for ADD operation
    public static SecretOpData makeAdd(String alg, String cipher) {
        SecretOpData data = new SecretOpData();
        data.setOp(Op.ADD.toLowerCase());
        data.setAlg(alg);
        data.setCipher(cipher);
        return data;
    }

    // Factory method for DELETE operation
    public static SecretOpData makeDelete(List<String> secretIds) {
        SecretOpData data = new SecretOpData();
        data.setOp(Op.DELETE.toLowerCase());
        data.setSecretIds(secretIds);
        return data;
    }

    // Factory method for RECOVER operation
    public static SecretOpData makeRecover(List<String> secretIds) {
        SecretOpData data = new SecretOpData();
        data.setOp(Op.RECOVER.toLowerCase());
        data.setSecretIds(secretIds);
        return data;
    }
}
