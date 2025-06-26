package APIP19V1_Nid;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch._types.FieldValue;
import config.Settings;
import constants.FieldNames;
import data.fcData.ReplyBody;
import data.fchData.Cid;
import initial.Initiator;

import server.ApipApiNames;
import server.HttpRequestChecker;
import utils.http.AuthType;
import data.feipData.Nid;
import data.feipData.Service;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static constants.FieldNames.*;
import static constants.IndicesNames.CID;
import static core.crypto.KeyTools.isGoodFid;

import java.util.*;
import java.util.stream.Collectors;

@WebServlet(name = ApipApiNames.OID_BY_NIDS, value = "/"+ ApipApiNames.SN_19+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.OID_BY_NIDS)
public class OidByNids extends HttpServlet {
    private final Settings settings = Initiator.settings;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        AuthType authType = AuthType.FC_SIGN_URL;
        doRequest(request, response, authType, settings);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doRequest(request, response, authType, settings);
    }

    protected void doRequest(HttpServletRequest request, HttpServletResponse response, AuthType authType, Settings settings) {
        ReplyBody replier = new ReplyBody(settings);
        HttpRequestChecker httpRequestChecker = new HttpRequestChecker(settings, replier);
        
        // Check request
        Map<String, String> other = httpRequestChecker.checkOtherRequestHttp(request, response, authType);
        if (other == null) return;

        // Get nids from request
        String nidsJson = other.get("nids");
        if (nidsJson == null || nidsJson.isEmpty()) {
            replier.replyOtherErrorHttp("nids parameter is required", response);
            return;
        }

        try {
            // Parse nids JSON to List<String>
            List<String> nidList = new Gson().fromJson(nidsJson, new TypeToken<List<String>>(){}.getType());
            if (nidList == null || nidList.isEmpty()) {
                replier.replyOtherErrorHttp("Invalid nids format", response);
                return;
            }

            Map<String, String> nidOidMap = new HashMap<>();
            ElasticsearchClient esClient = (ElasticsearchClient) settings.getClient(Service.ServiceType.ES);

//            Map<String, String[]> nidNameIdMap = new HashMap<>();
            Map<String,String> nidCidMap = new HashMap<>();
            Map<String,String> cidFidMap = new HashMap<>();

            Iterator<String> iterator = nidList.iterator();
            while (iterator.hasNext()) {
                String nid = iterator.next();

                String[] parts = nid.split("@");
                if (parts.length != 2) {
                    nidOidMap.put(nid, "");
                    iterator.remove();
                    continue;
                }
//                nidNameIdMap.put(nid, parts);
                String id = parts[1];

                // Handle CID case
                if (id.contains("_")) {
                    nidCidMap.put(nid, id);
                    cidFidMap.put(id,"");
                    continue;
                }

                if(!isGoodFid(id)){
                    nidOidMap.put(nid, "");
                    iterator.remove();
                }
            }

            // Batch lookup CIDs
            if (!nidCidMap.isEmpty()) {
                try {
                    List<FieldValue> cidValues = nidCidMap.values().stream()
                            .map(FieldValue::of)
                            .collect(Collectors.toList());

                    SearchResponse<Cid> cidResult = esClient.search(s -> s
                            .index(CID)
                            .query(q -> q
                                    .terms(t -> t
                                            .field(USED_CIDS)
                                            .terms(v -> v.value(cidValues)))), Cid.class);

                    // Process CID results
                    cidResult.hits().hits().forEach(hit -> {
                        Cid cid = hit.source();
                        if (cid != null) {
                            List<String> usedCids = cid.getUsedCids();
                            for(String usedCid :usedCids){
                                if(cidFidMap.containsKey(usedCid)){
                                    cidFidMap.put(usedCid,cid.getId());
                                }
                            }
                        }
                    });
                } catch (Exception e) {
                    replier.replyOtherErrorHttp("Error searching CID: " + e.getMessage(), response);
                    return;
                }
            }

            List<String> handledNidList = new ArrayList<>();
            Map<String,String> nestOldNidMap = new HashMap<>();
            for (String nid: nidList){
                if(nid.contains("_")){
                    String cid = nidCidMap.get(nid);
                    String nestNid = nid.replace(cid,cidFidMap.get(cid));
                    handledNidList.add(nestNid);
                    nestOldNidMap.put(nestNid,nid);
                    continue;
                }
                handledNidList.add(nid);
                nestOldNidMap.put(nid,nid);
            }

            List<FieldValue> nidValues = handledNidList.stream()
                    .map(FieldValue::of)
                    .toList();

            SearchResponse<Nid> nidResult = esClient.search(s -> s
                    .index(NID)
                    .query(q -> q
                            .terms(t -> t
                                    .field(FieldNames.NID)
                                    .terms(v -> v.value(nidValues)))), Nid.class);

            nidResult.hits().hits().forEach(hit -> {
                Nid nid = hit.source();
                if (nid != null) {
                    nidOidMap.put(nestOldNidMap.get(nid.getNid()), nid.getOid());
                }
            });

            replier.replySingleDataSuccessHttp(nidOidMap, response);

        } catch (Exception e) {
            replier.replyOtherErrorHttp("Error processing request: " + e.getMessage(), response);
        }
    }
} 