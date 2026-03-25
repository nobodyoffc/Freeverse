package nerve;

import constants.CodeMessage;
import data.fcData.ReplyBody;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import constants.ApiNames;
import data.fchData.Block;
import data.feipData.ServiceType;
import initial.Initiator;
import server.HttpRequestChecker;
import utils.EsUtils;
import utils.http.AuthType;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import config.Settings;

@WebServlet(ApiNames.NERVE + ApiNames.GetBestBlock)
public class BestBlock extends HttpServlet {
    private final Settings settings;
    private final ReplyBody replier;
    private final HttpRequestChecker httpRequestChecker;

    public BestBlock() {
        this.settings = Initiator.settings;
        this.replier = new ReplyBody(settings);
        this.httpRequestChecker = new HttpRequestChecker(settings, replier);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doRequest(request, response, authType, settings);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        AuthType authType = AuthType.FREE;
        doRequest(request, response, authType, settings);
    }

    protected void doRequest(HttpServletRequest request, HttpServletResponse response, AuthType authType, Settings settings) throws ServletException, IOException {
//        boolean isOk = httpRequestChecker.checkRequestHttp(request, response, authType);
//        if (!isOk) {
//            return;
//        }

        ElasticsearchClient esClient = (ElasticsearchClient) settings.getClient(ServiceType.ES);
        if (esClient == null) {
            replier.replyOtherErrorHttp("Elasticsearch client not available", response);
            return;
        }

        Block bestBlock = EsUtils.getBestBlock(esClient);

        replier.setCodeMessage(CodeMessage.Code0Success);
        replier.setData(bestBlock);
        replier.setBestHeight(bestBlock.getHeight());
        replier.setBestBlockId(bestBlock.getId());
        response.getWriter().write(replier.toJson());
        replier.clean();
    }
} 