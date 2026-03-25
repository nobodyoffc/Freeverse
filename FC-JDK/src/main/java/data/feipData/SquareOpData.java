package data.feipData;

import java.util.HashMap;
import java.util.Map;

import constants.FieldNames;

import java.util.List;
public class SquareOpData {
	private String squareId;
	private List<String> squareIds;
	private String op;
	private String name;
	private String desc;
	private Map<String, String> home;

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
		OP_FIELDS.put(Op.UPDATE.toLowerCase(), new String[]{FieldNames.SQUARE_ID, FieldNames.NAME});
		OP_FIELDS.put(Op.JOIN.toLowerCase(), new String[]{FieldNames.SQUARE_ID});
		OP_FIELDS.put(Op.LEAVE.toLowerCase(), new String[]{FieldNames.SQUARE_IDS});
	}

	// Factory method for CREATE operation
	public static SquareOpData makeCreate(String name, String desc, Map<String, String> home) {
		SquareOpData data = new SquareOpData();
		data.setOp(Op.CREATE.toLowerCase());
		data.setName(name);
		data.setDesc(desc);
		data.setHome(home);
		return data;
	}

	// Factory method for UPDATE operation
	public static SquareOpData makeUpdate(String gid, String name, String desc, Map<String, String> home) {
		SquareOpData data = new SquareOpData();
		data.setOp(Op.UPDATE.toLowerCase());
		data.setSquareId(gid);
		data.setName(name);
		data.setDesc(desc);
		data.setHome(home);
		return data;
	}

	// Factory method for JOIN operation
	public static SquareOpData makeJoin(String gid) {
		SquareOpData data = new SquareOpData();
		data.setOp(Op.JOIN.toLowerCase());
		data.setSquareId(gid);
		return data;
	}

	// Factory method for LEAVE operation
	public static SquareOpData makeLeave(List<String> gids) {
		SquareOpData data = new SquareOpData();
		data.setOp(Op.LEAVE.toLowerCase());
		data.setSquareIds(gids);
		return data;
	}

	// Getters and setters
	public String getSquareId() {
		return squareId;
	}
	public void setSquareId(String squareId) {
		this.squareId = squareId;
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
	public List<String> getSquareIds() {
		return squareIds;
	}
	public void setSquareIds(List<String> squareIds) {
		this.squareIds = squareIds;
	}

	public Map<String, String> getHome() {
		return home;
	}

	public void setHome(Map<String, String> home) {
		this.home = home;
	}
}
