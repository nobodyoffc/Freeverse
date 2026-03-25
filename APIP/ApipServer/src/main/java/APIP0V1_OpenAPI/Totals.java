package APIP0V1_OpenAPI;

import config.Settings;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.cat.IndicesResponse;
import co.elastic.clients.elasticsearch.cat.indices.IndicesRecord;
import constants.ApipApiNames;
import data.fcData.ReplyBody;
import data.feipData.ServiceType;
import initial.Initiator;
import utils.http.AuthType;
import server.HttpRequestChecker;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WebServlet(name = ApipApiNames.TOTALS, value = "/"+ ApipApiNames.TOTALS+"/"+ ApipApiNames.VER_1 )
public class Totals extends HttpServlet {
    private final Settings settings;
    private final ReplyBody replier;
    private final HttpRequestChecker httpRequestChecker;

    public Totals() {
        this.settings = Initiator.settings;
        this.replier = new ReplyBody(settings);
        this.httpRequestChecker = new HttpRequestChecker(settings, replier);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthType authType = AuthType.SYMKEY_ENCRYPT;
        doRequest(request, response, authType, settings);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        doRequest(request, response, authType, settings);
    }
    protected void doRequest(HttpServletRequest request, HttpServletResponse response, AuthType authType, Settings settings) throws IOException {
        //Check authorization
        ElasticsearchClient esClient = (ElasticsearchClient) settings.getClient(ServiceType.ES);
        boolean isOk = httpRequestChecker.checkRequestHttp(request, response, authType);
        if (!isOk) {
                return;
            }

            IndicesResponse result = esClient.cat().indices();
            List<IndicesRecord> indicesRecordList = result.valueBody();

            Map<String, String> allSumMap = new HashMap<>();
            for (IndicesRecord record : indicesRecordList) {
                allSumMap.put(record.index(), record.docsCount());
        }
        replier.setGot((long) allSumMap.size());
        replier.setTotal((long) allSumMap.size());
        replier.reply0SuccessHttp(allSumMap,response);
    }
}