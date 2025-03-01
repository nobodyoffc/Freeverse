package fch.fchData;

import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

import com.google.gson.Gson;

import appTools.Shower;
import constants.FieldNames;
import fcData.FcObject;
import fch.ParseTools;
import tools.DateTools;
import nasa.data.UTXO;

import static constants.Constants.COINBASE;
import static constants.Constants.OneDayInterval;
import static constants.FieldNames.*;
import static constants.Strings.FCH;

public class Cash extends FcObject {

	//calculated
	private String issuer; //first input fid when this cash was born.

	//from utxo
	private Integer birthIndex;		//index of cash. Order in cashs of the tx when created.
	private String type;	//type of the script. P2PKH,P2SH,OP_RETURN,Unknown,MultiSig
	private String owner; 	//address
	private Long value;		//in satoshi
	private String lockScript;	//LockScript
	private String birthTxId;		//txid, hash in which this cash was created.
	private Integer birthTxIndex;		//Order in the block of the tx in which this cash was created.
	private String birthBlockId;		//block ID, hash of block head
	private Long birthTime;		//Block time when this cash is created.
	private Long birthHeight;		//Block height.

	//from input
	private Long spendTime;	//Block time when spent.
	private String spendTxId;	//Tx hash when spent.
	private Long spendHeight; 	//Block height when spent.
	private Integer spendTxIndex;		//Order in the block of the tx in which this cash was spent.
	private String spendBlockId;		//block ID, hash of block head
	private Integer spendIndex;		//Order in inputs of the tx when spent.
	private String unlockScript;	//unlock script.
	private String sigHash;	//sigHash.
	private String sequence;	//nSequence
	private Long cdd;		//CoinDays Destroyed
	private Long cd;		//CoinDays
	private Boolean valid;	//Is this cash valid (utxo), or spent (stxo);
	private Long lastTime;
	private Long lastHeight;
	public Cash() {
		// default constructor
	}

	public Cash(int outIndex, String type, String addr, long value, String lockScript, String txId, int txIndex,
				String blockId, long birthTime, long birthHeight) {
		this.birthIndex = outIndex;
		this.type = type;
		this.owner = addr;
		this.value = value;
		this.lockScript = lockScript;
		this.birthTxId = txId;
		this.birthTxIndex = txIndex;
		this.birthBlockId = blockId;
		this.birthTime = birthTime;
		this.birthHeight = birthHeight;
	}

	public String toJson() {
		return new Gson().toJson(this);
	}

	public static Cash fromJson(String json) {
		return new Gson().fromJson(json, Cash.class);
	}

	public byte[] toBytes() {
		return toJson().getBytes();
	}

	public static Cash fromBytes(byte[] bytes) {
		return fromJson(new String(bytes));
	}

	public static Cash fromUtxo(UTXO utxo) {
		Cash cash = new Cash();
		cash.setBirthTxId(utxo.getTxid());
		cash.setBirthIndex(utxo.getVout());
		cash.setOwner(utxo.getAddress());
		cash.setLockScript(utxo.getScriptPubKey());
		cash.setValue(ParseTools.coinToSatoshi(utxo.getAmount()));
		cash.setValid(true);	
		return cash;
	}

	public static List<Cash> fromUtxoList(List<UTXO> utxoList) {
		if (utxoList == null || utxoList.isEmpty()) {
			return new ArrayList<>();
		}
		
		return utxoList.stream()
			.map(Cash::fromUtxo)
			.collect(Collectors.toList());
	}

	public static long sumCashValue(List<Cash> cashList) {
		if(cashList==null||cashList.isEmpty())return 0;
		long sum = 0;
		for(Cash cash :cashList){
			sum+=cash.getValue();
		}
		return sum;
	}

	public static double sumCashAmount(List<Cash> cashList) {
		if(cashList==null||cashList.isEmpty())return 0;
		long sum = 0;
		for(Cash cash :cashList){
			sum+=cash.getValue();
		}
		return ParseTools.satoshiToCoin(sum);
	}

	public static long sumCashCd(List<Cash> cashList) {
		if(cashList==null||cashList.isEmpty())return 0;
		long sum = 0;
		for(Cash cash :cashList){
			if(cash.cd!=null)sum+=cash.getCd();
		}
		return sum;
	}

	public static void checkImmatureCoinbase(List<Cash> cashList, long bestHeight) {
		cashList.removeIf(cash -> COINBASE.equals(cash.getIssuer()) && bestHeight != 0 && (bestHeight - cash.getBirthHeight()) < OneDayInterval * 10);
	}

