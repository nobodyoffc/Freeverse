package server.reward;

import fch.fchData.Cash;

import java.util.List;

public class Payroll {

    RewardInfo rewardInfo;
    String account;
    fch.DataForOffLineTx dataForOffLineTx;
    List<Cash> meetCashList;

    public Payroll(String account,RewardInfo rewardInfo) {
        this.rewardInfo = rewardInfo;
        this.account = account;
    }

    public String makePayrollJson(){

        return null;
    }


}
