package feip.feipData;

public class StatementData {
	
	private String title;
	private String content;
	private String confirm;

	public static final String CONFIRM = "This is a formal and irrevocable statement.";

	public static StatementData makeStatement(String title,String content) {
		StatementData data = new StatementData();
		data.setTitle(title);
		data.setContent(content);
		data.setConfirm(CONFIRM);
		return data;
	}
	
	public String getTitle() {
		return title;
	}
	public void setTitle(String title) {
		this.title = title;
	}
	public String getContent() {
		return content;
	}
	public void setContent(String content) {
		this.content = content;
	}
	public String getConfirm() {
		return confirm;
	}
	public void setConfirm(String confirm) {
		this.confirm = confirm;
	}
}
