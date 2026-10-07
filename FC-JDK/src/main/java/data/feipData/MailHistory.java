package data.feipData;

import data.fcData.FcObject;

import java.util.List;

/**
 * One accepted FEIP Mail operation, kept so a rollback can rebuild a mail from the operations
 * that survive it. The id is the txid of the operation; mailId is the mail a send created (equal
 * to the id), mailIds the ones delete or recover touched.
 */
public class MailHistory extends FcObject {

	private Long height;
	private Integer index;
	private Long time;
	private String signer;
	private String op;
	private String mailId;
	private List<String> mailIds;
	private String alg;
	private String cipher;
	private String recipient;	// the send's recipient, which becomes the mail's 'to'
	private Long paid;	// the send's payment to the recipient, which becomes the mail's noticeFee

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

	public String getMailId() {
		return mailId;
	}

	public void setMailId(String mailId) {
		this.mailId = mailId;
	}

	public List<String> getMailIds() {
		return mailIds;
	}

	public void setMailIds(List<String> mailIds) {
		this.mailIds = mailIds;
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

	public String getRecipient() {
		return recipient;
	}

	public void setRecipient(String recipient) {
		this.recipient = recipient;
	}

	public Long getPaid() {
		return paid;
	}

	public void setPaid(Long paid) {
		this.paid = paid;
	}
}
