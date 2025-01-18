package feip.feipData;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
public class GroupData {
	private String gid;
	private List<String> gids;
	private String op;
	private String name;
	private String desc;

	// Enum for operations
	public enum Op {
		CREATE("create"),
		UPDATE("update"),
		JOIN("join"),
		LEAVE("leave");

		private final String value;

		Op(String value) {
			this.value = value;
		}

		public String getValue() {
			return value;
		}

		public static Op fromString(String text) {
			for (Op op : Op.values()) {
				if (op.value.equalsIgnoreCase(text)) {
					return op;
				}
			}
			throw new IllegalArgumentException("No constant with text " + text + " found");
		}
	}

	// Map for operation fields
	public static final Map<String, String[]> OP_FIELDS = new HashMap<>();

	static {
		OP_FIELDS.put(Op.CREATE.getValue(), new String[]{"name"});
		OP_FIELDS.put(Op.UPDATE.getValue(), new String[]{"gid", "name"});
		OP_FIELDS.put(Op.JOIN.getValue(), new String[]{"gid"});
		OP_FIELDS.put(Op.LEAVE.getValue(), new String[]{"gids"});
	}

	// Factory method for CREATE operation
	public static GroupData makeCreate(String name, String desc) {
		GroupData data = new GroupData();
		data.setOp(Op.CREATE.getValue());
		data.setName(name);
		data.setDesc(desc);
		return data;
	}

	// Factory method for UPDATE operation
	public static GroupData makeUpdate(String gid, String name, String desc) {
		GroupData data = new GroupData();
		data.setOp(Op.UPDATE.getValue());
		data.setGid(gid);
		data.setName(name);
		data.setDesc(desc);
		return data;
	}

	// Factory method for JOIN operation
	public static GroupData makeJoin(String gid) {
		GroupData data = new GroupData();
		data.setOp(Op.JOIN.getValue());
		data.setGid(gid);
		return data;
	}

	// Factory method for LEAVE operation
	public static GroupData makeLeave(List<String> gids) {
		GroupData data = new GroupData();
		data.setOp(Op.LEAVE.getValue());
		data.setGids(gids);
		return data;
	}

	// Getters and setters
	public String getGid() {
		return gid;
	}
	public void setGid(String gid) {
		this.gid = gid;
	}
	public String getOp() {
		return op;
	}
	public void setOp(String op) {
		this.op = op;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getDesc() {
		return desc;
	}
	public void setDesc(String desc) {
		this.desc = desc;
	}
	public List<String> getGids() {
		return gids;
	}
	public void setGids(List<String> gids) {
		this.gids = gids;
	}

}
