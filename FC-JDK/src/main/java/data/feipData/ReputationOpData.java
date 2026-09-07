package data.feipData;

/**
 * The {@code data} of a FEIP16 (Reputation) carve.
 *
 * <p><b>The ratee is {@code fid}, and it belongs here.</b> It was
 * previously read from {@code OpReturn.recipient} — the first non-signer
 * output owner — with the sentinel {@code "nobody"} when no such output
 * existed. That reading meant you could not rate anyone without paying
 * them, lost the rating whenever the output was absent or reordered, and
 * silently discarded every carve that named its subject any other way.
 * Android compounded it by putting the ratee in the envelope's
 * {@code did}, which is FEIP0's <i>document</i> id rather than a party.
 *
 * <p>A rating is an opinion <i>about</i> a FID, not a transfer <i>to</i>
 * one, so it names its subject in the payload and pays nobody.
 *
 * <p>{@code rate} MUST be {@link constants.Values#GOOD} or
 * {@link constants.Values#BAD}; any other string carries no reputation
 * delta and the operation is ignored.
 */
public class ReputationOpData {

	/** The rated FID. Required; without it the operation is ignored. */
	private String fid;
	private String rate;
	private String cause;

	public String getFid() {
		return fid;
	}

	public void setFid(String fid) {
		this.fid = fid;
	}

	public String getRate() {
		return rate;
	}

	public void setRate(String rate) {
		this.rate = rate;
	}

	public String getCause() {
		return cause;
	}

	public void setCause(String cause) {
		this.cause = cause;
	}

	/**
	 * @param fid   the FID being rated
	 * @param rate  {@code good} or {@code bad}
	 * @param cause optional free text; pass null when blank so the field
	 *              is omitted rather than carved as an empty string
	 */
	public static ReputationOpData makeRate(String fid, String rate, String cause) {
		ReputationOpData data = new ReputationOpData();
		data.setFid(fid);
		data.setRate(rate);
		data.setCause(cause);
		return data;
	}
}
