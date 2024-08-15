package clients.apipClient;

import apip.apipData.Fcdsl;
import constants.ApiNames;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static javaTools.StringTools.listToString;
import static javaTools.http.AuthType.FC_SIGN_BODY;
import static constants.FieldNames.*;
import static javaTools.StringTools.arrayToString;

public class SwapHallAPIs {

    public static ApipClientEvent swapRegisterPost(String urlHead, String sid, @Nullable String via, byte[] sessionKey) {
        Fcdsl fcdsl = new Fcdsl();
        Map<String, String> dataMap = new HashMap<>();
        dataMap.put(SID, sid);
        fcdsl.setOther(dataMap);
        ApipClientEvent apipClientData = new ApipClientEvent(sessionKey,urlHead,ApiNames.SwapHallPath,ApiNames.SwapRegisterAPI, fcdsl, FC_SIGN_BODY, via);
        apipClientData.post(sessionKey);
        return apipClientData;
    }

    public static ApipClientEvent swapUpdatePost(String urlHead, Map<String, Object> uploadMap, @Nullable String via, byte[] sessionKey) {
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setOther(uploadMap);
        ApipClientEvent apipClientData = new ApipClientEvent(sessionKey,urlHead,ApiNames.SwapHallPath,ApiNames.SwapUpdateAPI, fcdsl,  FC_SIGN_BODY, via);
        apipClientData.post(sessionKey);
        return apipClientData;
    }

    public static ApipClientEvent getSwapInfo(String urlHead, @Nullable String[] sid, @Nullable List<String> last) {

        Map<String,String>paramMap = new HashMap<>();
        if (sid != null) {
            String sidStr = arrayToString(sid);
            paramMap.put(SID,sidStr);
        } else if (last != null) {
            String lastStr = listToString(last);
            paramMap.put(LAST,lastStr);
        }

        ApipClientEvent apipClientData=new ApipClientEvent(urlHead, ApiNames.SwapHallPath,ApiNames.SwapInfoAPI);
        if(!paramMap.isEmpty()) apipClientData=new ApipClientEvent(urlHead, ApiNames.SwapHallPath,ApiNames.SwapInfoAPI,paramMap);

        apipClientData.get();
        return apipClientData;
    }

    public static ApipClientEvent getSwapState(String urlHead, @NotNull String sid) {
        Map<String,String>paramMap = new HashMap<>();
        paramMap.put(SID,sid);
        ApipClientEvent apipClientData=new ApipClientEvent(urlHead, ApiNames.SwapHallPath,ApiNames.SwapStateAPI,paramMap);
        apipClientData.get();
        return apipClientData;
    }

    public static ApipClientEvent getSwapLp(String urlHead, @NotNull String sid) {
        Map<String,String>paramMap = new HashMap<>();
        paramMap.put(SID,sid);
        ApipClientEvent apipClientData=new ApipClientEvent(urlHead, ApiNames.SwapHallPath,ApiNames.SwapLpAPI,paramMap);
        apipClientData.get();
        return apipClientData;
    }

    public static ApipClientEvent getSwapFinished(String urlHead, @Nullable  String sid, @Nullable String[] last) {

        Map<String,String>paramMap = new HashMap<>();
        if (sid != null) {
            paramMap.put(SID,sid);
        } else if (last != null) {
            String lastStr = arrayToString(last);
            paramMap.put(LAST,lastStr);
        }

        ApipClientEvent apipClientData=new ApipClientEvent(urlHead, ApiNames.SwapHallPath,ApiNames.SwapFinishedAPI);
        if(!paramMap.isEmpty()) apipClientData=new ApipClientEvent(urlHead, ApiNames.SwapHallPath,ApiNames.SwapFinishedAPI,paramMap);

        apipClientData.get();
        return apipClientData;
    }

    public static ApipClientEvent getSwapPending(String urlHead, @NotNull String sid) {
        Map<String,String>paramMap = new HashMap<>();
        paramMap.put(SID,sid);
        ApipClientEvent apipClientData=new ApipClientEvent(urlHead, ApiNames.SwapHallPath,ApiNames.SwapPendingAPI,paramMap);

        apipClientData.get();
        return apipClientData;
    }

    public static ApipClientEvent getSwapPrice(String urlHead, String sid, String gTick, String mTick, List<String> last) {

        Map<String,String>paramMap = new HashMap<>();

//        StringBuilder urlTailBuilder = new StringBuilder();
//        urlTailBuilder.append(ApiNames.SwapHallPath + ApiNames.SwapInfoAPI);

        if (sid != null) {
            paramMap.put(SID,sid);
        } else {
            if (gTick != null)
                paramMap.put(G_TICK,gTick);
            if (mTick != null) {
                paramMap.put(M_TICK,mTick);
            }
        }
        if (last != null) {
            String lastStr = listToString(last);
            paramMap.put(LAST,lastStr);
        }
        ApipClientEvent apipClientData=new ApipClientEvent(urlHead, ApiNames.SwapHallPath,ApiNames.SwapPendingAPI,paramMap);

        apipClientData.get();
        return apipClientData;
    }

}
