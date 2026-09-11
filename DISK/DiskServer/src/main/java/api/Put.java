package api;

import config.Settings;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import constants.CodeMessage;
import data.fcData.DiskItem;
import data.fcData.Hat;
import data.fcData.ReplyBody;
import data.feipData.ServiceType;
import data.feipData.serviceParams.DiskParams;
import managers.DiskManager;
import managers.Manager;
import initial.Initiator;
import server.DiskApiNames;
import server.HttpRequestChecker;
import utils.DateUtils;
import utils.http.AuthType;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static constants.FieldNames.DID;
import static constants.FieldNames.RESULT;
import static constants.Strings.DATA;

@WebServlet(name = DiskApiNames.PUT, value = "/"+ DiskApiNames.PUT+"/"+ DiskApiNames.VER_1)
public class Put extends HttpServlet {

    private final Settings settings = Initiator.settings;
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ReplyBody replier = new ReplyBody(settings);
        replier.replyHttp(CodeMessage.Code1017MethodNotAvailable,response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ReplyBody replier = new ReplyBody(settings);

        long dataLifeDays;
        AuthType authType = AuthType.FC_SIGN_URL;

        //Check authorization
        HttpRequestChecker httpRequestChecker = new HttpRequestChecker(settings, replier);
        if (!httpRequestChecker.checkRequestHttp(request, response, authType)) return;
        DiskParams diskParams  = DiskParams.fromObject(settings.getService().getParams());

        if(diskParams==null)return;

        dataLifeDays = Long.parseLong(diskParams.getDataLifeDays());
        //Do request
        long maxBytes = maxPutBytes(diskParams);
        if (request.getContentLengthLong() > maxBytes) {
            replier.replyOtherErrorHttp("Data exceeds the limit of " + maxBytes + " bytes.", response);
            return;
        }
        InputStream inputStream = request.getInputStream();
        DiskManager diskHandler = (DiskManager)settings.getManager(Manager.ManagerType.DISK);
        Hat hat;
        try {
            hat = diskHandler.put(inputStream, maxBytes);
        } catch (IOException e) {
            // Nothing was stored, so nothing may be indexed or reported as saved.
            replier.replyOtherErrorHttp("Failed to store the data: " + e.getMessage(), response);
            return;
        }

        Map<String,String> dataMap = new HashMap<>();
        dataMap.put(DID, hat.getId());
        String result = updateDataInfoToEs(dataLifeDays, hat.getSize(), hat.getId(),settings);
        dataMap.put(RESULT,result);
        replier.reply0SuccessHttp(dataMap,response);
    }

    /** The service's maxDataSize in bytes, or DiskManager's default when unset or unreadable. */
    static long maxPutBytes(DiskParams diskParams) {
        try {
            if (diskParams != null && diskParams.getMaxDataSize() != null) {
                long configured = Long.parseLong(diskParams.getMaxDataSize().trim());
                if (configured > 0) return configured;
            }
        } catch (NumberFormatException ignored) {
        }
        return DiskManager.DEFAULT_MAX_PUT_BYTES;
    }

    public static String updateDataInfoToEs(long dataLifeDays, long bytesLength, String did, Settings settings) throws IOException {
        System.out.println("Save File info to ES...");

        long saveDate = System.currentTimeMillis();
        Long expire = saveDate + DateUtils.dayToLong(dataLifeDays);
        DiskItem diskItem = new DiskItem(did, saveDate,expire, bytesLength);

        ElasticsearchClient esClient = (ElasticsearchClient)settings.getClient(ServiceType.ES);

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
