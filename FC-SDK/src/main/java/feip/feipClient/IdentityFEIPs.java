package feip.feipClient;

import clients.apipClient.ApipClient;
import fch.TxCreator;
import constants.Constants;
import constants.UpStrings;
import crypto.old.EccAes256K1P7;
import feip.feipData.Feip;
import feip.feipData.MasterData;
import fcData.AlgorithmId;
import javaTools.JsonTools;
import crypto.KeyTools;
import fch.fchData.SendTo;
import javaTools.http.AuthType;
import javaTools.http.HttpRequestMethod;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

import static constants.Constants.Dust;

public class IdentityFEIPs {
    public static String promise = "The master owns all my rights.";

    public static String setMaster(byte[] priKey, String masterPubKey) {
        Feip feip = new Feip(Constants.FEIP, "6", "6", UpStrings.MASTER);

        MasterData masterData = new MasterData();
        masterData.setMaster(KeyTools.pubKeyToFchAddr(masterPubKey));
        masterData.setAlg(AlgorithmId.EccAes256K1P7_No1_NrC7.getName());
        byte[] priKeyCipher = new EccAes256K1P7().encryptAsyOneWayBundle(priKey.clone(), HexFormat.of().parseHex(masterPubKey));
        masterData.setCipherPriKey(Base64.getEncoder().encodeToString(priKeyCipher));
        masterData.setPromise(promise);

        feip.setData(masterData);

        return JsonTools.toJson(feip);
    }

    public static String setMaster(String priKeyCipher, String ownerOrItsPubKey, ApipClient apipClient) {

        String ownerPubKey;
        if (KeyTools.isValidFchAddr(ownerOrItsPubKey)) {
            ownerPubKey = apipClient.getPubKey(ownerOrItsPubKey,HttpRequestMethod.POST, AuthType.FC_SIGN_BODY);
        } else if (KeyTools.isValidPubKey(ownerOrItsPubKey)) {
            ownerPubKey = ownerOrItsPubKey;
        } else return null;

        byte[] priKey = EccAes256K1P7.decryptJsonBytes(priKeyCipher,apipClient.getSymKey());
        if (priKey == null) return null;
        String masterJson = setMaster(priKey, ownerPubKey);
        SendTo sendTo = new SendTo();
        sendTo.setFid(ownerOrItsPubKey);
        sendTo.setAmount(Dust);
        List<SendTo> sendToList = new ArrayList<>();
        sendToList.add(sendTo);
        return TxCreator.sendTxForMsgByAPIP(apipClient.getApiAccount(), apipClient.getSymKey(), priKey, sendToList, masterJson);
    }
}
