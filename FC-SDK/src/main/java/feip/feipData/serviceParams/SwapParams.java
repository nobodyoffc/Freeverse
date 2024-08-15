package feip.feipData.serviceParams;

import clients.apipClient.ApipClient;
import fch.FchMainNetwork;
import feip.feipData.Service;
import appTools.Inputer;
import appTools.Menu;
import appTools.Shower;

import com.google.gson.Gson;

import crypto.old.EccAes256K1P7;
import crypto.KeyTools;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.params.MainNetParams;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;

import static fch.Inputer.inputGoodFid;
import static appTools.Inputer.askIfYes;
import static constants.Strings.BTC;
import static constants.Strings.LTC;
import static constants.Ticks.*;


public class SwapParams extends Params {
    private String goods;
    private String money;
    private String gTick;
    private String mTick;
    private String gAddr;
    private String mAddr;
    private String swapFee;
    private String serviceFee;
    private String gConfirm;
    private String mConfirm;
    private String gWithdrawFee;
    private String mWithdrawFee;
    private String curve;
    private transient String priKeyCipher;
    private transient ApipClient apipClient;

    public SwapParams(ApipClient apipClient) {
        this.apipClient = apipClient;
    }

    public SwapParams() {
    }

    public void inputParams(BufferedReader br, byte[] symKey) {
        this.goods = Inputer.inputString(br,"Input the name of the goods:");
        this.money = Inputer.inputString(br,"Input the name of the money:");
        this.gTick = Inputer.inputString(br,"Input the tick of the goods:");
        this.mTick = Inputer.inputString(br,"Input the tick of the money:");
        priKeyCipher = setAddrs(br, symKey);
        this.curve = Inputer.inputString(br,"Input the curve formula of the AMM:");
        this.gConfirm = Inputer.inputIntegerStr(br,"Input the required confirmation for goods payment:");
        this.mConfirm = Inputer.inputIntegerStr(br,"Input the required confirmation for money payment:");
        this.gWithdrawFee = Inputer.inputDoubleAsString(br,"Input the fee charged when withdrawing goods from LP:");
        this.mWithdrawFee = Inputer.inputDoubleAsString(br,"Input the fee charged when withdrawing money from LP:");
        this.swapFee = Inputer.inputDoubleAsString(br,"Input the fee charged for LPs:");
        this.serviceFee = Inputer.inputDoubleAsString(br,"Input the fee for the owner:");
    }

    public void updateParams(BufferedReader br, byte[] symKey) {
        priKeyCipher = null;
        System.out.println("The goods address is " + gAddr);
        System.out.println("The money address is " + mAddr);
        if(Inputer.askIfYes(br,"Update dealer addresses?")){
            priKeyCipher = setAddrs(br, symKey.clone());
        }
        updateGoods(br);
        updateMoney(br);
        updateGTick(br);
        updateMTick(br);
        updateCurve(br);
        updateGConfirm(br);
        updateMConfirm(br);
        updateGWithdrawFee(br);
        updateMWithdrawFee(br);
        updateSwapFee(br);
        updateServiceFee(br);
    }

    public static SwapParams getParamsFromService(Service service) {
        SwapParams params;
        Gson gson = new Gson();
        try {
            params = gson.fromJson(gson.toJson(service.getParams()), SwapParams.class);
        }catch (Exception e){
            System.out.println("Parse maker parameters from Service wrong.");
            return null;
        }
        return params;
    }

    @Nullable
    private String setAddrs(BufferedReader br, byte[] initSymKey) {
        String priKeyCipher =null;
        if(askIfYes(br,"Generate a new dealer?")){

            ECKey ecKey = KeyTools.generateNewPriKey(br);
            if(ecKey==null){
                System.out.println("Failed to generate new priKey.");
                Menu.anyKeyToContinue(br);
                return null;
            }
            byte[] priKey = ecKey.getPrivKeyBytes();
            priKeyCipher = EccAes256K1P7.encryptWithSymKey(priKey, initSymKey.clone());
            setAddr(ecKey, true,gTick);
            setAddr(ecKey,false, mTick);
            Shower.printUnderline(10);
            System.out.println("Goods addr is "+gAddr);
            System.out.println("Money addr is "+gAddr);
            Shower.printUnderline(10);
            if(apipClient!=null)apipClient.checkMaster(priKeyCipher,initSymKey,br);
        }else {
            this.gAddr = Inputer.inputString(br, "Input the dealer address of the goods:");
            this.mAddr = Inputer.inputString(br, "Input the dealer address of the money:");
        }

        return priKeyCipher;
    }

