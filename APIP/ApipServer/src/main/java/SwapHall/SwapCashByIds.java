package SwapHall;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.MgetResponse;
import co.elastic.clients.elasticsearch.core.mget.MultiGetResponseItem;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import config.Settings;
import constants.ApipApiNames;
import constants.IndicesNames;
import data.fchData.Cash;
import data.feipData.ServiceType;
import initial.Initiator;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.util.*;

@WebServlet(ApipApiNames.SwapHallPath + ApipApiNames.SwapCashByIds)
public class SwapCashByIds extends HttpServlet {
    private final Settings settings = Initiator.settings;
    private static final Gson gson = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>(){}.getType();

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter writer = response.getWriter();

        byte[] bodyBytes = request.getInputStream().readAllBytes();
        if (bodyBytes == null || bodyBytes.length == 0) {
            writeError(writer, response, "Request body is empty.");
            return;
        }

        Map<String, Object> dataMap;
        try {
            dataMap = gson.fromJson(new String(bodyBytes), MAP_TYPE);
        } catch (Exception e) {
            writeError(writer, response, "Invalid JSON: " + e.getMessage());
            return;
        }

        if (dataMap == null || dataMap.get("ids") == null) {
            writeError(writer, response, "The 'ids' field is required.");
            return;
        }

        List<String> idList;
        try {
            idList = gson.fromJson(gson.toJson(dataMap.get("ids")), new TypeToken<List<String>>(){}.getType());
        } catch (Exception e) {
            writeError(writer, response, "Invalid 'ids' format: " + e.getMessage());
            return;
        }

        if (idList == null || idList.isEmpty()) {
            writeError(writer, response, "The 'ids' list must not be empty.");
            return;
        }

        ElasticsearchClient esClient = (ElasticsearchClient) settings.getClient(ServiceType.ES);

        try {
            MgetResponse<Cash> mgetResponse = esClient.mget(m -> m.index(IndicesNames.CASH).ids(idList), Cash.class);

            Map<String, Cash> resultMap = new LinkedHashMap<>();
            for (MultiGetResponseItem<Cash> item : mgetResponse.docs()) {
                if (item.result().found() && item.result().source() != null) {
                    Cash cash = item.result().source();
                    resultMap.put(item.result().id(), cash);
                }
            }

            writeSuccess(writer, resultMap, resultMap.size());
        } catch (Exception e) {
            writeError(writer, response, "ES query failed: " + e.getMessage());
        }
    }

    private void writeSuccess(PrintWriter writer, Object data, int count) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 0);
        result.put("message", "Success.");
        result.put("data", data);
        result.put("got", count);
        result.put("total", count);
        writer.write(gson.toJson(result));
    }

    private void writeError(PrintWriter writer, HttpServletResponse response, String message) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        Map<String, Object> err = new HashMap<>();
        err.put("code", 1020);
        err.put("message", message);
        writer.write(gson.toJson(err));
    }
}
