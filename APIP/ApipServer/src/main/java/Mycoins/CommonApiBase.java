package Mycoins;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import config.Settings;
import core.crypto.CryptoDataByte;
import core.crypto.Decryptor;
import core.crypto.Encryptor;
import core.crypto.KeyTools;
import data.fcData.AlgorithmId;
import data.feipData.Service;
import initial.Initiator;
import managers.AccountManager;
import managers.Manager;
import utils.Hex;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Base class for common multi-coin API endpoints.
 *
 * Handles AsyTwoWay encrypted POST with simple JSON body (no FCDSL wrapper).
 *
 * Request: AsyTwoWay envelope -> decrypted plaintext is simple JSON:
 *   {"coin":"FCH", "address":"Fxxx", ...}
 *
 * Response: plain JSON encrypted with AsyTwoWay:
 *   {"code":0, "data":{...}, "balance":123}
 */
public abstract class CommonApiBase {

    protected static final Gson gson = new Gson();
    protected final Settings settings = Initiator.settings;

    /**
     * Handle a POST request with encrypted simple JSON body.
     * @param apiName The API name for charging (e.g. "getBalance"). Null means free.
     */
    protected void handleEncryptedPost(HttpServletRequest request, HttpServletResponse response, String apiName) {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        // Step 1: Read the POST body (encrypted envelope)
        String envelopeJson;
        try {
            StringBuilder sb = new StringBuilder();
            BufferedReader reader = request.getReader();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            envelopeJson = sb.toString();
        } catch (IOException e) {
            writeError(response, 1003, "Failed to read request body", null);
            return;
        }

        if (envelopeJson == null || envelopeJson.isEmpty()) {
            writeError(response, 1003, "Request body is empty", null);
            return;
        }

        // Step 2: Parse the envelope and decrypt
        CryptoDataByte cryptoDataByte;
        try {
            cryptoDataByte = CryptoDataByte.fromJson(envelopeJson);
        } catch (Exception e) {
            writeError(response, 1013, "Failed to parse encrypted envelope: " + e.getMessage(), null);
            return;
        }

        byte[] prikey = settings.decryptPrikey();
        if (prikey == null) {
            writeError(response, 1020, "Server key not available", null);
            return;
        }

        // Decrypt using Decryptor (same as HttpRequestChecker.checkEncryptedRequest)
        try {
            Decryptor decryptor = new Decryptor();
            cryptoDataByte.setPrikeyB(prikey);
            decryptor.decrypt(cryptoDataByte);
        } catch (Exception e) {
            writeError(response, 1029, "Failed to decrypt: " + e.getMessage(), null);
            return;
        }

        if (cryptoDataByte.getCode() != 0 || cryptoDataByte.getData() == null) {
            writeError(response, 1029, "Failed to decrypt request body", null);
            return;
        }

        // Extract client pubkey for encrypting the response
        String clientPubkey = cryptoDataByte.getPubkeyA() != null
                ? Hex.toHex(cryptoDataByte.getPubkeyA()) : null;

        // Step 3: Check balance if this is a charged API
        String fid = null;
        AccountManager accountHandler = null;
        if (apiName != null && clientPubkey != null) {
            fid = KeyTools.pubkeyToFchAddr(clientPubkey);
            accountHandler = (AccountManager) settings.getManager(Manager.ManagerType.ACCOUNT);
            if (accountHandler != null && accountHandler.isBadBalance(fid)) {
                Service service = settings.getService();
                String minPayment = service.getMinPayment();
                String sid = service.getId();
                String msg = "Send at least " + minPayment + " F to " + service.getDealer()
                        + " to buy the service #" + sid + ".";
                writeError(response, 1004, msg, clientPubkey);
                return;
            }
        }

        // Step 4: Parse decrypted bytes as simple JSON map
        String decryptedJson = new String(cryptoDataByte.getData());

        Map<String, String> params;
        try {
            params = parseParams(decryptedJson);
        } catch (Exception e) {
            writeError(response, 1013, "Failed to parse request params: " + e.getMessage(), clientPubkey);
            return;
        }

        if (params == null) params = new HashMap<>();

        // Step 5: Call the specific API logic
        Object result;
        try {
            result = doApiRequest(params);
        } catch (ApiException e) {
            writeError(response, e.code, e.getMessage(), clientPubkey);
            return;
        } catch (Exception e) {
            writeError(response, 1020, e.getMessage(), clientPubkey);
            return;
        }

        // Step 6: Build success response
        Map<String, Object> respMap = new HashMap<>();
        respMap.put("code", 0);
        respMap.put("message", "Success");
        if (result instanceof Paged paged) {
            respMap.put("data", paged.data);
            if (paged.got != null) respMap.put("got", paged.got);
            if (paged.total != null) respMap.put("total", paged.total);
            if (paged.last != null) respMap.put("last", paged.last);
        } else {
            respMap.put("data", result);
        }

        // Step 7: Charge the user based on response size
        if (apiName != null && fid != null && accountHandler != null) {
            String respJson = gson.toJson(respMap);
            long newBalance = accountHandler.userSpend(fid, apiName, (long) respJson.length(), null);
            respMap.put("balance", newBalance);
        }

        String finalJson = gson.toJson(respMap);
        writeEncryptedResponse(response, finalJson, clientPubkey);
    }

