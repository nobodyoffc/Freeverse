package feip.feipData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TeamData {

	private String tid;
	private List<String> tids;
	private String op;
	private String stdName;
	private String[] localNames;
	private String[] waiters;
	private String[] accounts;
	private String consensusId;
	private String desc;
	private String transferee;
	private String confirm;
	private String[] list;
	private Integer rate;  // Changed from int to Integer


	public enum Op {
		CREATE("create"),
		UPDATE("update"),
		JOIN("join"),
		LEAVE("leave"),
		TRANSFER("transfer"),
		TAKE_OVER("take over"),
		DISBAND("disband"),
		AGREE_CONSENSUS("agree consensus"),
		INVITE("invite"),
		WITHDRAW_INVITATION("withdraw invitation"),
		DISMISS("dismiss"),
		APPOINT("appoint"),
		CANCEL_APPOINTMENT("cancel appointment"),
		RATE("rate");

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

	public static final Map<String, String[]> OP_FIELDS = new HashMap<>();
	static {
		OP_FIELDS.put(Op.CREATE.getValue(), new String[]{"stdName", "consensusId"});
		OP_FIELDS.put(Op.UPDATE.getValue(), new String[]{"tid", "stdName", "consensusId"});
		OP_FIELDS.put(Op.JOIN.getValue(), new String[]{"tid", "consensusId", "confirm"});
		OP_FIELDS.put(Op.LEAVE.getValue(), new String[]{"tids"});
		OP_FIELDS.put(Op.TRANSFER.getValue(), new String[]{"tid", "transferee", "confirm"});
		OP_FIELDS.put(Op.TAKE_OVER.getValue(), new String[]{"tid", "confirm"});
		OP_FIELDS.put(Op.DISBAND.getValue(), new String[]{"tids"});
		OP_FIELDS.put(Op.AGREE_CONSENSUS.getValue(), new String[]{"tid", "consensusId", "confirm"});
		OP_FIELDS.put(Op.INVITE.getValue(), new String[]{"tid", "list"});
		OP_FIELDS.put(Op.WITHDRAW_INVITATION.getValue(), new String[]{"tid", "list"});
		OP_FIELDS.put(Op.DISMISS.getValue(), new String[]{"tid", "list"});
		OP_FIELDS.put(Op.APPOINT.getValue(), new String[]{"tid", "list"});
		OP_FIELDS.put(Op.CANCEL_APPOINTMENT.getValue(), new String[]{"tid", "list"});
		OP_FIELDS.put(Op.RATE.getValue(), new String[]{"tid", "rate"});
	}

	public static TeamData makeCreate(String stdName, String consensusId, String[] localNames, String[] waiters, String[] accounts, String desc) {
		TeamData data = new TeamData();
		data.setOp(Op.CREATE.getValue());
		data.setStdName(stdName);
		data.setConsensusId(consensusId);
		data.setLocalNames(localNames);
		data.setWaiters(waiters);
		data.setAccounts(accounts);
		data.setDesc(desc);
		return data;
	}

	public static TeamData makeUpdate(String tid, String stdName, String consensusId, String[] localNames, String[] waiters, String[] accounts, String desc) {
		TeamData data = new TeamData();
		data.setOp(Op.UPDATE.getValue());
		data.setTid(tid);
		data.setStdName(stdName);
		data.setConsensusId(consensusId);
		data.setLocalNames(localNames);
		data.setWaiters(waiters);
		data.setAccounts(accounts);
		data.setDesc(desc);
		return data;
	}

	public static TeamData makeJoin(String tid, String consensusId) {
		TeamData data = new TeamData();
		data.setOp(Op.JOIN.getValue());
		data.setTid(tid);
		data.setConsensusId(consensusId);
		data.setConfirm("I join the team and agree with the team consensus.");
		return data;
	}

	public static TeamData makeLeave(List<String> tids) {
		TeamData data = new TeamData();
		data.setOp(Op.LEAVE.getValue());
		data.setTids(tids);
		return data;
	}

	public static TeamData makeTransfer(String tid, String transferee) {
		TeamData data = new TeamData();
		data.setOp(Op.TRANSFER.getValue());
		data.setTid(tid);
		data.setTransferee(transferee);
		data.setConfirm("I transfer the team to the transferee.");
		return data;
	}

	public static TeamData makeTakeOver(String tid, String consensusId) {
		TeamData data = new TeamData();
		data.setOp(Op.TAKE_OVER.getValue());
		data.setTid(tid);
		data.setConsensusId(consensusId);
		data.setConfirm("I take over the team and agree with the team consensus.");
		return data;
	}

	public static TeamData makeDisband(List<String> tids) {
		TeamData data = new TeamData();
		data.setOp(Op.DISBAND.getValue());
		data.setTids(tids);
		return data;
	}

	public static TeamData makeAgreeConsensus(String tid, String consensusId) {
		TeamData data = new TeamData();
		data.setOp(Op.AGREE_CONSENSUS.getValue());
		data.setTid(tid);
		data.setConsensusId(consensusId);
		data.setConfirm("I agree with the new consensus.");
		return data;
	}

	public static TeamData makeInvite(String tid, String[] list) {
		TeamData data = new TeamData();
		data.setOp(Op.INVITE.getValue());
		data.setTid(tid);
		data.setList(list);
		return data;
	}

	public static TeamData makeWithdrawInvitation(String tid, String[] list) {
		TeamData data = new TeamData();
		data.setOp(Op.WITHDRAW_INVITATION.getValue());
		data.setTid(tid);
		data.setList(list);
		return data;
	}

	public static TeamData makeDismiss(String tid, String[] list) {
		TeamData data = new TeamData();
		data.setOp(Op.DISMISS.getValue());
		data.setTid(tid);
		data.setList(list);
		return data;
	}

	public static TeamData makeAppoint(String tid, String[] list) {
		TeamData data = new TeamData();
		data.setOp(Op.APPOINT.getValue());
		data.setTid(tid);
		data.setList(list);
		return data;
	}

	public static TeamData makeCancelAppointment(String tid, String[] list) {
		TeamData data = new TeamData();
		data.setOp(Op.CANCEL_APPOINTMENT.getValue());
		data.setTid(tid);
		data.setList(list);
		return data;
	}

	public static TeamData makeRate(String tid, Integer rate) {
		TeamData data = new TeamData();
		data.setOp(Op.RATE.getValue());
		data.setTid(tid);
		data.setRate(rate);
		return data;
	}


	public String getTid() {
		return tid;
	}
	public void setTid(String tid) {
		this.tid = tid;
	}

	public List<String> getTids() {
        return tids;
    }

    public void setTids(List<String> tids) {
        this.tids = tids;
    }

    public String getOp() {
		return op;
	}
	public void setOp(String op) {
		this.op = op;
	}
	public String getStdName() {
		return stdName;
	}
	public void setStdName(String stdName) {
		this.stdName = stdName;
	}
	public String[] getLocalNames() {
		return localNames;
	}
	public void setLocalNames(String[] localNames) {
		this.localNames = localNames;
	}
	public String getConsensusId() {
		return consensusId;
	}
	public void setConsensusId(String consensusId) {
		this.consensusId = consensusId;
	}
	public String getDesc() {
		return desc;
	}
	public void setDesc(String desc) {
		this.desc = desc;
	}
	public String getTransferee() {
		return transferee;
	}
	public void setTransferee(String transferee) {
		this.transferee = transferee;
	}
	public String getConfirm() {
		return confirm;
	}
	public void setConfirm(String confirm) {
		this.confirm = confirm;
	}
	public String[] getList() {
		return list;
	}
	public void setList(String[] list) {
		this.list = list;
	}
	public Integer getRate() {  // Changed return type from int to Integer
		return rate;
	}
	public void setRate(Integer rate) {  // Changed parameter type from int to Integer
		this.rate = rate;
	}


	public String[] getWaiters() {
		return waiters;
	}

	public void setWaiters(String[] waiters) {
		this.waiters = waiters;
	}

	public String[] getAccounts() {
		return accounts;
	}

	public void setAccounts(String[] accounts) {
		this.accounts = accounts;
	}
}
