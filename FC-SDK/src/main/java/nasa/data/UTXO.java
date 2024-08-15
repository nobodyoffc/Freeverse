package nasa.data;

public class UTXO {
    private String txid;
    private int vout;
    private String address;
    private String account;
    private String scriptPubKey;
    private double amount;
    private int confirmations;
    private String redeemScript;
    private boolean spendable;
    private boolean solvable;

    // Constructor
    public UTXO(
            String txid,
            int vout,
            String address,
            String account,
            String scriptPubKey,
            double amount,
            int confirmations,
            String redeemScript,
            boolean spendable,
            boolean solvable) {
        this.txid = txid;
        this.vout = vout;
        this.address = address;
        this.account = account;
        this.scriptPubKey = scriptPubKey;
        this.amount = amount;
        this.confirmations = confirmations;
        this.redeemScript = redeemScript;
        this.spendable = spendable;
        this.solvable = solvable;
    }

    //    {
//        "result": [{
//        "txid": "17c9ce8f90d1848d483c5fcba0afd700851072ddbdf7bf88f7430e02743a0893",
//                "vout": 0,
//                "address": "DJgizn89UvzNAW2wYAgN4qot82H9hjmAHM",
//                "account": "",
//                "scriptPubKey": "76a9149494f686025f04afe4c837139ea849b58ff3b99988ac",
//                "amount": 10.00000000,
//                "confirmations": 0,
//                "spendable": true,
//                "solvable": true
//    }],
//        "error": null,
//            "id": "1"
//    }
    public UTXO() {
    }

    // Getters and setters (optional)
    // You can generate getters and setters for the private fields using your IDE.
    // For brevity, they are not included here.

    public String getTxid() {
        return txid;
    }

    public void setTxid(String txid) {
        this.txid = txid;
    }

    public int getVout() {
        return vout;
    }

    public void setVout(int vout) {
        this.vout = vout;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getScriptPubKey() {
        return scriptPubKey;
    }

    public void setScriptPubKey(String scriptPubKey) {
        this.scriptPubKey = scriptPubKey;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public int getConfirmations() {
        return confirmations;
    }

    public void setConfirmations(int confirmations) {
        this.confirmations = confirmations;
    }

    public String getRedeemScript() {
        return redeemScript;
    }

    public void setRedeemScript(String redeemScript) {
        this.redeemScript = redeemScript;
    }

    public boolean isSpendable() {
        return spendable;
    }

    public void setSpendable(boolean spendable) {
        this.spendable = spendable;
    }

    public boolean isSolvable() {
        return solvable;
    }

    public void setSolvable(boolean solvable) {
        this.solvable = solvable;
    }
}

