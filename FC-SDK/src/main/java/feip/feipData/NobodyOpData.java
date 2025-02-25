package feip.feipData;

public class NobodyOpData {

	private String priKey;

	public String getPriKey() {
		return priKey;
	}

	public void setPriKey(String priKey) {
		this.priKey = priKey;
	}
	
	public static NobodyOpData makeNobody(String priKey) {
		NobodyOpData data = new NobodyOpData();
		data.setPriKey(priKey);
		return data;
	}	
	
}
