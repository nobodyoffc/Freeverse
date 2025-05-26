package data.feipData;

import java.util.HashMap;
import java.util.Map;

import constants.FieldNames;

import java.util.List;
public class GroupOpData {
	private String gid;
	private List<String> gids;
	private String op;
	private String name;
	private String desc;

	// Enum for operations
	public enum Op {
		CREATE(FeipOp.CREATE),
		UPDATE(FeipOp.UPDATE),
		JOIN(FeipOp.JOIN),
		LEAVE(FeipOp.LEAVE);

		private final FeipOp feipOp;

		Op(FeipOp feipOp) {
			this.feipOp = feipOp;
		}

		public FeipOp getFeipOp() {
			return feipOp;
		}

		public static Op fromString(String text) {
			for (Op op : Op.values()) {
				if (op.getFeipOp().equals(text)) {
					return op;
				}
			}
			throw new IllegalArgumentException("No constant with text " + text + " found");
		}

		public String toLowerCase() {
			return feipOp.getValue().toLowerCase();
		}
	}

	// Map for operation fields
	public static final Map<String, String[]> OP_FIELDS = new HashMap<>();

	static {
		OP_FIELDS.put(Op.CREATE.toLowerCase(), new String[]{FieldNames.NAME});
		OP_FIELDS.put(Op.UPDATE.toLowerCase(), new String[]{FieldNames.GID, FieldNames.NAME});
		OP_FIELDS.put(Op.JOIN.toLowerCase(), new String[]{FieldNames.GID});
		OP_FIELDS.put(Op.LEAVE.toLowerCase(), new String[]{FieldNames.GIDS});
	}

	// Factory method for CREATE operation
	public static GroupOpData makeCreate(String name, String desc) {
		GroupOpData data = new GroupOpData();
		data.setOp(Op.CREATE.toLowerCase());
		data.setName(name);
		data.setDesc(desc);
		return data;
	}

	// Factory method for UPDATE operation
	public static GroupOpData makeUpdate(String gid, String name, String desc) {
		GroupOpData data = new GroupOpData();
		data.setOp(Op.UPDATE.toLowerCase());
		data.setGid(gid);
		data.setName(name);
		data.setDesc(desc);
		return data;
	}

	// Factory method for JOIN operation
	public static GroupOpData makeJoin(String gid) {
		GroupOpData data = new GroupOpData();
		data.setOp(Op.JOIN.toLowerCase());
		data.setGid(gid);
		return data;
	}

	// Factory method for LEAVE operation
	public static GroupOpData makeLeave(List<String> gids) {
		GroupOpData data = new GroupOpData();
		data.setOp(Op.LEAVE.toLowerCase());
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
