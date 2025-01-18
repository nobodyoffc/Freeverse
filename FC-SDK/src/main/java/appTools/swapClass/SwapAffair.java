package appTools.swapClass;

import java.beans.Transient;
import java.util.List;

public class SwapAffair {
    private String id;
    private String sid;
    private long sn;
    private SwapAct act;
    private ActData g;
    private ActData m;
    private long sendTime;
    private long getTime;
    private State state;
    private String error;


    public enum State{
        GOT,SORTED,FILTERED,
        ORDER_SUSPECTED, ORDER_VANISHED, ORDER_CONFIRMED,
        SWAPPING,SWAP_SUSPECTED, SWAP_VANISHED,SWAPPED,
        ADDED,
        WITHDRAWING,WITHDRAW_SUSPECTED,WITHDRAW_VANISHED, WITHDREW,
        REFUNDING, REFUND_SUSPECTED,REFUND_VANISHED,REFUNDED,
        IGNORING, IGNORE_SUSPECTED,IGNORE_VANISHED, IGNORED,
    }


    public static class ActData{
        private String txId;
        private String refundTxId;
        private String withdrawTxId;
        private double refundAmt;
        private double withdrawAmt;
        private String addr;
        private double amt;
        private double sum;
        private long blockTime;
        private long blockHeight;
        private long blockIndex;
        private double txFee;

        public double getWithdrawAmt() {
            return withdrawAmt;
        }

        public void setWithdrawAmt(double withdrawAmt) {
            this.withdrawAmt = withdrawAmt;
        }

        public String getTxId() {
            return txId;
        }

        public void setTxId(String txId) {
            this.txId = txId;
        }

        public String getAddr() {
            return addr;
        }

        public void setAddr(String addr) {
            this.addr = addr;
        }

        public double getAmt() {
            return amt;
        }

        public String getWithdrawTxId() {
            return withdrawTxId;
        }

        public void setWithdrawTxId(String withdrawTxId) {
            this.withdrawTxId = withdrawTxId;
        }

        public void setAmt(double amt) {
            this.amt = amt;
        }

        public double getSum() {
            return sum;
        }

        public void setSum(double sum) {
            this.sum = sum;
        }

        public long getBlockTime() {
            return blockTime;
        }

        public void setBlockTime(long blockTime) {
            this.blockTime = blockTime;
        }

        public long getBlockHeight() {
            return blockHeight;
        }

        public void setBlockHeight(long blockHeight) {
            this.blockHeight = blockHeight;
        }

        public long getBlockIndex() {
            return blockIndex;
        }

        public void setBlockIndex(long blockIndex) {
            this.blockIndex = blockIndex;
        }

        public String getRefundTxId() {
            return refundTxId;
        }

        public void setRefundTxId(String refundTxId) {
            this.refundTxId = refundTxId;
        }

        public double getRefundAmt() {
            return refundAmt;
        }

        public void setRefundAmt(double refundAmt) {
            this.refundAmt = refundAmt;
        }

        public double getTxFee() {
            return txFee;
        }

        public void setTxFee(double txFee) {
            this.txFee = txFee;
        }
    }
    public enum SwapAct {
        SWAP,ADD,WITHDRAW,IGNORE
    }
    public static void sortSwapAffairs(List<SwapAffair> swapAffairList) {
        swapAffairList.sort((SwapAffair swap1, SwapAffair swap2) -> {
            if (swap1.getG().getBlockTime() != 0 && swap2.getM().getBlockTime() != 0) {
                return Long.compare(
                        swap1.getG().getBlockTime(),
                        swap2.getM().getBlockTime()
                );
            } else if (swap1.getM().getBlockTime() != 0 && swap2.getG().getBlockTime() != 0) {
                return Long.compare(
                        swap1.getM().getBlockTime(),
                        swap2.getG().getBlockTime()
                );
            }if (swap1.getG().getBlockTime() != 0 && swap2.getG().getBlockTime() != 0) {

                int x = Long.compare(
                        swap1.getG().getBlockHeight(),
                        swap2.getG().getBlockHeight()
                );
                if(x!=0)return x;
                return Long.compare(
                        swap1.getG().getBlockIndex(),
                        swap2.getG().getBlockIndex());

            }else if (swap1.getM().getBlockTime() != 0 && swap2.getM().getBlockTime() != 0) {
                int x= Long.compare(
                        swap1.getM().getBlockHeight(),
                        swap2.getM().getBlockHeight()
                );
                if(x!=0)return x;
                return Long.compare(
                        swap1.getM().getBlockIndex(),
                        swap2.getM().getBlockIndex());

            }
            return 0;
        });
    }

    @Transient
    public static boolean isFromGoods(SwapAffair swapAffair) {
        boolean isGoods;
        isGoods = swapAffair.getG().getAmt()>0;
        return isGoods;
    }

    @Transient
    public  boolean isFromGoods() {
        return this.id.equals(this.g.txId);
    }


    public long getSn() {
        return sn;
    }

    public void setSn(long sn) {
        this.sn = sn;
    }

    public SwapAct getAct() {
        return act;
    }

    public void setAct(SwapAct act) {
        this.act = act;
    }

    public ActData getG() {
        return g;
    }

    public void setG(ActData g) {
        this.g = g;
    }

    public ActData getM() {
        return m;
    }

    public void setM(ActData m) {
        this.m = m;
    }

    public long getSendTime() {
        return sendTime;
    }

    public void setSendTime(long sendTime) {
        this.sendTime = sendTime;
    }

    public long getGetTime() {
        return getTime;
    }

    public void setGetTime(long getTime) {
        this.getTime = getTime;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSid() {
        return sid;
    }

    public void setSid(String sid) {
        this.sid = sid;
    }

}
