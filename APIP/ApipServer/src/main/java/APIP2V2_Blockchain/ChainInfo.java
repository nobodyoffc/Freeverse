package APIP2V2_Blockchain;

import appTools.Settings;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import feip.feipData.Service;
import nasa.NaSaRpcClient;
import server.ApipApiNames;
import fcData.ReplyBody;
import fch.fchData.FchChainInfo;
import initial.Initiator;
import utils.ObjectUtils;
import utils.http.AuthType;
import server.HttpRequestChecker;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

import static constants.FieldNames.HEIGHT;

@WebServlet(name = ApipApiNames.CHAIN_INFO, value = "/"+ ApipApiNames.SN_2+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.CHAIN_INFO)
public class ChainInfo extends HttpServlet {
    private final Settings settings;

    public ChainInfo() {
        this.settings = Initiator.settings;
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
        //Check authorization
        try {
            HttpRequestChecker httpRequestChecker = new HttpRequestChecker(settings);
            boolean isOk = httpRequestChecker.checkRequestHttp(request, response, authType);
            if (!isOk) {
                return;
            }
            ReplyBody replier = httpRequestChecker.getReplyBody();
            String height=null;
            if(request.getParameter(HEIGHT)!=null)height = request.getParameter(HEIGHT);
            else if(null!= httpRequestChecker.getRequestBody().getFcdsl().getOther()){
                Object other = httpRequestChecker.getRequestBody().getFcdsl().getOther();
                Map<String,String> otherMap = ObjectUtils.objectToMap(other,String.class,String.class);
                if(otherMap!=null)
                    height = otherMap.get(HEIGHT);
            }

            FchChainInfo freecashInfo = new FchChainInfo();
            if (height == null) {
                NaSaRpcClient naSaRpcClient = (NaSaRpcClient) settings.getClient(Service.ServiceType.NASA_RPC);
                freecashInfo.infoBest(naSaRpcClient);
                replier.setBestHeight(Long.valueOf(freecashInfo.getHeight()));
            } else {
                ElasticsearchClient esClient = (ElasticsearchClient) settings.getClient(Service.ServiceType.ES);
                freecashInfo.infoByHeight(Long.parseLong(height), esClient);
                replier.setGot(1L);
                replier.setTotal(1L);
                replier.setBestBlock();
            }
            replier.replySingleDataSuccessHttp(freecashInfo, response);
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
