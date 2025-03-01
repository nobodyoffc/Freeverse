package fch.fchData;

import fcData.FcObject;

import java.util.ArrayList;

public class TxHasInfo extends FcObject {
    private Tx tx;
    private ArrayList<Cash> inCashList;
    private ArrayList<Cash> outCashList;

    public Tx getTx() {
        return tx;
    }

    public void setTx(Tx tx) {
        this.tx = tx;
    }

    public ArrayList<Cash> getInCashList() {
        return inCashList;
    }

    public void setInCashList(ArrayList<Cash> inCashList) {
        this.inCashList = inCashList;
    }

    public ArrayList<Cash> getOutCashList() {
        return outCashList;
    }

    public void setOutCashList(ArrayList<Cash> outCashList) {
        this.outCashList = outCashList;
    }
}
