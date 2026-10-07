package data.feipData;

import data.fcData.FcObject;

import java.util.List;

/**
 * One accepted FEIP Secret operation, kept so a rollback can rebuild a secret from the operations
 * that survive it. The id is the txid of the operation; secretId names the secret that add or update
 * touched (for add it equals the id), secretIds the ones delete or recover touched.
 */
public class SecretHistory extends FcObject {

	private Long height;
	private Integer index;
	private Long time;
	private String signer;
	private String op;
	private String secretId;
	private List<String> secretIds;
	private String alg;
	private String cipher;

	public Long getHeight() {
		return height;
	}

	public void setHeight(Long height) {
		this.height = height;
	}

	public Integer getIndex() {
		return index;
	}

	public void setIndex(Integer index) {
		this.index = index;
	}

	public Long getTime() {
		return time;
	}

	public void setTime(Long time) {
		this.time = time;
	}

	public String getSigner() {
		return signer;
	}

	public void setSigner(String signer) {
		this.signer = signer;
	}

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
}
