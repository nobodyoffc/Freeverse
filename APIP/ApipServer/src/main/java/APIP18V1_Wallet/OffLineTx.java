package APIP18V1_Wallet;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import handlers.CashHandler;
import fch.ParseTools;
import server.ApipApiNames;
import constants.CodeMessage;
import fcData.ReplyBody;
import fch.*;
import handlers.CashHandler.SearchResult;
import fch.fchData.Cash;
import fch.fchData.SendTo;
import initial.Initiator;
import server.HttpRequestChecker;
import tools.http.AuthType;
import handlers.MempoolHandler;
import handlers.Handler.HandlerType;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static constants.Constants.Dust;
import static constants.FieldNames.DATA_FOR_OFF_LINE_TX;
import static fch.CryptoSign.parseDataForOffLineTxFromOther;
import appTools.Settings;
import feip.feipData.Service;

@WebServlet(name = ApipApiNames.OFF_LINE_TX, value = "/"+ ApipApiNames.SN_18+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.OFF_LINE_TX)
public class OffLineTx extends HttpServlet {
    private final Settings settings = Initiator.settings;
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doRequest(request, response, authType,settings);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        doRequest(request, response, authType,settings);
    }
    protected void doRequest(HttpServletRequest request, HttpServletResponse response, AuthType authType, Settings settings) throws IOException {
        ReplyBody replier = new ReplyBody(settings);
        //Check authorization
        ElasticsearchClient esClient = (ElasticsearchClient) settings.getClient(Service.ServiceType.ES);
        //Do FCDSL other request
        HttpRequestChecker httpRequestChecker = new HttpRequestChecker(settings, replier);
        Map<String, String> other = httpRequestChecker.checkOtherRequestHttp(request, response, authType);
        if (other == null) return;
        //Do this request
        String json = other.get(DATA_FOR_OFF_LINE_TX);
        DataForOffLineTx dataForSignInCs = parseDataForOffLineTxFromOther(json);

        //Check API
        if(dataForSignInCs==null) {
            replier.replyHttp(CodeMessage.Code1012BadQuery,null,response);
            return;
        }

        String fromFid = dataForSignInCs.getFromFid();
        Long cd = dataForSignInCs.getCd();
        List<SendTo> sendToList = dataForSignInCs.getSendToList();
        int outputSize=0;
        if(sendToList!=null)outputSize=sendToList.size();
        String msg = dataForSignInCs.getMsg();
        int msgSize=0;
        if(msg!=null)msgSize = msg.getBytes().length;

        long amount = 0;
        if(dataForSignInCs.getSendToList()!=null && !dataForSignInCs.getSendToList().isEmpty()) {
            for (SendTo sendTo : dataForSignInCs.getSendToList()) {
                if (sendTo.getAmount() < Dust) {
                    replier.replyOtherErrorHttp("The amount must be more than "+Dust+"fch.", response);
                    return;
                }
                amount += ParseTools.coinToSatoshi(sendTo.getAmount());
            }
        }
        SearchResult<Cash> cashListReturn=null;

        if(cd!=null) {
            MempoolHandler mempoolHandler = (MempoolHandler) settings.getHandler(HandlerType.MEMPOOL);
            cashListReturn = CashHandler.getValidCashes(fromFid,amount, cd, null, outputSize, msgSize,null,esClient, mempoolHandler);
        }
        if(cashListReturn==null){
            replier.replyOtherErrorHttp("Can't get cashes. Check ES.", response);
            return;
        }
        List<Cash> meetList = cashListReturn.getData();
        if(meetList==null || meetList.isEmpty()){
            replier.replyOtherErrorHttp(cashListReturn.getMessage(), response);
            return;
        }

        long totalValue = 0;
        for(Cash cash :meetList){
            totalValue += cash.getValue();
        }

        if(totalValue < amount + 1000){
            replier.replyOtherErrorHttp("Cashes meeting this cd can not match the total amount of outputs.", response);
            return;
        }

        String rawTxForCs = CryptoSign.makeRawTxForCs(dataForSignInCs,meetList);
        replier.replySingleDataSuccessHttp(rawTxForCs,response);
    }
}