    public static void showCashList(List<Cash> cashList, String title, int totalDisplayed, String myFid) {
        String[] fields = new String[]{BIRTH_TIME,VALID, FROM,TO, FCH,FieldNames.CD,FieldNames.CDD,ID};
        int[] widths = new int[]{10,6, 17,17,16, 12,12,64};
        List<List<Object>> valueListList = new ArrayList<>();

        for (Cash cash : cashList) {
            List<Object> showList = new ArrayList<>();
            showList.add(DateTools.longToTime(cash.getBirthTime()*1000, "yyyy-MM-dd"));
			String signal;
			Boolean valid = cash.isValid();
			if(valid==null)continue;
			if(valid)signal = "✓";	
			else signal = "×";
			showList.add(signal);
			String issuer =cash.getIssuer();
			if(myFid.equals(issuer)) issuer = ME;
			showList.add(issuer);
			String owner =cash.getOwner();
			if(myFid.equals(owner)) owner = ME;
			showList.add(owner);
			if(cash.getValue()==null)cash.setValue(0L);
            String formattedValue = String.format("%.8f", ParseTools.satoshiToCoin(cash.getValue()))
                                        .replaceAll("0*$", "")  // Remove trailing zeros
                                        .replaceAll("\\.$", ""); // Remove decimal point if no decimals
            showList.add(formattedValue);
            showList.add(cash.getCd()==null?"":cash.getCd());
			showList.add(cash.getCdd()==null?"":cash.getCdd());
			showList.add(cash.getId());
            valueListList.add(showList);
        }
        Shower.showDataTable(title, fields, widths, valueListList, totalDisplayed, true);
    }

    public String getBirthBlockId() {
		return birthBlockId;
	}

	public void setBirthBlockId(String birthBlockId) {
		this.birthBlockId = birthBlockId;
	}

	public String getSpendBlockId() {
		return spendBlockId;
	}

	public void setSpendBlockId(String spendBlockId) {
		this.spendBlockId = spendBlockId;
	}
	public Integer getSpendTxIndex() {
		return spendTxIndex;
	}

	public void setSpendTxIndex(Integer spendTxIndex) {
		this.spendTxIndex = spendTxIndex;
	}

	public Integer getBirthIndex() {
		return birthIndex;
	}
	public void setBirthIndex(Integer birthIndex) {
		this.birthIndex = birthIndex;
	}
	public String getType() {
		return type;
	}
	public void setType(String type) {
		this.type = type;
	}
	public String getOwner() {
		return owner;
	}
	public void setOwner(String owner) {
		this.owner = owner;
	}
	public Long getValue() {
		return value;
	}
	public void setValue(Long value) {
		this.value = value;
	}
	public String getLockScript() {
		return lockScript;
	}
	public void setLockScript(String lockScript) {
		this.lockScript = lockScript;
	}
	public String getBirthTxId() {
		return birthTxId;
	}
	public void setBirthTxId(String birthTxId) {
		this.birthTxId = birthTxId;
	}
	public Integer getBirthTxIndex() {
		return birthTxIndex;
	}
	public void setBirthTxIndex(Integer birthTxIndex) {
		this.birthTxIndex = birthTxIndex;
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
	public Long getSpendTime() {
		return spendTime;
	}
	public void setSpendTime(Long spendTime) {
		this.spendTime = spendTime;
	}
	public String getSpendTxId() {
		return spendTxId;
	}
	public void setSpendTxId(String spendTxId) {
		this.spendTxId = spendTxId;
	}
	public Long getSpendHeight() {
		return spendHeight;
	}
	public void setSpendHeight(Long spendHeight) {
		this.spendHeight = spendHeight;
	}
	public Integer getSpendIndex() {
		return spendIndex;
	}
	public void setSpendIndex(Integer spendIndex) {
		this.spendIndex = spendIndex;
	}
	public String getUnlockScript() {
		return unlockScript;
	}
	public void setUnlockScript(String unlockScript) {
		this.unlockScript = unlockScript;
	}
	public String getSigHash() {
		return sigHash;
	}
	public void setSigHash(String sigHash) {
		this.sigHash = sigHash;
	}
	public String getSequence() {
		return sequence;
	}
	public void setSequence(String sequence) {
		this.sequence = sequence;
	}
	public Long getCdd() {
		return cdd;
	}
	public void setCdd(Long cdd) {
		this.cdd = cdd;
	}
	public Long getCd() {
		return cd;
	}
	public void setCd(Long cd) {
		this.cd = cd;
	}
	public Boolean isValid() {
		return valid;
	}
	public void setValid(Boolean valid) {
		this.valid = valid;
	}

	public String getIssuer() {
		return issuer;
	}

	public void setIssuer(String issuer) {
		this.issuer = issuer;
	}

	public Long getLastTime() {
		return lastTime;
	}

	public void setLastTime(Long lastTime) {
		this.lastTime = lastTime;
	}

	public Long getLastHeight() {
		return lastHeight;
	}

	public void setLastHeight(Long lastHeight) {
		this.lastHeight = lastHeight;
	}
}
