package feip;

import fch.fchData.OpReturn;
import feip.feipData.CidData;
import feip.feipData.DataOnChain;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import feip.feipData.MasterData;
import javaTools.JsonTools;
import org.slf4j.Logger;

import static constants.Constants.*;
import static constants.OpNames.REGISTER;
import static constants.OpNames.UNREGISTER;

public class FeipTools {

    public static DataOnChain parseFeip(OpReturn opre, Logger log) {

        if(opre.getOpReturn()==null)return null;

        DataOnChain feip = null;
        try {
            String json = JsonTools.strToJson(opre.getOpReturn());
            feip = new Gson().fromJson(json, DataOnChain.class);
        }catch(JsonSyntaxException e) {
            log.debug("Bad json on {}. ",opre.getTxId());
        }
        return  feip;
    }

    public static boolean isGoodCidName(String cid) {
        if(cid==null
                ||cid.equals("")
                ||cid.contains(" ")
                ||cid.contains("@")
                ||cid.contains("/")
        )return false;
        return true;
    }
    public static String getCidRegisterData(String name) {
        return getCidData(name,REGISTER);
    }

    public static String getMasterData(String master, String masterPubKey, String priKeyCipher) {
        DataOnChain data = new DataOnChain();
        data.setType(FEIP);
        data.setSn(String.valueOf(6));
        data.setName(Master);
        data.setVer(String.valueOf(6));

        MasterData masterData = new MasterData();
        masterData.setMaster(master);
        masterData.setPromise(MasterData.PROMISE);
        masterData.setCipherPriKey(priKeyCipher);

        data.setData(masterData);

        return JsonTools.toJson(data);
    }
    public static String getCidUnregisterData() {
        return getCidData(null,UNREGISTER);
    }

    public static String getCidData(String name,String op) {
        DataOnChain data = new DataOnChain();
        data.setType(FEIP);
        data.setSn(String.valueOf(3));
        data.setName(CID);
        data.setVer(String.valueOf(4));

        CidData cidData = new CidData();
        cidData.setOp(op);
        if(name!=null)cidData.setName(name);

        data.setData(cidData);
        return JsonTools.toJson(data);
    }
}
