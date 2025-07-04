package handlers;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import constants.Constants;
import constants.FieldNames;
import core.crypto.Hash;
import data.fcData.DiskItem;
import data.fcData.FcObject;
import data.fcData.Hat;
import data.feipData.Service;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.IdNameUtils;
import utils.FileUtils;
import utils.Hex;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Date;

import config.Settings;

import static constants.Strings.DATA;

public class DiskManager extends Manager<FcObject> {
    public final static Logger log = LoggerFactory.getLogger(DiskManager.class);
    public final static String TAG = "DISK";
    private final String storageDir;

    public DiskManager(String fid, String oid) {
        this.storageDir = IdNameUtils.makeKeyName(fid, oid, TAG, true);
    }
    public DiskManager(Settings settings){
        super(settings, ManagerType.DISK);
        this.storageDir = makeUserDir(settings.getMainFid(), settings.getSid(),TAG,(String) settings.getSettingMap().get(FieldNames.DISK_DIR));
    }

    @NotNull
    public static String makeUserDir(String fid, String sid, String appName, String fatherDir) {
        if(fatherDir==null)
            fatherDir = System.getProperty(Constants.UserHome);
        String myDataDir = IdNameUtils.makeKeyName(fid, sid, appName, true);
        return Paths.get(fatherDir, myDataDir).toString();
    }

//    public static String getDiskDataDir(Settings settings) {
//        return IdNameUtils.makeKeyName(settings.getMainFid(), settings.getSid(), "DISK", true);
//    }


    public static String getSubPathForDisk(String did) {
        return "/"+did.substring(0,2)+"/"+did.substring(2,4)+"/"+did.substring(4,6)+"/"+did.substring(6,8);
    }

    public Boolean checkFileOfDisk(String did) {
        String path = getDataPath(did);
        File file = new File(path);
        if(!file.exists())return false;

        String existDid;
        try {
            existDid = Hash.sha256x2(file);
        } catch (IOException e) {
            System.out.println("Failed to make sha256 of file "+did);
            return null;
        }

        return did.equals(existDid);
    }

    /**
     * Save byte array as a file using SHA256x2 hash as filename
     * @param bytes data to save
     * @return DID (SHA256x2 hash) of the saved file, or null if operation fails
     */
    public String put(byte[] bytes) {
        if (bytes == null) return null;
        String did = Hex.toHex(Hash.sha256x2(bytes));
        String fullPath = getDataPath(did);

        File file = new File(fullPath);
        if (file.exists()) {
            if (Boolean.TRUE.equals(checkFileOfDisk(did))) {
                return fullPath;
            }
        }

        if (FileUtils.createFileDirectories(fullPath)) {
            try {
                Files.write(file.toPath(), bytes);
                return fullPath;
            } catch (IOException e) {
                log.error("Failed to write file: " + e.getMessage());
                return null;
            }
        }
        return null;
    }

    @NotNull
    public Hat put(InputStream inputStream){
        Hat hat = new Hat();

        String tempFileName = FileUtils.getTempFileName();
        try (OutputStream outputStream = new FileOutputStream(tempFileName)) {

            HashFunction hashFunction = Hashing.sha256();
            Hasher hasher = hashFunction.newHasher();
            // Adjust buffer size as per your requirement
            byte[] buffer = new byte[8192];
            int bytesRead;
            long bytesLength = 0;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                // Write the bytes read from the request input stream to the output stream
                outputStream.write(buffer, 0, bytesRead);
                hasher.putBytes(buffer, 0, bytesRead);
                bytesLength +=bytesRead;
            }

            String did = Hex.toHex(Hash.sha256(hasher.hash().asBytes()));
            String path = getDataPath( did);

            hat.setId(did);
            hat.setLocas(new ArrayList<>());
            hat.getLocas().add(path);

            File file = new File(path);
            if(!file.exists() || Boolean.FALSE.equals(checkFileOfDisk( did))) {
                try {
                    Path source = Paths.get(tempFileName);
                    Path target = Paths.get(path);
                    Files.createDirectories(target.getParent());
                    Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    System.err.println("Error moving file: " + e.getMessage());
                }
            }
            hat.setSize(bytesLength);
        } catch (IOException e) {
            log.error("Failed to save file with a inputStream.");
        }
        return hat;
    }

    /**
     * Read file content as byte array
     * @param did SHA256x2 hash of the file to read
     * @return byte array of file content, or null if file doesn't exist or operation fails
     */
    public byte[] getBytes(String did) {
        if (did == null) return null;

        String subDir = getSubPathForDisk(did);
        Path filePath = Paths.get(storageDir + subDir, did);
        
        if (!Files.exists(filePath)) {
            return null;
        }

        try {
            byte[] bytes = Files.readAllBytes(filePath);
            String checkDid = Hex.toHex(Hash.sha256x2(bytes));
            if (did.equals(checkDid)) {
                return bytes;
            } else {
                log.error("File content hash mismatch");
                return null;
            }
        } catch (IOException e) {
            log.error("Failed to read file: " + e.getMessage());
            return null;
        }
    }

    /**
     * Delete file by its DID
     * @param did SHA256x2 hash of the file to delete
     * @return true if deletion successful, false otherwise
     */
    public boolean delete(String did) {
        if (did == null) return false;

        String subDir = getSubPathForDisk(did);
        Path filePath = Paths.get(storageDir + subDir, did);
        
        try {
            return Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.error("Failed to delete file: " + e.getMessage());
            return false;
        }
    }

    public void deleteExpiredFiles() {
        Date date = new Date();
        SearchResponse<DiskItem> result;
        try {
            ElasticsearchClient esClient = (ElasticsearchClient)settings.getClient(Service.ServiceType.ES);
            if(esClient==null)return;
            result = esClient.search(s -> s.index(Settings.addSidBriefToName(settings.getSid(),DATA)).query(q -> q.range(r -> r.field(FieldNames.EXPIRE).lt(JsonData.of(date)))), DiskItem.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if(result==null || result.hits().hits().isEmpty()) return;

        for(Hit<DiskItem> hit:result.hits().hits()){
            DiskItem source = hit.source();
            if(source==null)continue;
            String did = source.getDid();
            String subDir = getSubPathForDisk(did);
            String storageDir =IdNameUtils.makeKeyName(settings.getMainFid(), settings.getSid(), "DISK", true);
            File file = new File(settings.getSettingMap().get(storageDir)+subDir,did);
            if(file.exists())file.delete();
        }
    }

    /**
     * Gets the full path for a file identified by its DID (Data ID).
     * The path is constructed using the storage directory and a subdirectory structure
     * based on the DID's first 8 characters.
     *
     * @param did The Data ID (SHA256x2 hash) of the file
     * @return The full path to the file, or null if did is null or empty
     * @throws IllegalArgumentException if did is null or empty
     */
    public String getDataPath(String did) {
        if (did == null || did.trim().isEmpty()) {
            return null;
        }
        
        String subDir = getSubPathForDisk(did);
        return Paths.get(storageDir, subDir, did).toString();
    }

    public String getDataDir() {
        return this.storageDir;
    }
}