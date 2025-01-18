package feip.feipData;

public class NoticeFeeData {
	
	private String noticeFee;

	public String getNoticeFee() {
		return noticeFee;
	}

	public void setNoticeFee(String noticeFee) {
		this.noticeFee = noticeFee;
	}
	
	public static NoticeFeeData makeNoticeFee(String noticeFee) {
		NoticeFeeData data = new NoticeFeeData();
		data.setNoticeFee(noticeFee);
		return data;
	}
}
