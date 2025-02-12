package feip.feipData;

public class ReputationData {

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

	public static ReputationData makeRate(String rate, String cause) {
		ReputationData data = new ReputationData();
		data.setRate(rate);
		data.setCause(cause);
		return data;
	}
}
