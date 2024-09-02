package startAPIP;

import feip.feipData.ServiceData;

public class Feip5 {
	public final String type = "feip";
	public  final short sn = 5;
	public  final short ver = 2;
	public  final String name = "service";
	public  final String pid = "";
	
	private ServiceData data;
	
	public ServiceData getData() {
		return data;
	}
	public void setData(ServiceData data) {
		this.data = data;
	}
}
