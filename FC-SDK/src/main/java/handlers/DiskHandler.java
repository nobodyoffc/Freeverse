package handlers;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import constants.FieldNames;
import crypto.Hash;
import fcData.DiskItem;
import fcData.FcObject;
import fcData.Hat;
import feip.feipData.Service;
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

import appTools.Settings;

import static constants.Strings.DATA;
import static utils.FileUtils.checkFileOfDisk;
import static utils.FileUtils.getSubPathForDisk;

public class DiskHandler extends Handler<FcObject> {
    public final static Logger log = LoggerFactory.getLogger(DiskHandler.class);
    private final String storageDir;

    public DiskHandler(String fid,String oid) {
        this.storageDir = IdNameUtils.makeKeyName(fid, oid, "DISK", true);
    }
    public DiskHandler(Settings settings){
        super(settings,HandlerType.DISK);
        this.storageDir = IdNameUtils.makeKeyName(settings.getMainFid(), settings.getSid(), "DISK", true);
    }

    public static String getDiskDataDir(Settings settings) {
        return IdNameUtils.makeKeyName(settings.getMainFid(), settings.getSid(), "DISK", true);
    }

    /**
     * Save byte array as a file using SHA256x2 hash as filename
     * @param bytes data to save
     * @return DID (SHA256x2 hash) of the saved file, or null if operation fails
     */
    public String put(byte[] bytes) {
        if (bytes == null) return null;
        String fullPath =null;
        String did = Hex.toHex(Hash.sha256x2(bytes));
        String subDir = FileUtils.getSubPathForDisk(did);
        fullPath = storageDir + subDir;
        
        File file = new File(fullPath, did);
        if (file.exists()) {
            if (Boolean.TRUE.equals(FileUtils.checkFileOfDisk(fullPath, did))) {
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
        String diskDataDir = DiskHandler.getDiskDataDir(settings);
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
            String subDir = getSubPathForDisk(did);
            String path = diskDataDir +subDir;

            hat.setId(did);
            hat.setLocas(new ArrayList<>());
            hat.getLocas().add(path);

            File file = new File(path,did);
            if(!file.exists() || Boolean.FALSE.equals(checkFileOfDisk(path, did))) {
                try {
                    Path source = Paths.get(tempFileName);
                    Path target = Paths.get(path, did);
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

        String subDir = FileUtils.getSubPathForDisk(did);
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

        String subDir = FileUtils.getSubPathForDisk(did);
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
            String subDir = FileUtils.getSubPathForDisk(did);
            String storageDir =IdNameUtils.makeKeyName(settings.getMainFid(), settings.getSid(), "DISK", true);
            File file = new File(settings.getSettingMap().get(storageDir)+subDir,did);
            if(file.exists())file.delete();
        }
    }

    public String getDataDir() {
        return this.storageDir;
    }
}