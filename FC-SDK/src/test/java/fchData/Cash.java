package fchData;

public class Cash {

    //calculated
    private String cashId;    //hash of this cash: sha256(sha256(tx + index)).
    private String issuer; //first input fid when this cash was born.

    //from utxo
    private int birthIndex;        //index of cash. Order in cashs of the tx when created.
    private String type;    //type of the script. P2PKH,P2SH,OP_RETURN,Unknown,MultiSig
    private String owner;    //address
    private long value;        //in satoshi
    private String lockScript;    //LockScript
    private String birthTxId;        //txid, hash in which this cash was created.
    private int birthTxIndex;        //Order in the block of the tx in which this cash was created.
    private String birthBlockId;        //block ID, hash of block head
    private long birthTime;        //Block time when this cash is created.
    private long birthHeight;        //Block height.

    //from input
    private long spendTime;    //Block time when spent.
    private String spendTxId;    //Tx hash when spent.
    private long spendHeight;    //Block height when spent.
    private int spendTxIndex;        //Order in the block of the tx in which this cash was spent.
    private String spendBlockId;        //block ID, hash of block head
    private int spendIndex;        //Order in inputs of the tx when spent.
    private String unlockScript;    //unlock script.
    private String sigHash;    //sigHash.
    private String sequence;    //nSequence
    private long cdd;        //CoinDays Destroyed
    private long cd;        //CoinDays
    private boolean valid;    //Is this cash valid (utxo), or spent (stxo);

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

    public int getSpendTxIndex() {
        return spendTxIndex;
    }

    public void setSpendTxIndex(int spendTxIndex) {
        this.spendTxIndex = spendTxIndex;
    }

    public String getCashId() {
        return cashId;
    }

    public void setCashId(String cashId) {
        this.cashId = cashId;
    }

    public int getBirthIndex() {
        return birthIndex;
    }

    public void setBirthIndex(int birthIndex) {
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

    public long getValue() {
        return value;
    }

    public void setValue(long value) {
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

    public int getBirthTxIndex() {
        return birthTxIndex;
    }

    public void setBirthTxIndex(int birthTxIndex) {
        this.birthTxIndex = birthTxIndex;
    }

    public long getBirthTime() {
        return birthTime;
    }

    public void setBirthTime(long birthTime) {
        this.birthTime = birthTime;
    }

    public long getBirthHeight() {
        return birthHeight;
    }

    public void setBirthHeight(long birthHeight) {
        this.birthHeight = birthHeight;
    }

    public long getSpendTime() {
        return spendTime;
    }

    public void setSpendTime(long spendTime) {
        this.spendTime = spendTime;
    }

    public String getSpendTxId() {
        return spendTxId;
    }

    public void setSpendTxId(String spendTxId) {
        this.spendTxId = spendTxId;
    }

    public long getSpendHeight() {
        return spendHeight;
    }

    public void setSpendHeight(long spendHeight) {
        this.spendHeight = spendHeight;
    }

    public int getSpendIndex() {
        return spendIndex;
    }

    public void setSpendIndex(int spendIndex) {
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

    public long getCdd() {
        return cdd;
    }

    public void setCdd(long cdd) {
        this.cdd = cdd;
    }

    public long getCd() {
        return cd;
    }

    public void setCd(long cd) {
        this.cd = cd;
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }
}
