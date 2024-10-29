package clients.apipClient;

import apip.apipData.Fcdsl;
import constants.ApiNames;

import javax.annotation.Nullable;

public class ConstructAPIs {

    public static ApipClientEvent protocolByIdsPost(String urlHead, String[] ids, @Nullable String via, byte[] sessionKey) {
        ApipClientEvent apipClientData = new ApipClientEvent();
        
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIds(ids);

        String urlTail = ApiNames.APIP4V1Path + ApiNames.ProtocolByIdsAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientEvent protocolSearchPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientEvent apipClientData = new ApipClientEvent();
        

        String urlTail = ApiNames.APIP4V1Path + ApiNames.ProtocolSearchAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientEvent protocolOpHistoryPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientEvent apipClientData = new ApipClientEvent();
        

        String urlTail = ApiNames.APIP4V1Path + ApiNames.ProtocolOpHistoryAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientEvent protocolRateHistoryPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientEvent apipClientData = new ApipClientEvent();
        
        String urlTail = ApiNames.APIP4V1Path + ApiNames.ProtocolRateHistoryAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientEvent codeByIdsPost(String urlHead, String[] ids, @Nullable String via, byte[] sessionKey) {
        ApipClientEvent apipClientData = new ApipClientEvent();
        
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIds(ids);

        String urlTail = ApiNames.APIP5V1Path + ApiNames.CodeByIdsAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientEvent codeSearchPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientEvent apipClientData = new ApipClientEvent();
        

        String urlTail = ApiNames.APIP5V1Path + ApiNames.CodeSearchAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientEvent codeOpHistoryPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientEvent apipClientData = new ApipClientEvent();
        

        String urlTail = ApiNames.APIP5V1Path + ApiNames.CodeOpHistoryAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientEvent codeRateHistoryPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientEvent apipClientData = new ApipClientEvent();
        
        String urlTail = ApiNames.APIP5V1Path + ApiNames.CodeRateHistoryAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }


    public static ApipClientEvent serviceByIdsPost(String urlHead, String[] ids, @Nullable String via, byte[] sessionKey) {
        ApipClientEvent apipClientEvent = new ApipClientEvent();
        
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIds(ids);

        String urlTail = ApiNames.APIP6V1Path + ApiNames.ServiceByIdsAPI;

        boolean isGood = apipClientEvent.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientEvent;
    }

    public static ApipClientEvent serviceSearchPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientEvent apipClientData = new ApipClientEvent();

        String urlTail = ApiNames.APIP6V1Path + ApiNames.ServiceSearchAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientEvent serviceOpHistoryPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientEvent apipClientData = new ApipClientEvent();
        

        String urlTail = ApiNames.APIP6V1Path + ApiNames.ServiceOpHistoryAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientEvent serviceRateHistoryPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientEvent apipClientData = new ApipClientEvent();
        
        String urlTail = ApiNames.APIP6V1Path + ApiNames.ServiceRateHistoryAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }


    public static ApipClientEvent appByIdsPost(String urlHead, String[] ids, @Nullable String via, byte[] sessionKey) {
        ApipClientEvent apipClientData = new ApipClientEvent();
        
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIds(ids);

        String urlTail = ApiNames.APIP7V1Path + ApiNames.AppByIdsAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientEvent appSearchPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientEvent apipClientData = new ApipClientEvent();
        

        String urlTail = ApiNames.APIP7V1Path + ApiNames.AppSearchAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientEvent appOpHistoryPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientEvent apipClientData = new ApipClientEvent();
        

        String urlTail = ApiNames.APIP7V1Path + ApiNames.AppOpHistoryAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientEvent appRateHistoryPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientEvent apipClientData = new ApipClientEvent();
        
        String urlTail = ApiNames.APIP7V1Path + ApiNames.AppRateHistoryAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientEvent getApps(String urlHead, String id) {
        ApipClientEvent apipClientData = new ApipClientEvent();
        if (id == null) apipClientData.addNewApipUrl(urlHead, ApiNames.FreeGetPath + ApiNames.GetAppsAPI);
        else apipClientData.addNewApipUrl(urlHead, ApiNames.FreeGetPath + ApiNames.GetAppsAPI + "?id=" + id);
        apipClientData.get();
        return apipClientData;
    }

    public static ApipClientEvent getServices(String urlHead, String id) {
        ApipClientEvent apipClientData = new ApipClientEvent();
        if (id == null) apipClientData.addNewApipUrl(urlHead, ApiNames.FreeGetPath + ApiNames.GetServicesAPI);
        else apipClientData.addNewApipUrl(urlHead, ApiNames.FreeGetPath + ApiNames.GetServicesAPI + "?id=" + id);
        apipClientData.get();
        return apipClientData;
    }
}
