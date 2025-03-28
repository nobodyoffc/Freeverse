package APIP17V1_Crypto;

import server.ApipApiNames;
import constants.FieldNames;
import crypto.Hash;
import fcData.ReplyBody;
import initial.Initiator;
import server.HttpRequestChecker;
import utils.Hex;
import utils.http.AuthType;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

import appTools.Settings;

@WebServlet(name = ApipApiNames.SHA_256_HEX, value = "/"+ ApipApiNames.SN_17+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.SHA_256_HEX)
public class Sha256Hex extends HttpServlet {
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
        Map<String, String> other = httpRequestChecker.checkOtherRequestHttp(request, response, authType);
        if (other == null) return;
        //Do this request

        String text = other.get(FieldNames.MESSAGE);
        if(text==null){
            replier.replyOtherErrorHttp("The hex missed.", response);
            return;
        }
        if (!Hex.isHex32(text)) {
            replier.replyOtherErrorHttp("It is not a hex string.", response);
            return;
        }

        byte[] textBytes = Hex.fromHex(text);
        replier.replySingleDataSuccessHttp(Hex.toHex(Hash.sha256(textBytes)),response);
    }

}