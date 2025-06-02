package server.reward;


import core.fch.RawTxInfo;
import data.fchData.Cash;

import java.util.List;

public class Payroll {

    RewardInfo rewardInfo;
    String account;
    RawTxInfo rawTxInfo;
    List<Cash> meetCashList;

    public Payroll(String account,RewardInfo rewardInfo) {
        this.rewardInfo = rewardInfo;
        this.account = account;
    }

    public String makePayrollJson(){

        return null;
    }


}
