package APIP17V1_Crypto;

import apip.apipData.EncryptIn;
import co.elastic.clients.elasticsearch.core.GetResponse;
import com.google.gson.Gson;
import constants.ApiNames;
import constants.FieldNames;
import constants.IndicesNames;
import crypto.CryptoDataByte;
import crypto.Encryptor;
import fcData.FcReplier;
import fch.fchData.Address;
import initial.Initiator;
import javaTools.Hex;
import javaTools.http.AuthType;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.RequestChecker;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;


@WebServlet(name = ApiNames.Encrypt, value = "/"+ApiNames.SN_17+"/"+ApiNames.Version1 +"/"+ApiNames.Encrypt)
public class Encrypt extends HttpServlet {
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        AuthType authType = AuthType.FC_SIGN_URL;
        doRequest(Initiator.sid,request, response, authType,Initiator.jedisPool);
    }
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doRequest(Initiator.sid,request, response, authType,Initiator.jedisPool);
    }

    protected void doRequest(String sid, HttpServletRequest request, HttpServletResponse response, AuthType authType, JedisPool jedisPool) {
        FcReplier replier = new FcReplier(sid,response);
        try(Jedis jedis = jedisPool.getResource()) {
            //Do FCDSL other request
            Map<String, String> other = RequestChecker.checkOtherRequest(sid, request, authType, replier, jedis);
            if (other == null) return;
            //Do this request
            EncryptIn encryptInput;
            try {
                Gson gson = new Gson();
                String otherJson = other.get(FieldNames.ENCRYPT_INPUT);
                encryptInput = gson.fromJson(otherJson,EncryptIn.class);
            }catch (Exception e){
                replier.replyOtherError("Can't get parameters correctly from Json string.",e.getMessage(),jedis);
                return;
            }

            Encryptor encryptor = new Encryptor();
            encryptor.setAlgorithmType(encryptInput.getAlg());
            CryptoDataByte cryptoDataByte = null;
            String cipher;

            try {
                switch (encryptInput.getType()) {
                    case SymKey -> cryptoDataByte = encryptor.encryptBySymKey(encryptInput.getMsg().getBytes(), Hex.fromHex(encryptInput.getSymKey()));
                    case Password ->
                            cryptoDataByte = encryptor.encryptByPassword(encryptInput.getMsg().getBytes(), encryptInput.getPassword().toCharArray());
                    case AsyOneWay -> {
                        if(encryptInput.getPubKey()!=null)
                            cryptoDataByte = encryptor.encryptByAsyOneWay(encryptInput.getMsg().getBytes(), Hex.fromHex(encryptInput.getPubKey()));
                        else if(encryptInput.getFid()!=null){
                            GetResponse<Address> result = Initiator.esClient.get(g -> g.index(IndicesNames.ADDRESS).id(encryptInput.getFid()), Address.class);
                            Address address = result.source();
                            if(address==null||address.getPubKey()==null){
                                replier.replyOtherError("Failed to get pubkey.",null,jedis);
                                return;
                            }
                            cryptoDataByte = encryptor.encryptByAsyOneWay(encryptInput.getMsg().getBytes(), Hex.fromHex(address.getPubKey()));
                        }
                    }
                }
            }catch (Exception e){
                replier.replyOtherError("Failed to encrypt. Check the parameters.",encryptInput,jedis);
                return;
            }

            if(cryptoDataByte==null || cryptoDataByte.getCode()!=0){
                replier.replyOtherError("Can't get parameters correctly from Json string.",cryptoDataByte.getMessage(),jedis);
                return;
            }
            cipher = cryptoDataByte.toNiceJson();
            replier.replySingleDataSuccess(cipher,jedis);
        }
    }
}