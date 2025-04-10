package clients;

import apip.apipData.Fcdsl;
import configure.ApiAccount;
import configure.ApiProvider;
import crypto.Encryptor;
import server.ApipApiNames;
import constants.Constants;
import constants.CodeMessage;
import crypto.Hash;
import fcData.DiskItem;
import fcData.Hat;
import handlers.DiskHandler;
import handlers.HatHandler;
import server.DiskApiNames;
import utils.ObjectUtils;
import utils.http.AuthType;
import utils.http.RequestMethod;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static server.ApipApiNames.*;
import static constants.FieldNames.*;
import static utils.ObjectUtils.objectToList;

public class DiskClient extends Client {
    public static final String[] freeAPIs = new String[]{
            "https://apip.cash/DISK",
            "https://help.cash/DISK",
            "http://127.0.0.1:8080/DISK",
            "http://127.0.0.1:8081/DISK"
    };
    public DiskClient(ApiProvider apiProvider, ApiAccount apiAccount, byte[] symKey, ApipClient apipClient) {
        super(apiProvider, apiAccount, symKey, apipClient);
    }

    public String get(RequestMethod method, AuthType authType, String did, String localPath) {
        localPath = checkLocalPath(localPath);
        if (localPath == null) return null;
        Fcdsl fcdsl = new Fcdsl();
        Map<String,String> paramMap= new HashMap<>();
        paramMap.put(DID,did);
        fcdsl.setOther(paramMap);
        Object data = requestFile(ApipApiNames.VERSION_1, DiskApiNames.GET, fcdsl, did, localPath, authType, sessionKey, method);
        return (String)data;
    }

    public String check(String did) {
        Map<String,String> urlParamMap= new HashMap<>();
        urlParamMap.put(DID,did);
        Object data  = requestJsonByUrlParams( ApipApiNames.VERSION_1, DiskApiNames.CHECK, urlParamMap,null);
        return String.valueOf(data);
    }

    public List<DiskItem> list(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(VERSION_1, DiskApiNames.LIST,fcdsl, authType,sessionKey, requestMethod);
        return objectToList(data, DiskItem.class);
    }
    public List<DiskItem> list(RequestMethod method, AuthType authType, int size, String sort, String order, String[] last) {
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
        Object data = requestJsonByFile(ApipApiNames.VERSION_1, DiskApiNames.PUT,null,sessionKey,fileName);
        return replyPut(fileName, data);
    }
    public String carve(String fileName) {
        Object data = requestJsonByFile( ApipApiNames.VERSION_1, Carve,null,sessionKey,fileName);
        return replyPut(fileName, data);
    }

    @Nullable
    private String replyPut(String fileName, Object data) {
        if(sessionFreshen) data = checkResult();
        if(data ==null) return null;
        Map<String, String> stringStringMap = ObjectUtils.objectToMap(data, String.class, String.class);
        if(stringStringMap==null || stringStringMap.size()==0){
            apipClientEvent.setCode(CodeMessage.Code1020OtherError);
            apipClientEvent.setMessage("Failed to get the DID.");
            return null;
        }
        String respondDid = stringStringMap.get(DID);//DataGetter.getStringMap(data).get(DID);
        String localDid;
        try {
            localDid = Hash.sha256x2(new File(fileName));
        } catch (IOException e) {
            apipClientEvent.setCode(CodeMessage.Code1020OtherError);
            apipClientEvent.setMessage("Failed to hash local file.");
            return null;
        }
        if(localDid.equals(respondDid)) return respondDid;
        else {
            apipClientEvent.setCode(CodeMessage.Code1020OtherError);
            apipClientEvent.setMessage("Wrong DID."+"\nLocal DID:"+localDid+"\nrespond DID:"+respondDid);
            return null;
        }
    }

    public static byte[] getData(String did, DiskClient diskClient, DiskHandler diskHandler, HatHandler hatHandler) {
        if (did == null) return null;
        
        // 1. Try getting data from diskHandler first
        try {
            byte[] data = diskHandler.getBytes(did);
            if (data != null) {
                log.debug("Got data from diskHandler with DID: {}", did);
                return data;
            }
        } catch (Exception e) {
            log.debug("Failed to get data from diskHandler: {}", e.getMessage());
        }

        // 2. Try getting data from diskClient
        try {
            String tempPath = System.getProperty("java.io.tmpdir");
            String gotFileId = diskClient.get(RequestMethod.POST, AuthType.FC_SIGN_BODY, did, tempPath);
            if (gotFileId != null) {
                byte[] data = Files.readAllBytes(Path.of(tempPath, gotFileId));
                log.debug("Got data from diskClient with DID: {}", did);
                return data;
            }
        } catch (Exception e) {
            log.debug("Failed to get data from diskClient: {}", e.getMessage());
        }

        // 3. Try getting rawDid and fetch original data
        if (hatHandler != null) {
            String rawDid = hatHandler.getRawDidForCipher(did);
            if (rawDid != null) {
                // Try diskHandler with rawDid
                try {
                    byte[] data = diskHandler.getBytes(rawDid);
                    if (data != null) {
                        log.debug("Got data from diskHandler using rawDid: {}", rawDid);
                        return data;
                    }
                } catch (Exception e) {
                    log.debug("Failed to get data from diskHandler using rawDid: {}", e.getMessage());
                }

                // Try diskClient with rawDid
                try {
                    String tempPath = System.getProperty("java.io.tmpdir");
                    String gotFileId = diskClient.get(RequestMethod.POST, AuthType.FC_SIGN_BODY, rawDid, tempPath);
                    if (gotFileId != null) {
                        byte[] data = Files.readAllBytes(Path.of(tempPath, gotFileId));
                        log.debug("Got data from diskClient using rawDid: {}", rawDid);
                        return data;
                    }
                } catch (Exception e) {
                    log.debug("Failed to get data from diskClient using rawDid: {}", e.getMessage());
                }
            }
        }

        log.error("Failed to get data with DID: {} from all available sources", did);
        return null;
    }

    public static String encryptAndSaveData(String dataFilePath, DiskClient diskClient, byte[] dataBytes, HatHandler hatHandler) {
        try {
            // 1. Encrypt the data
            String encryptedFilePath = Encryptor.encryptFile(dataFilePath, diskClient.getApiAccount().getUserPubKey());
            if (encryptedFilePath == null) {
                throw new Exception("Failed to encrypt data");
            }

            // 2. Save encrypted data using diskClient
            String encryptedDid = diskClient.put(encryptedFilePath);
            if (encryptedDid == null) {
                throw new Exception("Failed to save encrypted data to disk client");
            }

            // 3. Create and save Hat
            Hat hat = new Hat();
            hat.setId(encryptedDid);
            hat.setRawDid(dataFilePath.substring(dataFilePath.lastIndexOf("/") + 1));
            hat.setSize((long) dataBytes.length);
            hat.setBorn(System.currentTimeMillis());
            hat.setState(Hat.DataState.ACTIVE);
            hat.setPubKeyB(diskClient.getApiAccount().getUserPubKey());
            
            if (hatHandler != null) {
                hatHandler.putHat(hat);
            }

            return encryptedDid;
        } catch (Exception e) {
            log.error("Error in encryption process: {}", e.getMessage());
            return null;
        }
    }

}
