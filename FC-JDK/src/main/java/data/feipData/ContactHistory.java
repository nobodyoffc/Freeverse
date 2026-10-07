package data.feipData;

import data.fcData.FcObject;

import java.util.List;

/**
 * One accepted FEIP Contact operation, kept so a rollback can rebuild a contact from the operations
 * that survive it. The id is the txid of the operation; contactId names the contact that add or update
 * touched (for add it equals the id), contactIds the ones delete or recover touched.
 */
public class ContactHistory extends FcObject {

	private Long height;
	private Integer index;
	private Long time;
	private String signer;
	private String op;
	private String contactId;
	private List<String> contactIds;
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

	public String getContactId() {
		return contactId;
	}

	public void setContactId(String contactId) {
		this.contactId = contactId;
	}

	public List<String> getContactIds() {
		return contactIds;
	}

	public void setContactIds(List<String> contactIds) {
		this.contactIds = contactIds;
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
