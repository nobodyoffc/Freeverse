package clients.fcspClient;

import apip.apipData.Fcdsl;
import clients.Client;
import clients.apipClient.ApipClient;
import configure.ApiAccount;
import configure.ApiProvider;
import constants.ApiNames;
import constants.Constants;
import constants.ReplyCodeMessage;
import crypto.*;
import crypto.Hash;
import javaTools.BytesTools;
import javaTools.FileTools;
import javaTools.Hex;
import javaTools.ObjectTools;
import javaTools.http.AuthType;
import javaTools.http.HttpRequestMethod;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static constants.ApiNames.*;
import static constants.FieldNames.*;
import static fcData.AlgorithmId.FC_EccK1AesCbc256_No1_NrC7;
import static javaTools.ObjectTools.objectToList;

public class DiskClient extends Client {

    public DiskClient(ApiProvider apiProvider, ApiAccount apiAccount, byte[] symKey, ApipClient apipClient) {
        super(apiProvider, apiAccount, symKey, apipClient);
    }

    public static String encryptFile(String fileName, String pubKeyHex) {

        byte[] pubKey = Hex.fromHex(pubKeyHex);
        Encryptor encryptor = new Encryptor(FC_EccK1AesCbc256_No1_NrC7);
        String tempFileName = FileTools.getTempFileName();
        CryptoDataByte result1 = encryptor.encryptFileByAsyOneWay(fileName, tempFileName, pubKey);
        if(result1.getCode()!=0)return null;
        String cipherFileName;
        try {
            cipherFileName = Hash.sha256x2(new File(tempFileName));
            Files.move(Paths.get(tempFileName),Paths.get(cipherFileName));
        } catch (IOException e) {
            return null;
        }
        return cipherFileName;
    }

    @Nullable
    public static String decryptFile(String path, String gotFile,byte[]symKey,String priKeyCipher) {
        CryptoDataByte cryptoDataByte = new Decryptor().decryptJsonBySymKey(priKeyCipher,symKey);
        if(cryptoDataByte.getCode()!=0){
            log.debug("Failed to decrypt the user priKey.");
            log.debug(cryptoDataByte.getMessage());
            return null;
        }
        byte[] priKey = cryptoDataByte.getData();
        CryptoDataByte cryptoDataByte1 = new Decryptor().decryptFileToDidByAsyOneWay(path, gotFile, path, priKey);
        if(cryptoDataByte1.getCode()!=0){
            log.debug("Failed to decrypt file "+ Path.of(path, gotFile));
            return null;
        }
        BytesTools.clearByteArray(priKey);
        return Hex.toHex(cryptoDataByte1.getDid());
    }

    public String get(HttpRequestMethod method, AuthType authType, String did, String localPath) {
        localPath = checkLocalPath(localPath);
        if (localPath == null) return null;
        Fcdsl fcdsl = new Fcdsl();
        Map<String,String> paramMap= new HashMap<>();
        paramMap.put(DID,did);
        fcdsl.setOther(paramMap);
        Object data = requestFile(ApiNames.Version1, ApiNames.Get, fcdsl, did, localPath, authType, sessionKey, method);
        return (String)data;
    }

    public String check(String did) {
        Map<String,String> urlParamMap= new HashMap<>();
        urlParamMap.put(DID,did);
        Object data  = requestJsonByUrlParams( ApiNames.Version1,ApiNames.Check, urlParamMap,null);
        return String.valueOf(data);
    }

    public List<DiskItem> list(Fcdsl fcdsl, HttpRequestMethod httpRequestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(Version1, LIST,fcdsl, authType,sessionKey, httpRequestMethod);
        return objectToList(data, DiskItem.class);
    }
    public List<DiskItem> list(HttpRequestMethod method, AuthType authType, int size, String sort, String order, String[] last) {
        Fcdsl fcdsl = new Fcdsl();
        if(size!=0)fcdsl.addSize(size);
        if(sort!=null)fcdsl.addSort(sort,order);
        if(last!=null && last.length>0)fcdsl.addAfter(List.of(last));

        return list(fcdsl,method,authType);
    }


    @Nullable
    private static String checkLocalPath(String localPath) {
        if(localPath ==null) localPath =System.getProperty(Constants.UserDir);
        Path path = Paths.get(localPath);
        if(Files.notExists(path)) {
            try {
                Files.createDirectories(path);
            } catch (IOException e) {
                log.debug("Failed to create path:" + localPath);
                return null;
            }
        }
        return localPath;
    }

    public String put(String fileName) {
        Object data = requestJsonByFile(ApiNames.Version1,ApiNames.Put,null,sessionKey,fileName);
        return replyPut(fileName, data);
    }
    public String carve(String fileName) {
        Object data = requestJsonByFile( ApiNames.Version1, Carve,null,sessionKey,fileName);
        return replyPut(fileName, data);
    }

    @Nullable
    private String replyPut(String fileName, Object data) {
        if(sessionFreshen) data = checkResult();
        if(data ==null) return null;
        Map<String, String> stringStringMap = ObjectTools.objectToMap(data, String.class, String.class);
        if(stringStringMap==null || stringStringMap.size()==0){
            fcClientEvent.setCode(ReplyCodeMessage.Code1020OtherError);
            fcClientEvent.setMessage("Failed to get the DID.");
            return null;
        }
        String respondDid = stringStringMap.get(DID);//DataGetter.getStringMap(data).get(DID);
        String localDid;
        try {
            localDid = Hash.sha256x2(new File(fileName));
        } catch (IOException e) {
            fcClientEvent.setCode(ReplyCodeMessage.Code1020OtherError);
            fcClientEvent.setMessage("Failed to hash local file.");
            return null;
        }
        if(localDid.equals(respondDid)) return respondDid;
        else {
            fcClientEvent.setCode(ReplyCodeMessage.Code1020OtherError);
            fcClientEvent.setMessage("Wrong DID."+"\nLocal DID:"+localDid+"\nrespond DID:"+respondDid);
            return null;
        }
    }
}
