package apip;

import clients.apipClient.ApipClientEvent;
import feip.feipData.serviceParams.ApipParams;


import feip.feipData.Service;

import crypto.Hash;
import javaTools.BytesTools;
import javaTools.Hex;
import javaTools.ObjectTools;
import org.jetbrains.annotations.Nullable;
import redis.clients.jedis.Jedis;


import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Map;

import static constants.ApiNames.apiList;
import static constants.ApiNames.freeApiList;
import static constants.Strings.*;

public class ApipTools {


    public static String getApiNameFromUrl(String url) {
        int lastSlashIndex = url.lastIndexOf('/');
        if (lastSlashIndex != -1 && lastSlashIndex != url.length() - 1) {
            String name = url.substring(lastSlashIndex + 1);
            if (apiList.contains(name) || freeApiList.contains(name)) {
                return name;
            }
            return "";
        } else {
            return "";  // Return empty string if '/' is the last character or not found
        }

    }

    public static int getNPrice(String apiName, Jedis jedis) {
        try {
            return Integer.parseInt(jedis.hget(N_PRICE, apiName));
        } catch (Exception e) {
            return -1;
        }
    }

    public static boolean isGoodSign(String requestBody, String sign, String symKey) {
        byte[] requestBodyBytes = requestBody.getBytes(StandardCharsets.UTF_8);
        return isGoodSign(requestBodyBytes, sign, HexFormat.of().parseHex(symKey));
    }

    public static boolean isGoodSign(byte[] bytes, String sign, byte[] symKey) {
        if (sign == null || bytes == null) return false;
        byte[] signBytes = BytesTools.bytesMerger(bytes, symKey);
        byte[] hash = Hash.sha256x2(signBytes);
        String doubleSha256Hash = Hex.toHex(hash);

        return (sign.equals(doubleSha256Hash));
    }

    public static String getSessionName(byte[] sessionKey) {
        if (sessionKey == null) return null;
        return HexFormat.of().formatHex(Arrays.copyOf(sessionKey, 6));
    }

    @Nullable
    public static Map<String, Service> parseApipServiceMap(ApipClientEvent apipClientData) {
        if(apipClientData.checkResponse()!=0) {
            System.out.println("Failed to buy APIP service. Code:"+ apipClientData.getCode()+", Message:"+ apipClientData.getMessage());
            return null;
        }

        try {
            Map<String, Service> serviceMap = ObjectTools.objectToMap(apipClientData.getResponseBody().getData(),String.class,Service.class);//DataGetter.getServiceMap();
            if(serviceMap==null) return null;
            for(String sid :serviceMap.keySet()) {
                Service service = serviceMap.get(sid);
                ApipParams apipParams = ApipParams.fromObject(service.getParams());
                service.setParams(apipParams);
            }
            return serviceMap;
        } catch (Exception e) {
            System.out.println("Failed to get APIP service.");
            e.printStackTrace();
            return null;
        }
    }
}
