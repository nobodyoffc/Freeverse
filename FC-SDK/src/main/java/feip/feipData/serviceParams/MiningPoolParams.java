package feip.feipData.serviceParams;

import feip.feipData.Service;
import appTools.Inputer;
import com.google.gson.Gson;

import java.io.BufferedReader;
import java.util.Arrays;


public class MiningPoolParams {
    private String cashier;
    private String feeRate;
    private String feeMode;
    private String[] ticks;
    private String payDays;

    public static MiningPoolParams getParamsFromService(Service service) {
        MiningPoolParams params;
        Gson gson = new Gson();
        try {
            params = gson.fromJson(gson.toJson(service.getParams()), MiningPoolParams.class);
        }catch (Exception e){
            System.out.println("Parse maker parameters from Service wrong.");
            return null;
        }
        return params;
    }
    public void inputParams(BufferedReader br){
        this.cashier = Inputer.inputString(br,"Input the fid of the mining income receiver:");
        this.feeMode = Inputer.inputString(br,"Input the fee mode:");
        this.ticks = Inputer.inputStringArray(br,"Input the ticks you are mining:",0);
        this.feeRate = Inputer.inputDoubleAsString(br,"Input the fee rate:");
        this.payDays = Inputer.inputDoubleAsString(br,"Input the number of days on which you pay miner:");
    }

    public void updateParams(BufferedReader br, byte[] initSymKey){
        updateCashier(br);
        updateFeeRate(br);
        updateFeeMode(br);
        updateTicks(br);
        updatePayDays(br);
    }

    private void updateCashier(BufferedReader br) {
        System.out.println("The cashier is " +cashier);
        if(Inputer.askIfYes(br,"Change it?")) cashier= Inputer.inputString(br, "Input the cashier:");
    }
    private void updateFeeRate(BufferedReader br) {
        System.out.println("The feeRate is " + feeRate);
        if(Inputer.askIfYes(br,"Change it?")) feeRate = Inputer.inputString(br, "Input the feeRate:");
    }
    private void updateFeeMode(BufferedReader br) {
        System.out.println("The feeMode is " + feeMode);
        if(Inputer.askIfYes(br,"Change it?")) feeMode = Inputer.inputString(br, "Input the feeMode:");
    }
    private void updateTicks(BufferedReader br) {
        System.out.println("The ticks is " + Arrays.toString(ticks));
        if(Inputer.askIfYes(br,"Change it?")) ticks = Inputer.inputStringArray(br, "Input the ticks:",0);
    }
    private void updatePayDays(BufferedReader br) {
        System.out.println("The payDays is " +payDays);
        if(Inputer.askIfYes(br,"Change it?")) payDays= Inputer.inputString(br, "Input the payDays:");
    }

    public String getCashier() {
        return cashier;
    }

    public void setCashier(String cashier) {
        this.cashier = cashier;
    }

    public String getFeeRate() {
        return feeRate;
    }

    public void setFeeRate(String feeRate) {
        this.feeRate = feeRate;
    }

    public String getFeeMode() {
        return feeMode;
    }

    public void setFeeMode(String feeMode) {
        this.feeMode = feeMode;
    }

    public String[] getTicks() {
        return ticks;
    }

    public void setTicks(String[] ticks) {
        this.ticks = ticks;
    }

    public String getPayDays() {
        return payDays;
    }

    public void setPayDays(String payDays) {
        this.payDays = payDays;
    }
}
