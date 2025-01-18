package nasa.data;

class TransactionDetail {
    private boolean involvesWatchonly;
    private String account;
    private String address;
    private String category;
    private double amount;
    private String label;
    private int vout;
    private double fee;
    private boolean abandoned;

    // Getters and setters for the fields

    public boolean isInvolvesWatchonly() {
        return involvesWatchonly;
    }

    public void setInvolvesWatchonly(boolean involvesWatchonly) {
        this.involvesWatchonly = involvesWatchonly;
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

    public double getFee() {
        return fee;
    }

    public void setFee(double fee) {
        this.fee = fee;
    }

    public boolean isAbandoned() {
        return abandoned;
    }

    public void setAbandoned(boolean abandoned) {
        this.abandoned = abandoned;
    }
}
