package APIP17V1_Crypto;

import server.ApipApiNames;
import constants.FieldNames;
import core.crypto.Base58;
import data.fcData.ReplyBody;
import initial.Initiator;
import server.HttpRequestChecker;
import utils.Hex;
import utils.http.AuthType;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

import config.Settings;

@WebServlet(name = ApipApiNames.HEX_TO_BASE_58, value = "/"+ ApipApiNames.SN_17+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.HEX_TO_BASE_58)
public class HexToBase58 extends HttpServlet {
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
        String hex = other.get(FieldNames.MESSAGE);
        if(hex==null){
                replier.replyOtherErrorHttp("The hex missed.", response);
                return;
        }
        if (!Hex.isHex32(hex)) {
            replier.replyOtherErrorHttp("It is not a hex string.", response);
            return;
        }

        replier.replySingleDataSuccessHttp(Base58.encode(Hex.fromHex(hex)),response);
    }
}