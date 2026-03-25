package APIP0V1_OpenAPI;

import constants.ApipApiNames;
import data.fcData.FcEntity;
import data.fcData.ReplyBody;
import initial.Initiator;
import utils.EsUtils;
import utils.http.AuthType;
import server.FcHttpRequestHandler;
import server.HttpRequestChecker;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static constants.FieldNames.ID;

@WebServlet(name = ApipApiNames.ENTITY_BY_IDS, value = "/"+ ApipApiNames.ENTITY_BY_IDS +"/"+ ApipApiNames.VER_1)
public class EntityByIds extends HttpServlet {
    private final FcHttpRequestHandler fcHttpRequestHandler;
    private final ReplyBody replier;
    private final HttpRequestChecker httpRequestChecker;

    public EntityByIds() {
        config.Settings settings = Initiator.settings;
        this.replier = new ReplyBody(settings);
        this.httpRequestChecker = new HttpRequestChecker(settings, replier);
        this.fcHttpRequestHandler = new FcHttpRequestHandler(replier, settings);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        AuthType authType = AuthType.SYMKEY_ENCRYPT;
        doRequest(request, response, authType);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        AuthType authType = AuthType.FC_SIGN_URL;
        doRequest(request, response, authType);
    }

    protected void doRequest(HttpServletRequest request, HttpServletResponse response, AuthType authType) {
        boolean isOk = httpRequestChecker.checkRequestHttp(request, response, authType);
        if (!isOk) {
            return;
        }

        // Get entity name from FCDSL
        String entityName = httpRequestChecker.getRequestBody().getFcdsl().getEntity();
        if (entityName == null || entityName.isEmpty()) {
            replier.replyOtherErrorHttp("The parameter 'entity' is required.", response);
            return;
        }

        // Convert entity name to ES index name
        String indexName = EsUtils.convertEntityNameToIndexName(entityName);

        // Get entity class - try converted name first, then try original lowercase
        Class<?> entityClass = FcEntity.getEntityClass(indexName);
        if (entityClass == null) {
            // Try with original name in lowercase
            String lowerEntityName = entityName.toLowerCase();
            if (!lowerEntityName.equals(indexName)) {
                entityClass = FcEntity.getEntityClass(lowerEntityName);
                if (entityClass != null) {
                    indexName = lowerEntityName;
                }
            }
        }
        
        if (entityClass == null) {
            replier.replyOtherErrorHttp("Unknown entity type: " + entityName, response);
            return;
        }

        // Check if ids parameter is required
        if (httpRequestChecker.getRequestBody().getFcdsl().getIds() == null) {
            replier.replyOtherErrorHttp("The parameter 'ids' is required.", response);
            return;
        }

        // Request
        fcHttpRequestHandler.doIdsRequest(indexName, entityClass, ID, request, response, authType);
    }

}

