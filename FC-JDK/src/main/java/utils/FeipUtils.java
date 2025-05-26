package utils;

import data.fchData.OpReturn;
import data.feipData.CidOpData;
import data.feipData.Feip;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import data.feipData.MasterOpData;
import org.slf4j.Logger;

import static constants.Constants.*;
import static constants.OpNames.REGISTER;
import static constants.OpNames.UNREGISTER;

public class FeipUtils {

    public static Feip parseFeip(OpReturn opre, Logger log) {

        if(opre.getOpReturn()==null)return null;

        Feip feip = null;
        try {
            String json = JsonUtils.strToJson(opre.getOpReturn());
            feip = new Gson().fromJson(json, Feip.class);
        }catch(JsonSyntaxException e) {
            log.debug("Bad json on {}. ",opre.getId());
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
        Feip data = new Feip();
        data.setType(FEIP);
        data.setSn(String.valueOf(6));
        data.setName(Master);
        data.setVer(String.valueOf(6));

        MasterOpData masterOpData = new MasterOpData();
        masterOpData.setMaster(master);
        masterOpData.setPromise(MasterOpData.PROMISE);
        masterOpData.setCipherPriKey(priKeyCipher);

        data.setData(masterOpData);

        return JsonUtils.toJson(data);
    }
    public static String getCidUnregisterData() {
        return getCidData(null,UNREGISTER);
    }

    public static String getCidData(String name,String op) {
        Feip data = new Feip();
        data.setType(FEIP);
        data.setSn(String.valueOf(3));
        data.setName(CID);
        data.setVer(String.valueOf(4));

        CidOpData cidOpData = new CidOpData();
        cidOpData.setOp(op);
        if(name!=null) cidOpData.setName(name);

        data.setData(cidOpData);
        return JsonUtils.toJson(data);
    }
}
