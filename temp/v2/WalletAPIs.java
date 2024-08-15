package clients.apipClient;

import apip.apipData.Fcdsl;
import constants.ApiNames;
import constants.FieldNames;
import javaTools.NumberTools;


import javax.annotation.Nullable;
import java.util.List;

public class WalletAPIs {


    public static ApipClientEvent feeRateGet(String urlHead){
        ApipClientEvent apipClient = new ApipClientEvent();
        String urlTail = ApiNames.APIP18V1Path + ApiNames.FeeRateAPI;
        apipClient.addNewApipUrl(urlHead, urlTail);
        apipClient.get();
        return apipClient;
    }

    public static ApipClientEvent feeRatePost(String urlHead, @Nullable String via, byte[] sessionKey){
        ApipClientEvent apipClient = new ApipClientEvent();
        String urlTail = ApiNames.APIP18V1Path + ApiNames.FeeRateAPI;
        boolean isGood = apipClient.post(urlHead,urlTail, null, via, sessionKey);
        if(!isGood)return null;

        return apipClient;
    }

    public static ApipClientEvent broadcastTxPost(String urlHead, String txHex, @Nullable String via, byte[] sessionKey) {
        ApipClientEvent apipClientData = new ApipClientEvent();
        
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setOther(txHex);

        String urlTail = ApiNames.APIP18V1Path + ApiNames.BroadcastTxAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientEvent decodeRawTxPost(String urlHead, String rawTx, @Nullable String via, byte[] sessionKey) {
        ApipClientEvent apipClientData = new ApipClientEvent();
        
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setOther(rawTx);

        String urlTail = ApiNames.APIP18V1Path + ApiNames.DecodeRawTxAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientEvent cashValidForPayPost(String urlHead, String fid, double amount, @Nullable String via, byte[] sessionKey) {
        ApipClientEvent apipClientData = new ApipClientEvent();
        
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(FieldNames.OWNER).addNewValues(fid);
        amount = NumberTools.roundDouble8(amount);
        fcdsl.setOther(String.valueOf(amount));
        String urlTail = ApiNames.APIP18V1Path + ApiNames.CashValidForPayAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientEvent cashValidForCdPost(String urlHead, String fid, int cd, @Nullable String via, byte[] sessionKey) {
        ApipClientEvent apipClientData = new ApipClientEvent();
        
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(FieldNames.OWNER).addNewValues(fid);
        fcdsl.setOther(String.valueOf(cd));
        String urlTail = ApiNames.APIP18V1Path + ApiNames.CashValidForCdAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientEvent unconfirmedPost(String urlHead, String[] ids, @Nullable String via, byte[] sessionKey) {
        ApipClientEvent apipClientData = new ApipClientEvent();
        
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIds(List.of(ids));

        String urlTail = ApiNames.APIP18V1Path + ApiNames.UnconfirmedAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }
}
