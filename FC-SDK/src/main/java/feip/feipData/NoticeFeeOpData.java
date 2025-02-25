package feip.feipData;

public class NoticeFeeOpData {
	
	private String noticeFee;

	public String getNoticeFee() {
		return noticeFee;
	}

	public void setNoticeFee(String noticeFee) {
		this.noticeFee = noticeFee;
	}
	
	public static NoticeFeeOpData makeNoticeFee(String noticeFee) {
		NoticeFeeOpData data = new NoticeFeeOpData();
		data.setNoticeFee(noticeFee);
		return data;
	}
}
