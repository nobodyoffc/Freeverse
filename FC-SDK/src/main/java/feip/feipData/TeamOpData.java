package feip.feipData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import feip.FeipOp;

public class TeamOpData {

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
		CREATE(FeipOp.CREATE),
		UPDATE(FeipOp.UPDATE),
		JOIN(FeipOp.JOIN),
		LEAVE(FeipOp.LEAVE),
		TRANSFER(FeipOp.TRANSFER),
		TAKE_OVER(FeipOp.TAKE_OVER),
		DISBAND(FeipOp.DISBAND),
		AGREE_CONSENSUS(FeipOp.AGREE_CONSENSUS),
		INVITE(FeipOp.INVITE),
		WITHDRAW_INVITATION(FeipOp.WITHDRAW_INVITATION),
		DISMISS(FeipOp.DISMISS),
		APPOINT(FeipOp.APPOINT),
		CANCEL_APPOINTMENT(FeipOp.CANCEL_APPOINTMENT),
		RATE(FeipOp.RATE);

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

	public static final Map<String, String[]> OP_FIELDS = new HashMap<>();
	static {
		OP_FIELDS.put(Op.CREATE.toLowerCase(), new String[]{"stdName", "consensusId"});
		OP_FIELDS.put(Op.UPDATE.toLowerCase(), new String[]{"tid", "stdName", "consensusId"});
		OP_FIELDS.put(Op.JOIN.toLowerCase(), new String[]{"tid", "consensusId", "confirm"});
		OP_FIELDS.put(Op.LEAVE.toLowerCase(), new String[]{"tids"});
		OP_FIELDS.put(Op.TRANSFER.toLowerCase(), new String[]{"tid", "transferee", "confirm"});
		OP_FIELDS.put(Op.TAKE_OVER.toLowerCase(), new String[]{"tid", "confirm"});
		OP_FIELDS.put(Op.DISBAND.toLowerCase(), new String[]{"tids"});
		OP_FIELDS.put(Op.AGREE_CONSENSUS.toLowerCase(), new String[]{"tid", "consensusId", "confirm"});
		OP_FIELDS.put(Op.INVITE.toLowerCase(), new String[]{"tid", "list"});
		OP_FIELDS.put(Op.WITHDRAW_INVITATION.toLowerCase(), new String[]{"tid", "list"});
		OP_FIELDS.put(Op.DISMISS.toLowerCase(), new String[]{"tid", "list"});
		OP_FIELDS.put(Op.APPOINT.toLowerCase(), new String[]{"tid", "list"});
		OP_FIELDS.put(Op.CANCEL_APPOINTMENT.toLowerCase(), new String[]{"tid", "list"});
		OP_FIELDS.put(Op.RATE.toLowerCase(), new String[]{"tid", "rate"});
	}

	public static TeamOpData makeCreate(String stdName, String consensusId, String[] localNames, String[] waiters, String[] accounts, String desc) {
		TeamOpData data = new TeamOpData();
		data.setOp(Op.CREATE.toLowerCase());
		data.setStdName(stdName);
		data.setConsensusId(consensusId);
		data.setLocalNames(localNames);
		data.setWaiters(waiters);
		data.setAccounts(accounts);
		data.setDesc(desc);
		return data;
	}

	public static TeamOpData makeUpdate(String tid, String stdName, String consensusId, String[] localNames, String[] waiters, String[] accounts, String desc) {
		TeamOpData data = new TeamOpData();
		data.setOp(Op.UPDATE.toLowerCase());
		data.setTid(tid);
		data.setStdName(stdName);
		data.setConsensusId(consensusId);
		data.setLocalNames(localNames);
		data.setWaiters(waiters);
		data.setAccounts(accounts);
		data.setDesc(desc);
		return data;
	}

	public static TeamOpData makeJoin(String tid, String consensusId) {
		TeamOpData data = new TeamOpData();
		data.setOp(Op.JOIN.toLowerCase());
		data.setTid(tid);
		data.setConsensusId(consensusId);
		data.setConfirm("I join the team and agree with the team consensus.");
		return data;
	}

	public static TeamOpData makeLeave(List<String> tids) {
		TeamOpData data = new TeamOpData();
		data.setOp(Op.LEAVE.toLowerCase());
		data.setTids(tids);
		return data;
	}

	public static TeamOpData makeTransfer(String tid, String transferee) {
		TeamOpData data = new TeamOpData();
		data.setOp(Op.TRANSFER.toLowerCase());
		data.setTid(tid);
		data.setTransferee(transferee);
		data.setConfirm("I transfer the team to the transferee.");
		return data;
	}

	public static TeamOpData makeTakeOver(String tid, String consensusId) {
		TeamOpData data = new TeamOpData();
		data.setOp(Op.TAKE_OVER.toLowerCase());
		data.setTid(tid);
		data.setConsensusId(consensusId);
		data.setConfirm("I take over the team and agree with the team consensus.");
		return data;
	}

	public static TeamOpData makeDisband(List<String> tids) {
		TeamOpData data = new TeamOpData();
		data.setOp(Op.DISBAND.toLowerCase());
		data.setTids(tids);
		return data;
	}

	public static TeamOpData makeAgreeConsensus(String tid, String consensusId) {
		TeamOpData data = new TeamOpData();
		data.setOp(Op.AGREE_CONSENSUS.toLowerCase());
		data.setTid(tid);
		data.setConsensusId(consensusId);
		data.setConfirm("I agree with the new consensus.");
		return data;
	}

	public static TeamOpData makeInvite(String tid, String[] list) {
		TeamOpData data = new TeamOpData();
		data.setOp(Op.INVITE.toLowerCase());
		data.setTid(tid);
		data.setList(list);
		return data;
	}

	public static TeamOpData makeWithdrawInvitation(String tid, String[] list) {
		TeamOpData data = new TeamOpData();
		data.setOp(Op.WITHDRAW_INVITATION.toLowerCase());
		data.setTid(tid);
		data.setList(list);
		return data;
	}

	public static TeamOpData makeDismiss(String tid, String[] list) {
		TeamOpData data = new TeamOpData();
		data.setOp(Op.DISMISS.toLowerCase());
		data.setTid(tid);
		data.setList(list);
		return data;
	}

	public static TeamOpData makeAppoint(String tid, String[] list) {
		TeamOpData data = new TeamOpData();
		data.setOp(Op.APPOINT.toLowerCase());
		data.setTid(tid);
		data.setList(list);
		return data;
	}

	public static TeamOpData makeCancelAppointment(String tid, String[] list) {
		TeamOpData data = new TeamOpData();
		data.setOp(Op.CANCEL_APPOINTMENT.toLowerCase());
		data.setTid(tid);
		data.setList(list);
		return data;
	}

	public static TeamOpData makeRate(String tid, Integer rate) {
		TeamOpData data = new TeamOpData();
		data.setOp(Op.RATE.toLowerCase());
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
