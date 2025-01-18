package clients.apipClient;

import apip.apipData.Fcdsl;
import constants.ApiNames;

import javax.annotation.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static constants.Strings.HEIGHT;

public class BlockchainAPIs {

    public static ApipClientEvent blockByIdsPost(String urlHead, String[] ids, @Nullable String via, byte[] sessionKey) {
        ApipClientEvent apipClientData = new ApipClientEvent();
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIds(List.of(ids));
        apipClientData.setFcdsl(fcdsl);

        String urlTail = ApiNames.APIP2V1Path + ApiNames.BlockByIdsAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientEvent blockByHeightPost(String urlHead, String[] heights, @Nullable String via, byte[] sessionKey) {
        ApipClientEvent apipClientData = new ApipClientEvent();

        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(HEIGHT).addNewValues(heights);
        apipClientData.setFcdsl(fcdsl);

        String urlTail = ApiNames.APIP2V1Path + ApiNames.BlockByHeightsAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientEvent blockSearchPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientEvent apipClientData = new ApipClientEvent();

        apipClientData.setFcdsl(fcdsl);

        String urlTail = ApiNames.APIP2V1Path + ApiNames.BlockSearchAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientEvent cashValidPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientEvent apipClientData = new ApipClientEvent();

        apipClientData.setFcdsl(fcdsl);

        String urlTail = ApiNames.APIP2V1Path + ApiNames.CashValidAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientEvent cashByIdsPost(String urlHead, String[] ids, @Nullable String via, byte[] sessionKey) {
        ApipClientEvent apipClientData = new ApipClientEvent();

        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIds(List.of(ids));
        apipClientData.setFcdsl(fcdsl);

        String urlTail = ApiNames.APIP2V1Path + ApiNames.CashByIdsAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientEvent cashSearchPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientEvent apipClientData = new ApipClientEvent();

        apipClientData.setFcdsl(fcdsl);

        String urlTail = ApiNames.APIP2V1Path + ApiNames.CashSearchAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientEvent fidByIdsPost(String urlHead, String[] ids, @Nullable String via, byte[] sessionKey) {
        ApipClientEvent apipClientData = new ApipClientEvent();

        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIds(List.of(ids));
        apipClientData.setFcdsl(fcdsl);

        String urlTail = ApiNames.APIP2V1Path + ApiNames.FidByIdsAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientEvent fidSearchPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientEvent apipClientData = new ApipClientEvent();
        
        apipClientData.setFcdsl(fcdsl);

        String urlTail = ApiNames.APIP2V1Path + ApiNames.FidSearchAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientEvent opReturnByIdsPost(String urlHead, String[] ids, @Nullable String via, byte[] sessionKey) {
        ApipClientEvent apipClientData = new ApipClientEvent();
        
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIds(List.of(ids));
        apipClientData.setFcdsl(fcdsl);

        String urlTail = ApiNames.APIP2V1Path + ApiNames.OpReturnByIdsAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientEvent opReturnSearchPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientEvent apipClientData = new ApipClientEvent();
        
        apipClientData.setFcdsl(fcdsl);

        String urlTail = ApiNames.APIP2V1Path + ApiNames.OpReturnSearchAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientEvent p2shByIdsPost(String urlHead, String[] ids, @Nullable String via, byte[] sessionKey) {
        ApipClientEvent apipClientData = new ApipClientEvent();
        
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIds(List.of(ids));
        apipClientData.setFcdsl(fcdsl);

        String urlTail = ApiNames.APIP2V1Path + ApiNames.P2shByIdsAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientEvent p2shSearchPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientEvent apipClientData = new ApipClientEvent();
        
        apipClientData.setFcdsl(fcdsl);

        String urlTail = ApiNames.APIP2V1Path + ApiNames.P2shSearchAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientEvent txByIdsPost(String urlHead, String[] ids, @Nullable String via, byte[] sessionKey) {
        ApipClientEvent apipClientData = new ApipClientEvent();
        
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIds(List.of(ids));
        apipClientData.setFcdsl(fcdsl);

        String urlTail = ApiNames.APIP2V1Path + ApiNames.TxByIdsAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientEvent txSearchPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientEvent apipClientData = new ApipClientEvent();
        

        String urlTail = ApiNames.APIP2V1Path + ApiNames.TxSearchAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static Fcdsl txByFidQuery(String fid, @Nullable List<String> last) {
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery()
                .addNewTerms()
                .addNewFields("inMarks.fid", "outMarks.fid")
                .addNewValues(fid);
        if (last != null) {
            fcdsl.addAfter(last);
        }
        return fcdsl;
    }

    public static ApipClientEvent getUtxo(String urlHead, String id, double amount) {
        Map<String,String> paramMap = new HashMap<>();
        paramMap.put("address",id);
        paramMap.put("amount", String.valueOf(amount));
        ApipClientEvent apipClientData = new ApipClientEvent(urlHead, ApiNames.APIP2V1Path,ApiNames.GetUtxoAPI);
        apipClientData.get();
        return apipClientData;
    }

    public static ApipClientEvent chainInfo(String urlHead) {
        ApipClientEvent apipClientData = new ApipClientEvent(urlHead, ApiNames.APIP2V1Path,ApiNames.ChainInfoAPI);
        apipClientData.get();
        return apipClientData;
    }
}
