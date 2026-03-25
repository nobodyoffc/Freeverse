package api;

import data.apipData.WebhookPushBody;
import config.Settings;
import data.fcData.ReplyBody;
import managers.AccountManager;
import managers.Manager;
import managers.SessionManager;
import server.ApipApi;
import server.DiskApiNames;
import initial.Initiator;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static data.fcData.Signature.symSign;

@WebServlet(name = DiskApiNames.NEW_ORDER, value ="/"+ DiskApiNames.NEW_ORDER+ DiskApiNames.VER_1)
public class NewOrder extends HttpServlet {

    private final Settings settings = Initiator.settings;
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ReplyBody replyBody = new ReplyBody(settings);
        replyBody.replyHttp("GET is not available for the API Endpoint.",response);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        SessionManager sessionHandler = (SessionManager)settings.getManager(Manager.ManagerType.SESSION);
        byte[] requestBodyBytes = request.getInputStream().readAllBytes();

        WebhookPushBody webhookPushBody = WebhookPushBody.checkWebhookPushBody(sessionHandler, requestBodyBytes);
        if (webhookPushBody == null) return;

        String method = webhookPushBody.getMethod();

        if(method.equals(ApipApi.HOOK_NEW_CASH_BY_FIDS.getName())) {
            AccountManager accountHandler = (AccountManager) settings.getManager(Manager.ManagerType.ACCOUNT);
            accountHandler.updateAll();
        }
    }
}
