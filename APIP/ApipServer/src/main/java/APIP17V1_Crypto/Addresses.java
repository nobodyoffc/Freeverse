package APIP17V1_Crypto;

import server.ApipApiNames;
import constants.FieldNames;
import crypto.KeyTools;
import fcData.ReplyBody;
import initial.Initiator;
import server.HttpRequestChecker;
import utils.http.AuthType;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import appTools.Settings;

@WebServlet(name = ApipApiNames.ADDRESSES, value = "/"+ ApipApiNames.SN_17+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.ADDRESSES)
public class Addresses extends HttpServlet {
    private final Settings settings = Initiator.settings;
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        AuthType authType = AuthType.FC_SIGN_URL;
        doRequest(request, response, authType,settings);
    }
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doRequest(request, response, authType,settings);
    }

    protected void doRequest(HttpServletRequest request, HttpServletResponse response, AuthType authType, Settings settings) {
        ReplyBody replier = new ReplyBody(settings);
        HttpRequestChecker httpRequestChecker = new HttpRequestChecker(settings, replier);
        //Do FCDSL other request
        Map<String, String> other = httpRequestChecker.checkOtherRequestHttp(request, response, authType);
        if (other == null) return;
        //Do this request

        String addrOrPubKey = other.get(FieldNames.ADDR_OR_PUB_KEY);

        Map<String, String> addrMap;
        String pubKey;

        if(addrOrPubKey.startsWith("F")||addrOrPubKey.startsWith("1")||addrOrPubKey.startsWith("D")||addrOrPubKey.startsWith("L")){
            byte[] hash160 = KeyTools.addrToHash160(addrOrPubKey);
            addrMap = KeyTools.hash160ToAddresses(hash160);
        }else if (addrOrPubKey.startsWith("02")||addrOrPubKey.startsWith("03")){
            pubKey = addrOrPubKey;
            addrMap= KeyTools.pubKeyToAddresses(pubKey);
        }else if(addrOrPubKey.startsWith("04")){
            try {
                pubKey = KeyTools.compressPk65To33(addrOrPubKey);
                addrMap= KeyTools.pubKeyToAddresses(pubKey);
            } catch (Exception e) {
                replier.replyOtherErrorHttp("Wrong public key.", response);
                return;
            }
        }else{
            replier.replyOtherErrorHttp("FID or Public Key are needed.", response);
            return;
        }
        replier.replySingleDataSuccessHttp(addrMap,response);
    
    }
}