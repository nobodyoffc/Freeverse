package APIP2V2_Blockchain;

import config.Settings;
import data.feipData.Service;
import server.ApipApiNames;
import data.fcData.ReplyBody;
import data.fchData.Block;
import initial.Initiator;
import utils.EsUtils;
import utils.http.AuthType;
import server.HttpRequestChecker;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import co.elastic.clients.elasticsearch.ElasticsearchClient;

import java.io.IOException;

@WebServlet(name = ApipApiNames.BEST_BLOCK, value = "/"+ ApipApiNames.SN_2+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.BEST_BLOCK)
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
        boolean isOk = httpRequestChecker.checkRequestHttp(request, response, authType);
        if (!isOk) {
            return;
        }

        ElasticsearchClient esClient = (ElasticsearchClient) settings.getClient(Service.ServiceType.ES);
        if (esClient == null) {
            replier.replyOtherErrorHttp("Elasticsearch client not available", response);
            return;
        }

        Block bestBlock = EsUtils.getBestBlock(esClient);
        
        replier.setBestHeight(bestBlock.getHeight());
        replier.setBestBlockId(bestBlock.getId());
        replier.replySingleDataSuccessHttp(bestBlock,response);
    }
} 