package startAPIP;

import data.feipData.ServiceOpData;

public class Feip5 {
	public final String type = "feip";
	public  final short sn = 5;
	public  final short ver = 2;
	public  final String name = "service";
	public  final String pid = "";
	
	private ServiceOpData data;
	
	public ServiceOpData getData() {
		return data;
	}
	public void setData(ServiceOpData data) {
		this.data = data;
	}
}
