package APIP17V1_Crypto;

import constants.ApipApiNames;
import data.apipData.EncryptIn;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.GetResponse;
import com.google.gson.Gson;
import data.fchData.Freer;
import constants.FieldNames;
import constants.IndicesNames;
import core.crypto.CryptoDataByte;
import core.crypto.Encryptor;
import data.fcData.ReplyBody;
import data.feipData.ServiceType;
import initial.Initiator;
import server.HttpRequestChecker;
import utils.Hex;
import utils.http.AuthType;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

import config.Settings;

@WebServlet(name = ApipApiNames.ENCRYPT, value = "/"+ ApipApiNames.SN_17+"/"+ ApipApiNames.ENCRYPT +"/"+ ApipApiNames.VER_1)
public class Encrypt extends HttpServlet {
    private final Settings settings = Initiator.settings;
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        AuthType authType = AuthType.FREE;
        doRequest(request, response, authType,settings);
    }
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        AuthType authType = AuthType.ENCRYPTED;
        doRequest(request, response, authType,settings);
    }

    protected void doRequest(HttpServletRequest request, HttpServletResponse response, AuthType authType, Settings settings) {
        ReplyBody replier = new ReplyBody(settings);
            //Do FCDSL other request
            HttpRequestChecker httpRequestChecker = new HttpRequestChecker(settings, replier);
            Map<String, String> other = httpRequestChecker.checkOtherRequestHttp(request, response, authType);
            if (other == null) return;
            //Do this request
            EncryptIn encryptInput;
            try {
                Gson gson = new Gson();
                String otherJson = other.get(FieldNames.ENCRYPT_INPUT);
                encryptInput = gson.fromJson(otherJson,EncryptIn.class);
            }catch (Exception e){
                replier.replyOtherErrorHttp("Can't get parameters correctly from Json string.",e.getMessage(), response);
                return;
            }

            Encryptor encryptor = new Encryptor();
            encryptor.setAlgorithmType(encryptInput.getAlg());
            CryptoDataByte cryptoDataByte = null;
            String cipher;

            try {
                switch (encryptInput.getType()) {
                    case Symkey -> cryptoDataByte = encryptor.encryptBySymkey(encryptInput.getMsg().getBytes(), Hex.fromHex(encryptInput.getSymkey()));
                    case Password ->
                            cryptoDataByte = encryptor.encryptByPassword(encryptInput.getMsg().getBytes(), encryptInput.getPassword().toCharArray());
                    case AsyOneWay -> {
                        if(encryptInput.getPubkey()!=null)
                            cryptoDataByte = encryptor.encryptByAsyOneWay(encryptInput.getMsg().getBytes(), Hex.fromHex(encryptInput.getPubkey()));
                        else if(encryptInput.getFid()!=null){
                            ElasticsearchClient esClient = (ElasticsearchClient) settings.getClient(ServiceType.ES);
                            GetResponse<Freer> result = esClient.get(g -> g.index(IndicesNames.FREER).id(encryptInput.getFid()), Freer.class);
                            Freer cid = result.source();
                            if(cid ==null|| cid.getPubkey()==null){
                                replier.replyOtherErrorHttp("Failed to get pubkey.", response);
                                return;
                            }
                            cryptoDataByte = encryptor.encryptByAsyOneWay(encryptInput.getMsg().getBytes(), Hex.fromHex(cid.getPubkey()));
                        }
                    }
                    default -> new IllegalArgumentException("Unexpected value: " + encryptInput.getType()).printStackTrace();
                }
            }catch (Exception e){
                replier.replyOtherErrorHttp("Failed to encrypt. Check the parameters.",encryptInput, response);
                return;
            }

            if(cryptoDataByte==null){
                replier.replyOtherErrorHttp("Can't get parameters correctly from Json string.", response);
                return;
            }

            if(cryptoDataByte.getCode()!=0){
                replier.replyOtherErrorHttp("Can't get parameters correctly from Json string.",cryptoDataByte.getMessage(), response);
                return;
            }
            cipher = cryptoDataByte.toNiceJson();
            replier.replySingleDataSuccessHttp(cipher,response);
    }
}