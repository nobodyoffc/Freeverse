package api;

import config.Settings;
import constants.CodeMessage;
import data.fcData.ReplyBody;
import initial.Initiator;
import server.DiskApiNames;
import server.FcHttpRequestHandler;
import server.HttpRequestChecker;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet(name = DiskApiNames.SIGN_IN, value = "/"+ DiskApiNames.SIGN_IN+"/"+ DiskApiNames.VER_1)
public class SignIn extends HttpServlet {
    private final Settings settings;
    private final ReplyBody replier;
    private final HttpRequestChecker httpRequestChecker;

    public SignIn() {
        this.settings = Initiator.settings;
        this.replier = new ReplyBody(settings);
        this.httpRequestChecker = new HttpRequestChecker(settings, replier);
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        FcHttpRequestHandler.doSigInPost(request, response,replier,settings,httpRequestChecker);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        replier.replyHttp(CodeMessage.Code1017MethodNotAvailable,null);
    }
//
//    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
//        FcSession fcSession;
//        SessionManager sessionHandler = (SessionManager) settings.getManager(Manager.ManagerType.SESSION);
//        boolean isOk = httpRequestChecker.checkSignInRequestHttp(request, response);
//        if (!isOk) {
//            return;
//            }
//
//        String fid = httpRequestChecker.getFid();
//
//        // Check for mode and algorithm in fcdsl.other
//        SignInMode mode = SignInMode.NORMAL; // default
//        AlgorithmId algorithmId = null; // default (no encryption)
//
//        if (httpRequestChecker.getRequestBody().getFcdsl() != null &&
//            httpRequestChecker.getRequestBody().getFcdsl().getOther() != null) {
//
//            String modeStr = httpRequestChecker.getRequestBody().getFcdsl().getOther().get("mode");
//            if (modeStr != null) {
//                try {
//                    mode = SignInMode.valueOf(modeStr.toUpperCase());
//                } catch (IllegalArgumentException e) {
//                    // Keep default NORMAL mode
//                }
//            }
//
//            String algStr = httpRequestChecker.getRequestBody().getFcdsl().getOther().get("alg");
//            if (algStr != null) {
//                try {
//                    algorithmId = AlgorithmId.fromDisplayName(algStr);
//                } catch (IllegalArgumentException e) {
//                    replier.replyHttp(CodeMessage.Code4002NoSuchAlgorithm, response);
//                    return;
//                }
//            }
//        }
//
//        // Validate supported algorithms for encryption
//        if (algorithmId != null) {
//            if (!algorithmId.equals(AlgorithmId.EccAes256K1P7_No1_NrC7) &&
//                !algorithmId.equals(AlgorithmId.BitCore_EccAes256)) {
//                replier.replyHttp(CodeMessage.Code4002NoSuchAlgorithm, response);
//                return;
//            }
//            // Use the ECC sign-in path for encrypted sessions
//            FcHttpRequestHandler.doSigInPost(request, response, replier, settings, httpRequestChecker);
//        } else {
//            // Original sign-in path for unencrypted sessions
//            if (sessionHandler.getSessionByUserId(fid) == null || SignInMode.REFRESH.equals(mode)) {
//                try {
//                    fcSession = sessionHandler.addNewSession(fid, null);
//                } catch (Exception e) {
//                    replier.replyOtherErrorHttp("Something wrong when making sessionKey.\n" + e.getMessage(), response);
//                    return;
//                }
//            } else {
//                fcSession = sessionHandler.getSessionByUserId(fid);
//                if (fcSession == null) {
//                    try {
//                        fcSession = sessionHandler.addNewSession(fid, null);
//                    } catch (Exception e) {
//                        replier.replyOtherErrorHttp("Something wrong when making sessionKey.\n" + e.getMessage(), response);
//                        return;
//                    }
//                }
//            }
//            replier.reply0SuccessHttp(fcSession, response);
//        }
//        replier.clean();
//    }
//    @Override
//    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
//        replier.replyHttp(CodeMessage.Code1017MethodNotAvailable,null);
//    }
}
