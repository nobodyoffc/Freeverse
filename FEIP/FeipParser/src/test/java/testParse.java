import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import fchClass.OpReturn;
import feipClass.FcInfo;
import fipaClass.Op;
import javaTools.JsonTools;

public class testParse {

    public static void main(String[] args) {
        OpReturn opReturn = new OpReturn();
        opReturn.setOpReturn("{\"data\":{\"name\":\"chunhua\",\"op\":\"register\"},\"hash\":\"\",\"name\":\"CID\",\"sn\":3,\"type\":\"FEIP\",\"ver\":4}");
        FcInfo feip = parseFeip(opReturn);
        System.out.println(feip.getName());
    }
    public static FcInfo parseFeip(OpReturn opre) {

        if(opre.getOpReturn()==null)return null;

        String protStr = JsonTools.strToJson(opre.getOpReturn());

        FcInfo feip = null;
        try {
            feip = new Gson().fromJson(protStr, FcInfo.class);
        }catch(JsonSyntaxException e) {
//            log.debug("Invalid opReturn content on {}. ",opre.getTxId());
        }
        return  feip;
    }
}
