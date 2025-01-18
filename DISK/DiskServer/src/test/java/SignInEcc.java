import constants.FieldNames;
import fcData.FcSession;
import apip.apipData.RequestBody;
import tools.RedisTools;
import constants.ApiNames;
import constants.CodeMessage;
import crypto.CryptoDataByte;
import crypto.Encryptor;
import crypto.old.EccAes256K1P7;
import fcData.AlgorithmId;
import initial.Initiator;
import tools.Hex;
import fcData.FcReplierHttp;
import redis.clients.jedis.Jedis;
import server.RequestCheckResult;
import server.RequestChecker;
import appTools.Settings;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

import static constants.Strings.*;

@WebServlet(name = ApiNames.SignInEcc, value = "/"+ApiNames.Version2 +"/"+ApiNames.SignInEcc)
public class SignInEcc extends HttpServlet {

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        FcReplierHttp replier = new FcReplierHttp(Initiator.sid,response);

        FcSession fcSession = new FcSession();
        RequestChecker requestChecker = new RequestChecker();
        String pubKey;
        try (Jedis jedis = Initiator.jedisPool.getResource()) {
            Map<String, String> paramsMap = jedis.hgetAll(Settings.addSidBriefToName(Initiator.sid, PARAMS));
            long windowTime = RedisTools.readHashLong(jedis, Settings.addSidBriefToName(Initiator.sid,SETTINGS), WINDOW_TIME);

            RequestCheckResult requestCheckResult = requestChecker.checkSignInRequest(Initiator.sid, request, replier, paramsMap, windowTime, jedis, Initiator.sessionHandler);
            if (requestCheckResult == null) {
                return;
            }
            pubKey = requestCheckResult.getPubKey();
            String fid = requestCheckResult.getFid();
            fcSession.setId(fid);

            RequestBody.SignInMode mode = requestCheckResult.getRequestBody().getMode();

            if ((!jedis.hexists(Settings.addSidBriefToName(Initiator.sid,FieldNames.ID_SESSION_NAME), fid)) || RequestBody.SignInMode.REFRESH.equals(mode)) {
                try {
                    fcSession = FcSession.makeSession(Initiator.sid,jedis, fid);
                    String sessionKeyCipher = EccAes256K1P7.encryptWithPubKey(fcSession.getKey().getBytes(), Hex.fromHex(pubKey));
                    fcSession.setKeyCipher(sessionKeyCipher);
                    fcSession.setKey(null);
                } catch (Exception e) {
                    replier.replyOtherErrorHttp("Some thing wrong when making sessionKey.\n" + e.getMessage(),null,jedis );
                }
            } else {
                jedis.select(0);
                String sessionName = jedis.hget(Settings.addSidBriefToName(Initiator.sid,FieldNames.ID_SESSION_NAME), fid);
                fcSession.setName(sessionName);
                jedis.select(1);
                String sessionKey = jedis.hget(sessionName, FieldNames.SESSION_KEY);
                if (sessionKey != null) {
                    Encryptor encryptor = new Encryptor(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7);
                    CryptoDataByte cryptoDataByte = encryptor.encryptByAsyOneWay(sessionKey.getBytes(), Hex.fromHex(pubKey));
                    if(cryptoDataByte==null || cryptoDataByte.getCode()!=0){
                        String msg=null;
                        if(cryptoDataByte!=null)msg=cryptoDataByte.getMessage();
                        replier.replyOtherErrorHttp("Failed to encrypt sessionKey."+msg,null,jedis);
                        return;
                    }
                    String sessionKeyCipher = cryptoDataByte.toJson();//EccAes256K1P7.encryptWithPubKey(sessionKey.getBytes(), Hex.fromHex(pubKey));
                    fcSession.setKeyCipher(sessionKeyCipher);
                } else {
                    try {
                        fcSession = FcSession.makeSession(Initiator.sid,jedis, fid);
                    } catch (Exception e) {
                        replier.replyOtherErrorHttp( "Some thing wrong when making sessionKey.\n" + e.getMessage(),null ,jedis);
                        return;
                    }
                }
            }
            replier.reply0SuccessHttp(fcSession,jedis, null);
            replier.clean();
        }
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        FcReplierHttp replier =new FcReplierHttp(Initiator.sid,response);
        replier.setCode(CodeMessage.Code1017MethodNotAvailable);
        replier.setMessage(CodeMessage.Msg1017MethodNotAvailable);
        response.getWriter().write(replier.toNiceJson());
    }
}
