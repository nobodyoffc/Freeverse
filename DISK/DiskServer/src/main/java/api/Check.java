package api;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.GetResponse;
import config.Settings;
import data.fcData.DiskItem;
import data.feipData.Service;
import handlers.DiskManager;
import handlers.Manager;
import initial.Initiator;
import server.DiskApiNames;
import utils.Hex;
import utils.http.AuthType;
import data.fcData.ReplyBody;
import server.HttpRequestChecker;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static constants.FieldNames.DID;
import static constants.Strings.DATA;

@WebServlet(name = DiskApiNames.CHECK, value = "/"+ DiskApiNames.CHECK+"/"+ DiskApiNames.VER_1 )
public class Check extends HttpServlet {

    private final Settings settings = Initiator.settings;
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ReplyBody replier = new ReplyBody(settings);

        AuthType authType = AuthType.FC_SIGN_URL;
        //Check authorization
        HttpRequestChecker httpRequestChecker = new HttpRequestChecker(settings, replier);
        httpRequestChecker.checkRequestHttp(request, response, authType);

        //Do request
        doRequest(request, response, replier);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        AuthType authType = AuthType.FC_SIGN_URL;

        ReplyBody replier = new ReplyBody(settings);
        //Check authorization
        HttpRequestChecker httpRequestChecker = new HttpRequestChecker(settings, replier);
        httpRequestChecker.checkRequestHttp(request, response, authType);
        //Do request
        doRequest(request, response, replier);
    }

    private void doRequest(HttpServletRequest request, HttpServletResponse response, ReplyBody replier) {
        String did = request.getParameter(DID);
        if (did == null){
            replier.replyOtherErrorHttp("Failed to get "+DID+"From the URL.", response);
            return;
        }

        if(!Hex.isHex32(did)) {
            replier.replyOtherErrorHttp("It is not a hex string of a 32 byte array.", did, response);
            return;
        }
        DiskManager diskManager = (DiskManager) settings.getManager(Manager.ManagerType.DISK);
        Boolean isFileExists = Boolean.TRUE.equals(diskManager.checkFileOfDisk( did));
        if(isFileExists){
            ElasticsearchClient esClient = (ElasticsearchClient) settings.getClient(Service.ServiceType.ES);
            try {
                GetResponse<DiskItem> result = esClient.get(g -> g.index(Settings.addSidBriefToName(settings.getSid(), DATA)).id(did), DiskItem.class);
                if(result!=null && result.source()!=null)
                    replier.reply0SuccessHttp(result.source(),response);
                return;
            } catch (IOException e) {
                replier.replyOtherErrorHttp("Data exists. But failed to get data info.", did, response);
                return;
            }
        }
        replier.replyOtherErrorHttp("Data exists. But failed to get data info.", did, response);
    }
}
