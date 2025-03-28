package APIP0V2_OpenAPI;

import appTools.Settings;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import utils.EsUtils;
import server.ApipApiNames;
import constants.CodeMessage;
import fcData.ReplyBody;
import feip.feipData.Service;
import feip.feipData.serviceParams.ApipParams;
import initial.Initiator;
import utils.JsonUtils;
import utils.http.AuthType;
import server.HttpRequestChecker;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static constants.Strings.SERVICE;

@WebServlet(name = ApipApiNames.GET_SERVICE, value = "/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.GET_SERVICE)
public class GetService extends HttpServlet {
    private final Settings settings;
    private final ReplyBody replier;
    private final HttpRequestChecker httpRequestChecker;

    public GetService() {
        this.settings = Initiator.settings;
        this.replier = new ReplyBody(settings);
        // Initialize with ApipParams.class as the expected params type
        this.httpRequestChecker = new HttpRequestChecker(settings, replier);
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FREE;
        boolean isOk = httpRequestChecker.checkRequestHttp(request, response, authType);
        if (!isOk) {
            return;
        }
        Service service = doRequest(response);
        if(service != null) {
            replier.setTotal(1L);
            replier.setGot(1L);
            replier.setBestBlock();
            String data = JsonUtils.toJson(service);
            replier.reply0SuccessHttp(data, response);
        } else {
            replier.replyOtherErrorHttp("Failed to get service info.", response);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        replier.replyHttp(CodeMessage.Code1017MethodNotAvailable,null);
    }

    private Service doRequest(HttpServletResponse response) {
        try {
            ElasticsearchClient esClient = (ElasticsearchClient) settings.getClient(Service.ServiceType.ES);
            Service service = EsUtils.getById(esClient, SERVICE, settings.getSid(), Service.class);
            if (service != null && service.getParams() != null) {
                // Convert params to ApipParams
                service.setParams(ApipParams.fromObject(service.getParams()));
            }
            return service;
        } catch (IOException e) {
            replier.replyOtherErrorHttp("EsClient wrong:" + e.getMessage(), response);
            return null;
        }
    }
}
