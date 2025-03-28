package APIP17V1_Crypto;

import server.ApipApiNames;
import constants.FieldNames;
import crypto.KeyTools;
import fcData.ReplyBody;
import fcData.Signature;
import initial.Initiator;
import server.HttpRequestChecker;
import utils.http.AuthType;
import org.bitcoinj.core.ECKey;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.security.SignatureException;
import java.util.Map;

import appTools.Settings;

@WebServlet(name = ApipApiNames.VERIFY, value = "/"+ ApipApiNames.SN_17+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.VERIFY)
public class Verify extends HttpServlet {
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
        //Do FCDSL other request
        HttpRequestChecker httpRequestChecker = new HttpRequestChecker(settings, replier);
        Map<String,String> other = httpRequestChecker.checkOtherRequestHttp(request, response, authType);
        if (other == null) return;
        //Do this request
        String rawSignJson = other.get(FieldNames.SIGN);
        Signature signature;
        try {
            signature = Signature.parseSignature(rawSignJson);
        }catch (Exception e){
            replier.replyOtherErrorHttp("Wrong signature format.", response);
            return;
        }

        if(signature==null){
            replier.replyOtherErrorHttp("Failed to parse signature.", response);
            return;
        }

        boolean isGoodSign;
        if(signature.getFid()!=null&& signature.getMsg()!=null && signature.getSign()!=null){
            String sign = signature.getSign().replace("\\u003d", "=");
            try {
                String signPubKey = ECKey.signedMessageToKey(signature.getMsg(), sign).getPublicKeyAsHex();
                isGoodSign= signature.getFid().equals(KeyTools.pubKeyToFchAddr(signPubKey));
            } catch (SignatureException e) {
                isGoodSign = false;
            }
        }else{
            replier.replyOtherErrorHttp("FID, signature or message missed.", response);
            return;
        }
        replier.replySingleDataSuccessHttp(isGoodSign,response);
    }
}
