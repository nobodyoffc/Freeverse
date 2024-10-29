package clients.apipClient;

import apip.apipData.Fcdsl;
import apip.apipData.WebhookRequestBody;
import clients.FcClientEvent;
import constants.ApiNames;

public class WebhookAPIs {
    public static FcClientEvent newCashList(String urlHead, String via, WebhookRequestBody webhookRequestBody, byte[] sessionKey) {
        ApipClientEvent apipClientEvent = new ApipClientEvent();
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setOther(webhookRequestBody);
        apipClientEvent.setFcdsl(fcdsl);

        String urlTail = ApiNames.APIP20V1Path + ApiNames.NewCashByFidsAPI;

        boolean isGood = apipClientEvent.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientEvent;
    }
}
