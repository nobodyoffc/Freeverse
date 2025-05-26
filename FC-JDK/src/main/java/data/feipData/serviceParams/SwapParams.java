package data.feipData.serviceParams;

import clients.ApipClient;
import data.feipData.Service;
import core.fch.FchMainNetwork;
import ui.Inputer;
import ui.Menu;
import ui.Shower;

import com.google.gson.Gson;

import core.crypto.old.EccAes256K1P7;
import core.crypto.KeyTools;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.params.MainNetParams;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;

import static core.fch.Inputer.inputGoodFid;
import static ui.Inputer.askIfYes;
import static constants.Strings.BTC;
import static constants.Strings.LTC;
import static constants.Tickers.*;

@SuppressWarnings("unused")
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
    private transient String prikeyCipher;
    private transient ApipClient apipClient;

    public SwapParams(ApipClient apipClient) {
        this.apipClient = apipClient;
    }

    public SwapParams() {
    }

    public void inputParams(BufferedReader br, byte[] symkey) {
        this.goods = Inputer.inputString(br,"Input the name of the goods:");
        this.money = Inputer.inputString(br,"Input the name of the money:");
        this.gTick = Inputer.inputString(br,"Input the tick of the goods:");
        this.mTick = Inputer.inputString(br,"Input the tick of the money:");
        prikeyCipher = setAddrs(br, symkey);
        this.curve = Inputer.inputString(br,"Input the curve formula of the AMM:");
        this.gConfirm = Inputer.inputIntegerStr(br,"Input the required confirmation for goods payment:");
        this.mConfirm = Inputer.inputIntegerStr(br,"Input the required confirmation for money payment:");
        this.gWithdrawFee = Inputer.inputDoubleAsString(br,"Input the fee charged when withdrawing goods from LP:");
        this.mWithdrawFee = Inputer.inputDoubleAsString(br,"Input the fee charged when withdrawing money from LP:");
        this.swapFee = Inputer.inputDoubleAsString(br,"Input the fee charged for LPs:");
        this.serviceFee = Inputer.inputDoubleAsString(br,"Input the fee for the owner:");
    }

    public void updateParams(BufferedReader br, byte[] symkey) {
        prikeyCipher = null;
        System.out.println("The goods address is " + gAddr);
        System.out.println("The money address is " + mAddr);
        if(Inputer.askIfYes(br,"Update dealer addresses?")){
            prikeyCipher = setAddrs(br, symkey.clone());
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
    private String setAddrs(BufferedReader br, byte[] initSymkey) {
        String prikeyCipher =null;
        if(askIfYes(br,"Generate a new dealer?")){

            ECKey ecKey = KeyTools.generateNewPrikey(br);
            if(ecKey==null){
                System.out.println("Failed to generate new prikey.");
                Menu.anyKeyToContinue(br);
                return null;
            }
            byte[] prikey = ecKey.getPrivKeyBytes();
            prikeyCipher = EccAes256K1P7.encryptWithSymkey(prikey, initSymkey.clone());
            setAddr(ecKey, true,gTick);
            setAddr(ecKey,false, mTick);
            Shower.printUnderline(10);
            System.out.println("Goods addr is "+gAddr);
            System.out.println("Money addr is "+gAddr);
            Shower.printUnderline(10);
            if(apipClient!=null)apipClient.checkMaster(prikeyCipher,initSymkey,br);
        }else {
            this.gAddr = Inputer.inputString(br, "Input the dealer address of the goods:");
            this.mAddr = Inputer.inputString(br, "Input the dealer address of the money:");
        }

        return prikeyCipher;
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
                if(isGoods)gAddr = KeyTools.pubkeyToDogeAddr(ecKey.getPublicKeyAsHex());
                else mAddr = KeyTools.pubkeyToDogeAddr(ecKey.getPublicKeyAsHex());
            }
            case LTC -> {
                if(isGoods)gAddr =KeyTools.pubkeyToLtcAddr(ecKey.getPublicKeyAsHex());
                else mAddr =KeyTools.pubkeyToLtcAddr(ecKey.getPublicKeyAsHex());
            }
        }
    }

//    public String updateParams(BufferedReader br, byte[] initSymkey){
//        String prikeyCipher = null;
//        System.out.println("The goods address is " + gAddr);
//        System.out.println("The money address is " + mAddr);
//        if(Inputer.askIfYes(br,"Update dealer addresses? y/n:")){
//            prikeyCipher = setAddrs(br, initSymkey.clone());
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
//        return prikeyCipher;
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

    public String getPrikeyCipher() {
        return prikeyCipher;
    }

    public void setPrikeyCipher(String prikeyCipher) {
        this.prikeyCipher = prikeyCipher;
    }

    public ApipClient getApipClient() {
        return apipClient;
    }

    public void setApipClient(ApipClient apipClient) {
        this.apipClient = apipClient;
    }
}
