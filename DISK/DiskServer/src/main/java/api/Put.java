package api;

import config.Settings;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import constants.CodeMessage;
import data.fcData.DiskItem;
import data.fcData.Hat;
import data.fcData.ReplyBody;
import data.feipData.Service;
import data.feipData.serviceParams.DiskParams;
import handlers.DiskManager;
import handlers.Manager;
import initial.Initiator;
import server.ApipApiNames;
import server.DiskApiNames;
import server.HttpRequestChecker;
import utils.DateUtils;
import utils.http.AuthType;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static constants.FieldNames.DID;
import static constants.FieldNames.RESULT;
import static constants.Strings.DATA;

@WebServlet(name = DiskApiNames.PUT, value = "/"+ ApipApiNames.VERSION_1 +"/"+ DiskApiNames.PUT)
public class Put extends HttpServlet {

    private final Settings settings = Initiator.settings;
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ReplyBody replier = new ReplyBody(settings);
        replier.setCode(CodeMessage.Code1017MethodNotAvailable);
        replier.setMessage(CodeMessage.Msg1017MethodNotAvailable);
        response.getWriter().write(replier.toNiceJson());
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ReplyBody replier = new ReplyBody(settings);

        long dataLifeDays;
        AuthType authType = AuthType.FC_SIGN_URL;

        //Check authorization
        HttpRequestChecker httpRequestChecker = new HttpRequestChecker(settings, replier);
        httpRequestChecker.checkRequestHttp(request, response, authType);
        DiskParams diskParams  = DiskParams.fromObject(settings.getService().getParams());

        if(diskParams==null)return;

        dataLifeDays = Long.parseLong(diskParams.getDataLifeDays());
        //Do request
        InputStream inputStream = request.getInputStream();
        DiskManager diskHandler = (DiskManager)settings.getManager(Manager.ManagerType.DISK);
        Hat hat = diskHandler.put(inputStream);

        Map<String,String> dataMap = new HashMap<>();
        dataMap.put(DID, hat.getId());
        String result = updateDataInfoToEs(dataLifeDays, hat.getSize(), hat.getId(),settings);
        dataMap.put(RESULT,result);
        replier.reply0SuccessHttp(dataMap,response);
    }

    public static String updateDataInfoToEs(long dataLifeDays, long bytesLength, String did, Settings settings) throws IOException {
        System.out.println("Save File info to ES...");

        long saveDate = System.currentTimeMillis();
        Long expire = saveDate + DateUtils.dayToLong(dataLifeDays);
        DiskItem diskItem = new DiskItem(did, saveDate,expire, bytesLength);

        ElasticsearchClient esClient = (ElasticsearchClient)settings.getClient(Service.ServiceType.ES);

        try {
            IndexResponse result = esClient.index(i -> i.index(Settings.addSidBriefToName(settings.getSid(), DATA)).id(did).document(diskItem));
            if(result==null){
                System.out.println("Failed to updateDataInfoToEs");
                return null;
            }
            return result.result().jsonValue();
        } catch (IOException e) {
            System.out.println("Failed to updateDataInfoToEs:"+e.getMessage());
            return null;
        }
    }
}
