package APIP17V1_Crypto;

import server.ApipApiNames;
import constants.FieldNames;
import core.crypto.KeyTools;
import data.fcData.ReplyBody;
import initial.Initiator;
import server.HttpRequestChecker;
import utils.http.AuthType;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import config.Settings;

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

        String addrOrPubkey = other.get(FieldNames.ADDR_OR_PUB_KEY);

        Map<String, String> addrMap;
        String pubkey;

        if(addrOrPubkey.startsWith("F")||addrOrPubkey.startsWith("1")||addrOrPubkey.startsWith("D")||addrOrPubkey.startsWith("L")){
            byte[] hash160 = KeyTools.addrToHash160(addrOrPubkey);
            addrMap = KeyTools.hash160ToAddresses(hash160);
        }else if (addrOrPubkey.startsWith("02")||addrOrPubkey.startsWith("03")){
            pubkey = addrOrPubkey;
            addrMap= KeyTools.pubkeyToAddresses(pubkey);
        }else if(addrOrPubkey.startsWith("04")){
            try {
                pubkey = KeyTools.compressPk65To33(addrOrPubkey);
                addrMap= KeyTools.pubkeyToAddresses(pubkey);
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