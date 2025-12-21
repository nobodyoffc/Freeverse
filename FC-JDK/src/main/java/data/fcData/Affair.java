package data.fcData;

import core.crypto.KeyTools;
import utils.Hex;

public class Affair extends FcObject {
    public static String NAME = "affair";

    //Subject
    private String fid;
    private String pubkey;

    //Operation
    private Op op; // For operating affairs.
    private String opType;

    //Object
    private String oid;

    private Object data;
    private String dataStr;

    private String fidB;
    private String pubkeyB;

    private String oidB;
    private Object dataB;


    public static Affair makeNotifyAffair(String fromFid, String recipientFid, String message) {
        // Create payment notice affair
        Affair affair = new Affair();
        affair.setOp(Op.NOTIFY);
        affair.setFid(fromFid);
        affair.setFidB(recipientFid);
        affair.setData(message);
        return affair;
    }

    /*
    {
	"meta": "FC",
	"op": "encrypt",
	"fid": "FB7UTj7vH6Qp69pNbKCJjv1WSAz2m9XRe7",
	"oid": "069a301c1662f6d4fcb79b077a826af7c36dca120ee77684535f43479c89af80",
	"data": {
		"type": "AsyOneWay",
		"alg": "EccAes256K1P7@No1_NrC7",
		"pubKeyA": "02566bd9bed0b9634e9a1de24f2fd3e2f865f93af8fdd704a3e03e99922340c5d2",
		"pubKeyB": "025e953ff8f711e95e5471e366472db2baa39242dc4540fc3ba46e68c0470d4ea1",
		"iv": "3f1d212333ebd2b2da2641ef2fbbfcd6",
		"sum": "c8c46536",
		"badSum": false
	}
}
     */

    public String getDataStr() {
        return dataStr;
    }

    public void setDataStr(String dataStr) {
        this.dataStr = dataStr;
    }


    public String getFidB() {
        return fidB;
    }

    public void setFidB(String fidB) {
        this.fidB = fidB;
    }

    public String getOidB() {
        return oidB;
    }

    public void setOidB(String oidB) {
        this.oidB = oidB;
    }


    public Op getOp() {
        return op;
    }

    public void setOp(Op op) {
        this.op = op;
    }

    public String getFid() {
        return fid;
    }

    public void setFid(String fid) {
        this.fid = fid;
    }

    public String getOid() {
        return oid;
    }

    public void setOid(String oid) {
        this.oid = oid;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public String getPubkey() {
        return pubkey;
    }

    public void setPubkey(String pubkey) {
        this.pubkey = pubkey;
        if(this.fid==null  && pubkey!=null)
            this.fid = KeyTools.pubkeyToFchAddr(Hex.fromHex(pubkey));
    }

    public String getPubkeyB() {
        return pubkeyB;
    }

    public void setPubkeyB(String pubkeyB) {
        this.pubkeyB = pubkeyB;
        if(this.fidB==null && pubkeyB!=null)
            this.fidB = KeyTools.pubkeyToFchAddr(Hex.fromHex(pubkeyB));
    }

    public Object getDataB() {
        return dataB;
    }

    public void setDataB(Object dataB) {
        this.dataB = dataB;
    }

    public String getOpType() {
        return opType;
    }

    public void setOpType(String opType) {
        this.opType = opType;
    }

}
