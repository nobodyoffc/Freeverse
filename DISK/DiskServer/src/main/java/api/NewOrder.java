package api;

import data.apipData.WebhookPushBody;
import config.Settings;
import handlers.AccountManager;
import handlers.Manager;
import handlers.SessionManager;
import server.ApipApiNames;
import initial.Initiator;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static data.fcData.Signature.symSign;

@WebServlet(name = ApipApiNames.NEW_ORDER, value ="/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.NEW_ORDER)
public class NewOrder extends HttpServlet {

    private final Settings settings = Initiator.settings;
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.getWriter().write("GET is not available for the API Endpoint.");
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        SessionManager sessionHandler = (SessionManager)settings.getManager(Manager.ManagerType.SESSION);
        byte[] requestBodyBytes = request.getInputStream().readAllBytes();

        WebhookPushBody webhookPushBody = WebhookPushBody.checkWebhookPushBody(sessionHandler, requestBodyBytes);
        if (webhookPushBody == null) return;

        String method = webhookPushBody.getMethod();

        if(method.equals(ApipApiNames.NEW_CASH_BY_FIDS)) {
            AccountManager accountHandler = (AccountManager) settings.getManager(Manager.ManagerType.ACCOUNT);
            accountHandler.updateAll();
        }
    }
}
