package APIP20V1_Webhook;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import constants.*;
import data.fcData.ReplyBody;
import managers.Manager;
import managers.WebhookManager;
import initial.Initiator;
import constants.ApipApiNames;
import server.HttpRequestChecker;
import utils.http.AuthType;
import config.Settings;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@WebServlet(name = ApipApiNames.HOOK_NEW_CASH_BY_FIDS, value = "/"+ ApipApiNames.SN_20+"/"+ ApipApiNames.HOOK_NEW_CASH_BY_FIDS +"/"+ ApipApiNames.VER_1)
public class NewCashByFids extends HttpServlet {
    private final Settings settings = Initiator.settings;
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        AuthType authType = AuthType.ENCRYPTED;
        doRequest(request, response, authType,settings);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        AuthType authType = AuthType.FC_SIGN_URL;
        doRequest(request, response, authType,settings);
    }
    protected void doRequest(HttpServletRequest request, HttpServletResponse response, AuthType authType, Settings settings) {
        ReplyBody replier = new ReplyBody(settings);

        //Do FCDSL other request
        HttpRequestChecker httpRequestChecker = new HttpRequestChecker(settings, replier);
        Map<String, String> other = httpRequestChecker.checkOtherRequestHttp(request, response, authType);
        if (other == null) return;
        //Do this request
        doNewCashByFidsRequest(replier,other,settings, response);

    }

    private void doNewCashByFidsRequest(ReplyBody replier, Map<String, String> otherMap, Settings settings, HttpServletResponse response) {
        String addr = replier.getRequestChecker().getFid();
        String pubkey = replier.getRequestChecker().getPubkey();

        Gson gson = new Gson();
        WebhookManager.WebhookRequestBody webhookRequestBody;

        WebhookManager webhookHandler = (WebhookManager)settings.getManager(Manager.ManagerType.WEBHOOK);

        try {
            webhookRequestBody = gson.fromJson(otherMap.get(FieldNames.WEBHOOK_REQUEST_BODY), WebhookManager.WebhookRequestBody.class);
            webhookRequestBody.setUserId(addr);
            webhookRequestBody.setMethod(ApipApiNames.HOOK_NEW_CASH_BY_FIDS);
            webhookRequestBody.setPubkey(pubkey);
            String hookUserId = webhookRequestBody.makeHookUserId(settings.getSid());

            Map<String, String> dataMap = new HashMap<>();
            switch (webhookRequestBody.getOp()) {
                case Strings.SUBSCRIBE -> {
                    webhookHandler.putWebhookRequestBody(hookUserId, webhookRequestBody);

                    dataMap.put(Strings.OP, Strings.SUBSCRIBE);
                    dataMap.put(Strings.HOOK_USER_ID, hookUserId);
                    replier.setData(dataMap);
                }
                case Strings.UNSUBSCRIBE -> {
                    webhookHandler.remove(hookUserId);
                    dataMap.put(Strings.OP, Strings.UNSUBSCRIBE);
                    dataMap.put(Strings.HOOK_USER_ID, hookUserId);
                    replier.setData(dataMap);
                }
                case Strings.CHECK -> {
                    WebhookManager.WebhookRequestBody subscription = webhookHandler.getWebhookRequestBody(webhookRequestBody.getUserId());
                    dataMap.put(Strings.OP, Strings.CHECK);
                    if (subscription == null) {
                        dataMap.put(Strings.FOUND, Values.FALSE);
                    } else {
                        dataMap.put(Strings.FOUND, Values.TRUE);
                        dataMap.put(Strings.SUBSCRIBE, subscription.toJson());
                    }
                    replier.setData(dataMap);
                }
                default -> {
                    replier.replyOtherErrorHttp("The op in request body is wrong.", response);
                    return;
                }
            }

            replier.reply0SuccessHttp(response);
        } catch (JsonSyntaxException e) {
            replier.replyOtherErrorHttp(e.getMessage(), response);
        }
    }
}
