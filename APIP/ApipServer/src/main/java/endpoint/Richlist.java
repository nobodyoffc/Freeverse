package endpoint;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import constants.ApipApiNames;
import data.fcData.ReplyBody;
import data.fchData.Freer;
import constants.FieldNames;
import constants.IndicesNames;
import initial.Initiator;
import utils.FchUtils;
import utils.JsonUtils;
import utils.http.AuthType;
import server.HttpRequestChecker;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import config.Settings;
import data.feipData.Service;

@WebServlet(name = ApipApiNames.RICHLIST, value = "/"+ ApipApiNames.RICHLIST)
public class Richlist extends HttpServlet {
    private final Settings settings = Initiator.settings;
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FREE;
        doRequest(request, response, authType,settings);
    }
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.SYMKEY_ENCRYPT;
        doRequest(request, response, authType,settings);
    }

    protected void doRequest(HttpServletRequest request, HttpServletResponse response, AuthType authType, Settings settings) throws ServletException, IOException {
        String numberStr = request.getParameter(FieldNames.NUMBER);
        int number = 0;
        try{
            number = Integer.parseInt(numberStr);
        }catch (Exception ignore){}
        if(number==0)number=100;

        //Check authorization
        try {
            HttpRequestChecker httpRequestChecker = new HttpRequestChecker(settings);
            httpRequestChecker.checkRequestHttp(request, response, authType);
            ReplyBody replier = httpRequestChecker.getReplyBody();

            int finalNumber = number;
            ElasticsearchClient esClient = (ElasticsearchClient) settings.getClient(Service.ServiceType.ES);
            SearchResponse<Freer> result = esClient.search(s -> s.index(IndicesNames.FREER).size(finalNumber).sort(so -> so.field(f -> f.field(FieldNames.BALANCE).order(SortOrder.Desc))), Freer.class);
            if(result==null||result.hits()==null||result.hits().hits()==null){
                replier.replyHttp("Failed to get data.",response);
                return;
            }
            Map<String,Double> richMap = new LinkedHashMap<>();
            for(Hit<Freer> hit : result.hits().hits()){
                Freer cid = hit.source();
                if(cid ==null)continue;
                richMap.put(cid.getId(), FchUtils.satoshiToCoin(cid.getBalance()));
            }
            if(richMap.isEmpty()) {
                replier.replyHttp("Failed to get data.",response);
                return;
            }

            replier.replyHttp(JsonUtils.toNiceJson(richMap),response);
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}