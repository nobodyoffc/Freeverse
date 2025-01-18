package clients.apipClient;

import constants.ApiNames;
import constants.ReplyCodeMessage;

public class FreeGetAPIs {

    public static ApipClientEvent broadcast(String urlHead, String rawTx) {
        ApipClientEvent apipClientData = new ApipClientEvent();
        apipClientData.addNewApipUrl(urlHead, ApiNames.FreeGetPath + ApiNames.BroadcastAPI + "?rawTx=" + rawTx);
        apipClientData.get();
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

    public static ApipClientEvent getAvatar(String urlHead, String fid) {
        ApipClientEvent apipClientData = new ApipClientEvent();
        apipClientData.addNewApipUrl(urlHead, ApiNames.FreeGetPath + ApiNames.GetAvatarAPI + "?fid=" + fid);
        apipClientData.get();
        return apipClientData;
    }

    public static ApipClientEvent getCashes(String urlHead, String id, double amount) {
        ApipClientEvent apipClientData = new ApipClientEvent();
        String urlTail = ApiNames.FreeGetPath + ApiNames.GetCashesAPI;
        if (id != null) urlTail = urlTail + "?fid=" + id;
        if (amount != 0) {
            if(id==null){
                apipClientData.setCode(ReplyCodeMessage.Code1021FidIsRequired);
                apipClientData.setMessage(ReplyCodeMessage.Msg1021FidIsRequired);
                return apipClientData;
            }
            urlTail = urlTail + "&amount=" + amount;
        }
        apipClientData.addNewApipUrl(urlHead, urlTail);
        apipClientData.get();

        return apipClientData;
    }

    public static ApipClientEvent getFidCid(String urlHead, String id) {
        ApipClientEvent apipClientData = new ApipClientEvent();
        apipClientData.addNewApipUrl(urlHead, ApiNames.FreeGetPath + ApiNames.GetFidCidAPI + "?id=" + id);
        apipClientData.get();
        return apipClientData;
    }

    public static ApipClientEvent getFreeService(String urlHead) {
        ApipClientEvent apipClientData = new ApipClientEvent();
        apipClientData.addNewApipUrl(urlHead, ApiNames.FreeGetPath + ApiNames.GetFreeServiceAPI);
        apipClientData.get();
        return apipClientData;
    }

    public static ApipClientEvent getTotals(String urlHead) {
        ApipClientEvent apipClientData = new ApipClientEvent();
        apipClientData.addNewApipUrl(urlHead, ApiNames.FreeGetPath + ApiNames.GetTotalsAPI);
        apipClientData.get();
        return apipClientData;
    }
}
