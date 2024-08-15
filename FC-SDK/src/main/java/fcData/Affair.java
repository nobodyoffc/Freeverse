package fcData;

public class Affair {
    private String meta = "FC";
    private Op op;
    private Relation rela;
    private String fid;
    private String oid;
    private String fidB;
    private String oidB;
    private Object data;
    private String dataBase64;

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

    public String getDataBase64() {
        return dataBase64;
    }

    public void setDataBase64(String dataBase64) {
        this.dataBase64 = dataBase64;
    }

    public Relation getRela() {
        return rela;
    }

    public void setRela(Relation rela) {
        this.rela = rela;
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

    public String getMeta() {
        return meta;
    }

    public void setMeta(String meta) {
        this.meta = meta;
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
}
