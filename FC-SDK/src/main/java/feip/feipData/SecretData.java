package feip.feipData;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
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
	
	
	public enum Op {
        ADD("add"),
        DELETE("delete"),
        RECOVER("recover");

        private final String value;

        Op(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static Op fromString(String text) {
            for (Op op : Op.values()) {
                if (op.value.equalsIgnoreCase(text)) {
                    return op;
                }
            }
            throw new IllegalArgumentException("No constant with text " + text + " found");
        }

        public String toLowerCase() {
            return this.value.toLowerCase();
        }
    }

    public static final Map<String, String[]> OP_FIELDS = new HashMap<>();

    static {
        OP_FIELDS.put(Op.ADD.toLowerCase(), new String[]{"alg", "cipher"});
        OP_FIELDS.put(Op.DELETE.toLowerCase(), new String[]{"secretIds"});
        OP_FIELDS.put(Op.RECOVER.toLowerCase(), new String[]{"secretIds"});
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
