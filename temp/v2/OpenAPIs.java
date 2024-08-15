package clients.apipClient;

import apip.apipData.Fcdsl;
import apip.apipData.RequestBody;
import constants.ApiNames;

import javax.annotation.Nullable;
import java.io.IOException;

public class OpenAPIs {
    public static ApipClientEvent getService(String urlHead) {
        ApipClientEvent apipClientData = new ApipClientEvent();
        apipClientData.addNewApipUrl(urlHead, ApiNames.APIP0V1Path + ApiNames.GetServiceAPI);
        apipClientData.get();
        return apipClientData;
    }

    public static ApipClientEvent signInPost(String urlHead, String via, byte[] priKey, RequestBody.SignInMode mode) {
        ApipClientEvent apipClientData = new ApipClientEvent();
        
        String urlTail = ApiNames.APIP0V1Path + ApiNames.SignInAPI;
        doSignIn(apipClientData, urlHead, via, priKey, urlTail, mode);

        return apipClientData;
    }

    public static ApipClientEvent signInEccPost(String urlHead, @Nullable String via, byte[] priKey, @Nullable RequestBody.SignInMode modeNullOrRefresh) {
        ApipClientEvent apipClientData = new ApipClientEvent();
        
        String urlTail = ApiNames.APIP0V1Path + ApiNames.SignInEccAPI;
        doSignIn(apipClientData, urlHead, via, priKey, urlTail, modeNullOrRefresh);

        return apipClientData;
    }

    @Nullable
    private static ApipClientEvent doSignIn(ApipClientEvent apipClientData, String urlHead, @Nullable String via, byte[] priKey, String urlTail, @Nullable RequestBody.SignInMode mode) {

        try {
            apipClientData.asySignPost(urlHead, urlTail, via, null, priKey, mode);
        } catch (IOException e) {
            System.out.println("Do post wrong.");
            return null;
        }

        return apipClientData;
    }

    public static ApipClientEvent totalsGet(String urlHead) {
        ApipClientEvent apipClientData = new ApipClientEvent();
        apipClientData.addNewApipUrl(urlHead, ApiNames.FreeGetPath + ApiNames.GetTotalsAPI);
        apipClientData.get();
        return apipClientData;
    }

    public static ApipClientEvent totalsPost(String urlHead, @Nullable String via, byte[] sessionKey) {
        ApipClientEvent apipClientData = new ApipClientEvent();
        
        String urlTail = ApiNames.APIP0V1Path + ApiNames.TotalsAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, apipClientData.getFcdsl(), via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientEvent generalPost(String index, String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientEvent apipClientData = new ApipClientEvent();
        if (index == null) {
            System.out.println("The index name is required.");
            return null;
        }
        if (fcdsl == null) fcdsl = new Fcdsl();

        fcdsl.setIndex(index);

        String urlTail = ApiNames.APIP1V1Path + ApiNames.GeneralAPI;
        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;

        return apipClientData;
    }
}