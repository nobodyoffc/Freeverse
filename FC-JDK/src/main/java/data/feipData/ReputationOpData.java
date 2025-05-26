package data.feipData;

public class ReputationOpData {

	private String rate;
	private String cause;

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

	public static ReputationOpData makeRate(String rate, String cause) {
		ReputationOpData data = new ReputationOpData();
		data.setRate(rate);
		data.setCause(cause);
		return data;
	}
}
