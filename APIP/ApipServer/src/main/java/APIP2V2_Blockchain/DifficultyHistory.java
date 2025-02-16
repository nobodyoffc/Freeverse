package APIP2V2_Blockchain;

import appTools.Settings;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import feip.feipData.Service;
import server.ApipApiNames;
import fcData.ReplyBody;
import fch.fchData.FchChainInfo;
import initial.Initiator;
import tools.ObjectTools;
import tools.http.AuthType;
import server.HttpRequestChecker;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

import static fch.fchData.FchChainInfo.MAX_REQUEST_COUNT;

@WebServlet(name = ApipApiNames.DIFFICULTY_HISTORY, value = "/"+ ApipApiNames.SN_2+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.DIFFICULTY_HISTORY)
public class DifficultyHistory extends HttpServlet {
    private final Settings settings;

    public DifficultyHistory() {
        this.settings = Initiator.settings;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doRequest(request, response, authType, settings);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        AuthType authType = AuthType.FC_SIGN_URL;
        doRequest(request, response, authType, settings);
    }
    protected void doRequest(HttpServletRequest request, HttpServletResponse response, AuthType authType, Settings settings) throws ServletException, IOException {
        ReplyBody replier = new ReplyBody(settings);
        //Check authorization
            HttpRequestChecker httpRequestChecker = new HttpRequestChecker(settings, replier);
            boolean isOk = httpRequestChecker.checkRequestHttp(request, response, authType);
            if (!isOk) {
                return;
            }

            long startTime = 0;
            long endTime = 0;
            int count = 0;
            if(httpRequestChecker.getRequestBody()!=null && httpRequestChecker.getRequestBody().getFcdsl()!=null && httpRequestChecker.getRequestBody().getFcdsl().getOther()!=null) {
                Object other =  httpRequestChecker.getRequestBody().getFcdsl().getOther();
                Map<String, String> paramMap = ObjectTools.objectToMap(other,String.class, String.class);//DataGetter.getStringMap(other);
                String endTimeStr = paramMap.get("endTime");
                String startTimeStr = paramMap.get("startTime");
                String countStr = paramMap.get("count");
                if (startTimeStr != null) startTime = Long.parseLong(startTimeStr);
                if (endTimeStr != null) endTime = Long.parseLong(endTimeStr);
                if (countStr != null) count = Integer.parseInt(countStr);
            }

            if (count > MAX_REQUEST_COUNT){
                replier.replyOtherErrorHttp( "The count can not be bigger than " + FchChainInfo.MAX_REQUEST_COUNT, response);
                return;
            }

            ElasticsearchClient esClient = (ElasticsearchClient) settings.getClient(Service.ServiceType.ES);

            Map<Long, String> hist = FchChainInfo.difficultyHistory(startTime, endTime, count, esClient);

            if (hist == null){
                replier.replyOtherErrorHttp( "Failed to get the difficulty history.", response);
                return;
            }

            replier.setGot((long) hist.size());
            replier.setBestBlock();
            replier.setTotal( replier.getBestHeight()- 1);
            replier.reply0SuccessHttp(hist, response);
    }
}
