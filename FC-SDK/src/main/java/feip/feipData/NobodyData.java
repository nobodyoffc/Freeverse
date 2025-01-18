package feip.feipData;

public class NobodyData {

	private String priKey;

	public String getPriKey() {
		return priKey;
	}

	public void setPriKey(String priKey) {
		this.priKey = priKey;
	}
	
	public static NobodyData makeNobody(String priKey) {
		NobodyData data = new NobodyData();
		data.setPriKey(priKey);
		return data;
	}	
	
}
