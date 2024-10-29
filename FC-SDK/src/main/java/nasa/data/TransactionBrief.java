package nasa.data;


import java.util.Arrays;

public class TransactionBrief {
    private String account;
    private String address;
    private String category;
    private double amount;
    private String label;
    private int vout;
    private int confirmations;
    private String blockhash;
    private int blockindex;
    private long blocktime;
    private String txid;
    private String[] walletconflicts;
    private long time;
    private long timereceived;
    private String bip125Replaceable;

    // Add getters and setters for each field

    // You can also override toString() for debugging purposes

    @Override
    public String toString() {
        return "Transaction{" +
                "account='" + account + '\'' +
                ", address='" + address + '\'' +
                ", category='" + category + '\'' +
                ", amount=" + amount +
                ", label='" + label + '\'' +
                ", vout=" + vout +
                ", confirmations=" + confirmations +
                ", blockhash='" + blockhash + '\'' +
                ", blockindex=" + blockindex +
                ", blocktime=" + blocktime +
                ", txid='" + txid + '\'' +
                ", walletconflicts=" + Arrays.toString(walletconflicts) +
                ", time=" + time +
                ", timereceived=" + timereceived +
                ", bip125Replaceable='" + bip125Replaceable + '\'' +
                '}';
    }

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public int getVout() {
        return vout;
    }

    public void setVout(int vout) {
        this.vout = vout;
    }

    public int getConfirmations() {
        return confirmations;
    }

    public void setConfirmations(int confirmations) {
        this.confirmations = confirmations;
    }

    public String getBlockhash() {
        return blockhash;
    }

    public void setBlockhash(String blockhash) {
        this.blockhash = blockhash;
    }

    public int getBlockindex() {
        return blockindex;
    }

    public void setBlockindex(int blockindex) {
        this.blockindex = blockindex;
    }

    public long getBlocktime() {
        return blocktime;
    }

    public void setBlocktime(long blocktime) {
        this.blocktime = blocktime;
    }

    public String getTxid() {
        return txid;
    }

    public void setTxid(String txid) {
        this.txid = txid;
    }

    public String[] getWalletconflicts() {
        return walletconflicts;
    }

    public void setWalletconflicts(String[] walletconflicts) {
        this.walletconflicts = walletconflicts;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public long getTimereceived() {
        return timereceived;
    }

    public void setTimereceived(long timereceived) {
        this.timereceived = timereceived;
    }

    public String getBip125Replaceable() {
        return bip125Replaceable;
    }

    public void setBip125Replaceable(String bip125Replaceable) {
        this.bip125Replaceable = bip125Replaceable;
    }
}


