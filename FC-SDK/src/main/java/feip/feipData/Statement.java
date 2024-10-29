package feip.feipData;

public class Statement {

	private String statementId;
	private String title;
	private String content;
	
	private String owner;
	private Long birthTime;
	private Long birthHeight;
	
	public String getStatementId() {
		return statementId;
	}
	public void setStatementId(String statementId) {
		this.statementId = statementId;
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
	public String getOwner() {
		return owner;
	}
	public void setOwner(String owner) {
		this.owner = owner;
	}
	public Long getBirthTime() {
		return birthTime;
	}
	public void setBirthTime(Long birthTime) {
		this.birthTime = birthTime;
	}
	public Long getBirthHeight() {
		return birthHeight;
	}
	public void setBirthHeight(Long birthHeight) {
		this.birthHeight = birthHeight;
	}
	
}