    private void setAddr(ECKey ecKey, boolean isGoods, String tick) {
        switch (tick) {
            case FCH -> {
                if(isGoods)gAddr = ecKey.toAddress(FchMainNetwork.MAINNETWORK).toBase58();
                else mAddr = ecKey.toAddress(FchMainNetwork.MAINNETWORK).toBase58();
            }
            case BTC,BCH,XEC -> {
                if(isGoods)gAddr =ecKey.toAddress(MainNetParams.get()).toBase58();
                else mAddr = ecKey.toAddress(MainNetParams.get()).toBase58();
            }
            case DOGE -> {
                if(isGoods)gAddr = KeyTools.pubKeyToDogeAddr(ecKey.getPublicKeyAsHex());
                else mAddr = KeyTools.pubKeyToDogeAddr(ecKey.getPublicKeyAsHex());
            }
            case LTC -> {
                if(isGoods)gAddr =KeyTools.pubKeyToLtcAddr(ecKey.getPublicKeyAsHex());
                else mAddr =KeyTools.pubKeyToLtcAddr(ecKey.getPublicKeyAsHex());
            }
        }
    }

//    public String updateParams(BufferedReader br, byte[] initSymKey){
//        String priKeyCipher = null;
//        System.out.println("The goods address is " + gAddr);
//        System.out.println("The money address is " + mAddr);
//        if(Inputer.askIfYes(br,"Update dealer addresses? y/n:")){
//            priKeyCipher = setAddrs(br, initSymKey.clone());
//        }
//        updateGoods(br);
//        updateMoney(br);
//        updateGTick(br);
//        updateMTick(br);
//        updateCurve(br);
//        updateGConfirm(br);
//        updateMConfirm(br);
//        updateGWithdrawFee(br);
//        updateMWithdrawFee(br);
//        updateSwapFee(br);
//        updateServiceFee(br);
//        return priKeyCipher;
//    }

    private void updateGAddr(BufferedReader br) {
        System.out.println("The goods address is " + gAddr);
        if(Inputer.askIfYes(br,"Change it?")) gAddr = Inputer.inputString(br, "Input the address of the goods:");
    }
    private void updateMAddr(BufferedReader br) {
        System.out.println("The money address is " + mAddr);
        if(Inputer.askIfYes(br,"Change it?")) mAddr = inputGoodFid(br, "Input the address of the money:");
    }
    private void updateGoods(BufferedReader br) {
        System.out.println("The goods is " +goods);
        if(Inputer.askIfYes(br,"Change it?")) goods= Inputer.inputString(br, "Input the goods:");
    }
    private void updateMoney(BufferedReader br) {
        System.out.println("The mTick is " +money);
        if(Inputer.askIfYes(br,"Change it?")) money= Inputer.inputString(br, "Input the money:");
    }
    private void updateGTick(BufferedReader br) {
        System.out.println("The gTick is " +gTick);
        if(Inputer.askIfYes(br,"Change it?")) gTick= Inputer.inputString(br, "Input the gTick:");
    }
    private void updateMTick(BufferedReader br) {
        System.out.println("The mTick is " +mTick);
        if(Inputer.askIfYes(br,"Change it?")) mTick= Inputer.inputString(br, "Input the mTick:");
    }
    private void updateCurve(BufferedReader br) {
        System.out.println("The curve is " +curve);
        if(Inputer.askIfYes(br,"Change it?")) curve= Inputer.inputString(br, "Input the curve formula of AMM:");
    }
    private void updateGConfirm(BufferedReader br) {
        System.out.println("The gConfirm is " +gConfirm);
        if(Inputer.askIfYes(br,"Change it?")) gConfirm= Inputer.inputIntegerStr(br, "Input the gConfirm:");
    }
    private void updateMConfirm(BufferedReader br) {
        System.out.println("The mConfirm is " +mConfirm);
        if(Inputer.askIfYes(br,"Change it?")) mConfirm= Inputer.inputIntegerStr(br, "Input the mConfirm:");
    }
    private void updateGWithdrawFee(BufferedReader br) {
        System.out.println("The gWithdrawFee is " +gWithdrawFee);
        if(Inputer.askIfYes(br,"Change it?")) gWithdrawFee= Inputer.inputDoubleAsString(br, "Input the " + gWithdrawFee + ":");
    }
    private void updateMWithdrawFee(BufferedReader br) {
        System.out.println("The mWithdrawFee is " +mWithdrawFee);
        if(Inputer.askIfYes(br,"Change it?")) mWithdrawFee= Inputer.inputDoubleAsString(br, "Input the mWithdrawFee:");
    }
    private void updateSwapFee(BufferedReader br) {
        System.out.println("The gWithdrawFee is " +swapFee);
        if(Inputer.askIfYes(br,"Change it?")) swapFee= Inputer.inputDoubleAsString(br, "Input the " + swapFee + ":");
    }
    private void updateServiceFee(BufferedReader br) {
        System.out.println("The serviceFee is " +serviceFee);
        if(Inputer.askIfYes(br,"Change it?"))serviceFee= Inputer.inputDoubleAsString(br, "Input the serviceFee:");
    }

    public String getGoods() {
        return goods;
    }

    public void setGoods(String goods) {
        this.goods = goods;
    }

    public String getMoney() {
        return money;
    }

    public void setMoney(String money) {
        this.money = money;
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

    public String getCurve() {
        return curve;
    }

    public void setCurve(String curve) {
        this.curve = curve;
    }

    public String getPriKeyCipher() {
        return priKeyCipher;
    }

    public void setPriKeyCipher(String priKeyCipher) {
        this.priKeyCipher = priKeyCipher;
    }

    public ApipClient getApipClient() {
        return apipClient;
    }

    public void setApipClient(ApipClient apipClient) {
        this.apipClient = apipClient;
    }
}