    /**
     * Handle a POST request without charging (free API).
     */
    protected void handleEncryptedPost(HttpServletRequest request, HttpServletResponse response) {
        handleEncryptedPost(request, response, null);
    }

    /**
     * Decrypted request params as strings. A number keeps its JSON text ("50000", "0.5"); an
     * array or object (such as an {@code after} cursor) is kept as its JSON text.
     */
    static Map<String, String> parseParams(String json) {
        Map<String, String> params = new HashMap<>();
        JsonElement root = JsonParser.parseString(json);
        if (root == null || !root.isJsonObject()) return params;
        for (Map.Entry<String, JsonElement> e : root.getAsJsonObject().entrySet()) {
            JsonElement v = e.getValue();
            if (v == null || v.isJsonNull()) continue;
            params.put(e.getKey(), v.isJsonPrimitive() ? v.getAsString() : v.toString());
        }
        return params;
    }

    /** A cursor sent back by the client: a JSON array (["123","abc"]) or comma-separated text. */
    static List<String> parseCursor(String after) {
        if (after == null || after.isBlank()) return null;
        String t = after.trim();
        if (t.startsWith("[")) {
            List<String> list = new ArrayList<>();
            for (JsonElement e : JsonParser.parseString(t).getAsJsonArray()) list.add(e.getAsString());
            return list.isEmpty() ? null : list;
        }
        return List.of(t.split(","));
    }

    /** Thrown by {@link #doApiRequest} to reply with a specific error code. */
    static class ApiException extends Exception {
        final int code;
        ApiException(int code, String message) {
            super(message);
            this.code = code;
        }
    }

    /**
     * Returned by {@link #doApiRequest} to put got / total / last next to data at the top level
     * of the reply, the way FCDSL replies carry them. data stays exactly what it was.
     */
    record Paged(Object data, Long got, Long total, List<String> last) {}

    /**
     * Subclasses implement this to handle the specific API request.
     */
    protected abstract Object doApiRequest(Map<String, String> params) throws Exception;

    private void writeError(HttpServletResponse response, int code, String message, String clientPubkey) {
        Map<String, Object> respMap = new HashMap<>();
        respMap.put("code", code);
        respMap.put("message", message);
        String respJson = gson.toJson(respMap);

        if (clientPubkey != null) {
            writeEncryptedResponse(response, respJson, clientPubkey);
        } else {
            writePlainResponse(response, respJson);
        }
    }

    private void writeEncryptedResponse(HttpServletResponse response, String respJson, String clientPubkey) {
        if (clientPubkey == null) {
            writePlainResponse(response, respJson);
            return;
        }

        try {
            byte[] prikey = settings.decryptPrikey();
            Encryptor encryptor = new Encryptor(AlgorithmId.FC_EccK1AesGcm256_No1_NrC7);
            CryptoDataByte result = encryptor.encryptByAsyTwoWay(
                    respJson.getBytes(), prikey, Hex.fromHex(clientPubkey));

            if (result != null && result.getCode() == 0) {
                String encryptedJson = result.toJson();
                writePlainResponse(response, encryptedJson);
            } else {
                writePlainResponse(response, respJson);
            }
        } catch (Exception e) {
            writePlainResponse(response, respJson);
        }
    }

    private void writePlainResponse(HttpServletResponse response, String json) {
        try {
            response.getWriter().write(json);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
