package swap;

public class SwapInfoData {
    private String sid;
    private String name;
    private String owner;
    private String tRate;
    private String tCdd;
    private String gTick;
    private String mTick;
    private String gAddr;
    private String mAddr;
    private String gConfirm;
    private String mConfirm;
    private String swapFee;
    private String serviceFee;
    private String gWithdrawFee;
    private String mWithdrawFee;

    private double gSum;
    private double mSum;
    private double gPendingSum;
    private double mPendingSum;
    private double price;
    private long lastTime;

    public String getSid() {
        return sid;
    }

    public void setSid(String sid) {
        this.sid = sid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String gettRate() {
        return tRate;
    }

    public void settRate(String tRate) {
        this.tRate = tRate;
    }

    public String gettCdd() {
        return tCdd;
    }

    public void settCdd(String tCdd) {
        this.tCdd = tCdd;
    }

    public String getgTick() {
        return gTick;
    }

    public void setgTick(String gTick) {
        this.gTick = gTick;
    }

    public String getmTick() {
        return mTick;
    }

    public void setmTick(String mTick) {
        this.mTick = mTick;
    }

    public String getgAddr() {
        return gAddr;
    }

    public void setgAddr(String gAddr) {
        this.gAddr = gAddr;
    }

    public String getmAddr() {
        return mAddr;
    }

    public void setmAddr(String mAddr) {
        this.mAddr = mAddr;
    }

    public String getgConfirm() {
        return gConfirm;
    }

    public void setgConfirm(String gConfirm) {
        this.gConfirm = gConfirm;
    }

    public String getmConfirm() {
        return mConfirm;
    }

    public void setmConfirm(String mConfirm) {
        this.mConfirm = mConfirm;
    }
    public String getgWithdrawFee() {
        return gWithdrawFee;
    }

    public void setgWithdrawFee(String gWithdrawFee) {
        this.gWithdrawFee = gWithdrawFee;
    }

    public String getmWithdrawFee() {
        return mWithdrawFee;
    }

    public void setmWithdrawFee(String mWithdrawFee) {
        this.mWithdrawFee = mWithdrawFee;
    }

    public double getgSum() {
        return gSum;
    }

    public void setgSum(double gSum) {
        this.gSum = gSum;
    }

    public double getmSum() {
        return mSum;
    }

    public void setmSum(double mSum) {
        this.mSum = mSum;
    }

    public double getgPendingSum() {
        return gPendingSum;
    }

    public void setgPendingSum(double gPendingSum) {
        this.gPendingSum = gPendingSum;
    }

    public double getmPendingSum() {
        return mPendingSum;
    }

    public void setmPendingSum(double mPendingSum) {
        this.mPendingSum = mPendingSum;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public long getLastTime() {
        return lastTime;
    }

    public void setLastTime(long lastTime) {
        this.lastTime = lastTime;
    }

    public String getSwapFee() {
        return swapFee;
    }

    public void setSwapFee(String swapFee) {
        this.swapFee = swapFee;
    }

    public String getServiceFee() {
        return serviceFee;
    }

    public void setServiceFee(String serviceFee) {
        this.serviceFee = serviceFee;
    }
}
