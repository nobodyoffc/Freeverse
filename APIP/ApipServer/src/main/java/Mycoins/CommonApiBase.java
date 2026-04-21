package Mycoins;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
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
            params = gson.fromJson(decryptedJson, new TypeToken<Map<String, String>>(){}.getType());
        } catch (Exception e) {
            writeError(response, 1013, "Failed to parse request params: " + e.getMessage(), clientPubkey);
            return;
        }

        if (params == null) params = new HashMap<>();

        // Step 5: Call the specific API logic
        Object result;
        try {
            result = doApiRequest(params);
        } catch (Exception e) {
            writeError(response, 1020, e.getMessage(), clientPubkey);
            return;
        }

        // Step 6: Build success response
        Map<String, Object> respMap = new HashMap<>();
        respMap.put("code", 0);
        respMap.put("message", "Success");
        respMap.put("data", result);

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
