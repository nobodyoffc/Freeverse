package handlers;

import crypto.Hash;
import fcData.FcObject;
import utils.IdNameUtils;
import utils.FileUtils;
import utils.Hex;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

import appTools.Settings;

public class DiskHandler extends Handler<FcObject> {
    private static final Logger log = Logger.getLogger(DiskHandler.class.getName());
    private final String storageDir;

    public DiskHandler(String fid,String oid) {
        this.storageDir = IdNameUtils.makeKeyName(fid, oid, "DISK", true);
    }
    public DiskHandler(Settings settings){
        this.storageDir = IdNameUtils.makeKeyName(settings.getMainFid(), settings.getSid(), "DISK", true);
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
            if (Boolean.TRUE.equals(FileUtils.checkFileOfFreeDisk(fullPath, did))) {
                return fullPath;
            }
        }

        if (FileUtils.createFileDirectories(fullPath)) {
            try {
                Files.write(file.toPath(), bytes);
                return fullPath;
            } catch (IOException e) {
                log.warning("Failed to write file: " + e.getMessage());
                return null;
            }
        }
        return null;
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
                log.warning("File content hash mismatch");
                return null;
            }
        } catch (IOException e) {
            log.warning("Failed to read file: " + e.getMessage());
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
            log.warning("Failed to delete file: " + e.getMessage());
            return false;
        }
    }
} 