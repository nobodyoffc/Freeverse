package APIP18V1_Wallet;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import core.fch.RawTxInfo;
import core.fch.TxCreator;
import handlers.CashManager;
import handlers.MempoolManager;
import org.jetbrains.annotations.Nullable;
import server.ApipApiNames;
import constants.CodeMessage;
import data.fcData.ReplyBody;
import handlers.CashManager.SearchResult;
import data.fchData.Cash;
import data.fchData.SendTo;
import initial.Initiator;
import server.HttpRequestChecker;
import utils.FchUtils;
import utils.http.AuthType;
import handlers.Manager.ManagerType;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

import static constants.Constants.Dust;
import static constants.FieldNames.*;
import static core.fch.TxCreator.parseDataForOffLineTxFromOther;
import config.Settings;
import data.feipData.Service;

@WebServlet(name = ApipApiNames.OFF_LINE_TX, value = "/"+ ApipApiNames.SN_18+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.OFF_LINE_TX)
public class OffLineTx extends HttpServlet {
    private final Settings settings = Initiator.settings;
    private final ReplyBody replier = new ReplyBody(settings);
    //Check authorization
    private final ElasticsearchClient esClient = (ElasticsearchClient) settings.getClient(Service.ServiceType.ES);
    //Do FCDSL other request
    private final HttpRequestChecker httpRequestChecker = new HttpRequestChecker(settings, replier);

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

        Map<String, String> other = httpRequestChecker.checkOtherRequestHttp(request, response, authType);
        if (other == null) return;
        String ver = "2";

        RawTxInfo rawTxInfo;
        if (request.getMethod().equals("GET")) {
            rawTxInfo = parseUrlParamsToOffLineRequestData(request, response);
            if (rawTxInfo == null){
                replier.replyHttp(CodeMessage.Code1012BadQuery, null, response);
                return;
            }
        } else {
            // Handle POST request as before
            String json = other.get(DATA_FOR_OFF_LINE_TX);
            rawTxInfo = parseDataForOffLineTxFromOther(json);
        }

        //Check API
        if(rawTxInfo ==null) {
            replier.replyHttp(CodeMessage.Code1012BadQuery,null,response);
            return;
        }

        String fromFid = rawTxInfo.getSender();
        Long cd = rawTxInfo.getCd();
        List<SendTo> sendToList = rawTxInfo.getOutputs();
        int outputSize=0;
        if(sendToList!=null)outputSize=sendToList.size();
        String msg = rawTxInfo.getOpReturn();
        int msgSize=0;
        if(msg!=null)msgSize = msg.getBytes().length;

        long amount = 0;
        if(rawTxInfo.getOutputs()!=null && !rawTxInfo.getOutputs().isEmpty()) {
            for (SendTo sendTo : rawTxInfo.getOutputs()) {
                if (sendTo.getAmount() < Dust) {
                    replier.replyOtherErrorHttp("The amount must be more than "+Dust+"fch.", response);
                    return;
                }
                amount += FchUtils.coinToSatoshi(sendTo.getAmount());
            }
        }
        SearchResult<Cash> cashListReturn=null;

        if(cd!=null) {
            MempoolManager mempoolHandler = (MempoolManager) settings.getManager(ManagerType.MEMPOOL);
            cashListReturn = CashManager.getValidCashes(fromFid,amount, cd, null, outputSize, msgSize,null,esClient, mempoolHandler);
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

        String rawTxForCs = TxCreator. makeOffLineTxRequiredJson(rawTxInfo,meetList);

        replier.replyHttp(CodeMessage.Code0Success, rawTxForCs, response);
    }

    @Nullable
    public RawTxInfo parseUrlParamsToOffLineRequestData(HttpServletRequest request, HttpServletResponse response) {
        RawTxInfo dataForSignInCs;
        // Parse URL parameters for GET request
        String fromFid = request.getParameter("fromFid");
        String toFidsStr = request.getParameter("toFids");
        String amountsStr = request.getParameter("amounts");
        String msg = request.getParameter(MESSAGE);
        String cdStr = request.getParameter(CD);
        String ver = request.getParameter(VER);

        if (fromFid == null) {
            return null;
        }

        dataForSignInCs = new RawTxInfo();
        dataForSignInCs.setSender(fromFid);

        // Parse sendToList from toFids and amounts
        if (toFidsStr != null && amountsStr != null) {
            String[] toFids = toFidsStr.split(",");
            String[] amounts = amountsStr.split(",");
            if (toFids.length != amounts.length) {
                return null;
            }

            List<SendTo> sendToList = new ArrayList<>();
            for (int i = 0; i < toFids.length; i++) {
                SendTo sendTo = new SendTo();
                sendTo.setFid(toFids[i]);
                sendTo.setAmount(Double.parseDouble(amounts[i]));
                sendToList.add(sendTo);
            }
            dataForSignInCs.setOutputs(sendToList);
        }

        if (msg != null) {
            dataForSignInCs.setOpReturn(msg);
        }

        if (cdStr != null) {
            dataForSignInCs.setCd(Long.parseLong(cdStr));
        }

        if(ver!=null){
            dataForSignInCs.setVer(ver);
        }
        return dataForSignInCs;
    }
}