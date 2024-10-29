package startFEIP;

public class ParseMark {

	private String fileName;
	private long pointer;
	private int length;
	private long lastHeight;
	private int lastIndex;
	private String lastId;
	
	
	public String getFileName() {
		return fileName;
	}
	public void setFileName(String fileName) {
		this.fileName = fileName;
	}
	public long getPointer() {
		return pointer;
	}
	public void setPointer(long pointer) {
		this.pointer = pointer;
	}
	public long getLastHeight() {
		return lastHeight;
	}
	public void setLastHeight(long lastHeight) {
		this.lastHeight = lastHeight;
	}
	public int getLastIndex() {
		return lastIndex;
	}
	public void setLastIndex(int lastIndex) {
		this.lastIndex = lastIndex;
	}
	public String getLastId() {
		return lastId;
	}
	public void setLastId(String lastId) {
		this.lastId = lastId;
	}
	public int getLength() {
		return length;
	}
	public void setLength(int length) {
		this.length = length;
	}	
	
	
}
