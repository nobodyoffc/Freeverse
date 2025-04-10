package server.reward;

import fch.OffLineTxInfo;
import fch.fchData.Cash;

import java.util.List;

public class Payroll {

    RewardInfo rewardInfo;
    String account;
    OffLineTxInfo offLineTxInfo;
    List<Cash> meetCashList;

    public Payroll(String account,RewardInfo rewardInfo) {
        this.rewardInfo = rewardInfo;
        this.account = account;
    }

    public String makePayrollJson(){

        return null;
    }


}
