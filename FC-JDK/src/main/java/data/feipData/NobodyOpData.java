package data.feipData;

public class NobodyOpData {

	private String prikey;

	public String getPrikey() {
		return prikey;
	}

	public void setPrikey(String prikey) {
		this.prikey = prikey;
	}
	
	public static NobodyOpData makeNobody(String priKey) {
		NobodyOpData data = new NobodyOpData();
		data.setPrikey(priKey);
		return data;
	}	
	
}
