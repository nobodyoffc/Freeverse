package utils;

import crypto.Hash;

import feip.feipData.Service;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;


import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class FileUtils {
    public static String makeName(String fid, String oid, String name,Boolean trueForLowerCaseAndFalseForUpperCase){
        return IdNameUtils.makeKeyName(fid, oid, name, trueForLowerCaseAndFalseForUpperCase);
    }
    public static File getAvailableFile(BufferedReader br) {
        String input;
        while (true) {
            System.out.println("Input the full path.'s' to skip:");
            try {
                input = br.readLine();
            } catch (IOException e) {
                System.out.println("BufferedReader wrong:" + e.getMessage());
                return null;
            }

            if ("s".equals(input)) return null;

            File file = new File(input);
            if (!file.exists()) {
                System.out.println("\nPath doesn't exist. Input again.");
            } else {
                return file;
            }
        }
    }
    public static byte[] readAllBytes(String filename) {
        try {
            Path path = Paths.get(filename);
            if (!Files.exists(path)) {
                return null;
            }
            return Files.readAllBytes(path);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
    public static File getNewFile(String filePath, String fileName, CreateNewFileMode createNewFileMode) {
        File file = new File(filePath, fileName);

        int i=1;

        String fileNameHead = getFileNameHead(fileName);
        String fileNameTail = getFileNameTail(fileName,true);

        if(file.exists()){
            switch (createNewFileMode){
                case ADD_1 -> {
                    while (file.exists()){
                        String newFileName = fileNameHead+"_"+i+fileNameTail;
                        i++;
                        file = new File(filePath,newFileName);
                    }
                }
                case REWRITE -> {
                    System.out.println("File "+file.getName()+" existed. It will be covered.");
                    file.delete();
                }
                case RETURN_NULL -> {
                    System.out.println("File "+file.getName()+" existed.");
                    return null;
                }
                case THROW_EXCEPTION -> throw new RuntimeException("File "+file.getName()+" existed.");
            }
        }
        try {
            if (file.createNewFile()) {
                System.out.println("File "+file.getName()+" created.");
                return file;
            } else {
                System.out.println("Create new file " + fileName + " failed.");
                return null;
            }
        } catch (IOException e) {
            System.out.println("Create new file " + fileName + " wrong:" + e.getMessage());
            return null;
        }
    }

    public static String writeBytesToDisk(byte[] bytes, String storageDir) {
        if(storageDir==null)storageDir=System.getProperty("user.home");
        String did = Hex.toHex(Hash.sha256x2(bytes));
        String subDir = getSubPathForDisk(did);
        String path = storageDir+subDir;

        File file = new File(path,did);
        if(!file.exists()) {
            try {
                boolean done = createFileWithDirectories(path+"/"+did);
                if(!done)return null;
                try (OutputStream outputStream = new FileOutputStream(file)) {
                    outputStream.write(bytes);
                    return did;
                }
            } catch (IOException e) {
                return null;
            }
        }else if(Boolean.TRUE.equals(checkFileOfDisk(path, did)))return did;
        else return null;
    }

    public static boolean createFileWithDirectories(String filePathString) {
        Path path = Paths.get(filePathString);
        try {
            // Create parent directories if they do not exist
            Path pathParent = path.getParent();
            if(pathParent!=null && Files.notExists(pathParent)) {
                Files.createDirectories(pathParent);
            }

            // Create file if it does not exist
            if (Files.notExists(path)) {
                Files.createFile(path);
                return true;
            } else {
                return true;
            }
        } catch (IOException e) {
            System.err.println("Error creating file or directories: " + e.getMessage());
            return false;
        }
    }

    public static boolean createFileDirectories(String filePathString) {
        Path path = Paths.get(filePathString);
        try {
            // Create parent directories if they do not exist
            if (path.getParent()!=null && Files.notExists(path.getParent())) {
                Files.createDirectories(path.getParent());
            }
            return true;
        } catch (IOException e) {
            System.err.println("Error creating file or directories: " + e.getMessage());
            return false;
        }
    }

    public static String getSubPathForDisk(String did) {
        return "/"+did.substring(0,2)+"/"+did.substring(2,4)+"/"+did.substring(4,6)+"/"+did.substring(6,8);
    }

    public static Boolean checkFileOfDisk(String path, String did) {
        File file = new File(path,did);
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

    @NotNull
    public static String getTempFileName() {
        return Hex.toHex(BytesUtils.getRandomBytes(4));
    }

    public static String makeFileName(String fid, String oid, String name, String dotAndSuffix){
        StringBuilder sb =new StringBuilder();

        if(fid!=null){
            sb.append(fid);
//            if(fid.length()>6)sb.append(fid, fid.length()-6,fid.length());
//            else sb.append(fid);
        }

        if(oid!=null){
            oid = oid.replaceAll(" ", "_");
            if(fid!=null)sb.append("_");
            sb.append(oid);
//            if(Hex.isHexString(oid) && oid.length()>6)sb.append(oid, 0, 6);
//            else sb.append(oid);
        }

        if(name!=null)sb.append("_").append(name);

        if(dotAndSuffix!=null)sb.append(dotAndSuffix);
        return sb.toString();
    }

    public static String getUserDir() {
        return System.getProperty("user.dir");
    }

    public static String getHomeDir() {
        return System.getProperty("user.home");
    }

    public static String makeServerDataDir(String sid, Service.ServiceType serviceType) {
        return getHomeDir()+"/data/"+sid+"/"+serviceType.toString();
    }

    public static boolean checkDirOrMakeIt(String path) {
        File directory = new File(path);
        if (!directory.exists()) {
            boolean done = directory.mkdirs();
            if(!done){
                System.out.println("Failed to create path:"+path);
                return false;
            }
        }
        return true;
    }

    public static void removeAllBackUps(String rootFileName, String backupDir) {
        if(backupDir == null) {
            backupDir = System.getProperty("user.home") + "/backup";
        }

        if(!backupDir.endsWith("/")) {
            backupDir += "/";
        }

        File backupDirectory = new File(backupDir);
        if(!backupDirectory.exists()) {
            return;
        }

        // Delete all backup files with pattern rootFileName{number}.bak
        File[] files = backupDirectory.listFiles((dir, name) -> 
            name.startsWith(rootFileName) && name.endsWith(".bak"));

        if(files != null) {
            for(File file : files) {
                boolean deleted = file.delete();
                if(!deleted) {
                    System.out.println("Failed to delete backup file: " + file.getName());
                }
            }
        }
    }

    public static void backup(String rootFileName, @javax.annotation.Nullable String backupDir,int copiesNumber) {
        File sourceFile = new File(rootFileName);

        // If source file doesn't exist, nothing to backup
        if (!sourceFile.exists()) {
            System.out.println("No file to backup");
            return;
        }

        if(backupDir==null)backupDir=System.getProperty("user.home")+"/backup";

        // Create backup directory if it doesn't exist
        File backupDirectory = new File(backupDir);
        if (!backupDirectory.exists()) {
            boolean created = backupDirectory.mkdirs();
            if (!created) {
                System.out.println("Failed to create backup directory: " + backupDir);
                return;
            }
        }

        if(!backupDir.endsWith("/"))backupDir+="/";
        if(!makeDir(backupDir))return;

        // Handle existing backup files, shifting them up
        for (int i = copiesNumber-1; i >= 0; i--) {
            File currentFile = new File(backupDir+rootFileName+i+".bak");

            if (currentFile.exists()) {
                if (i == 5) {
                    currentFile.delete();
                } else {
                    File nextFile = new File(backupDir+rootFileName+(i + 1)+".bak");
                    currentFile.renameTo(nextFile);
                }
            }
        }

        // Copy the current file to backup0
        try {
            Files.copy(sourceFile.toPath(), new File(backupDir+rootFileName+"0"+".bak").toPath(), StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Backup created successfully");
        } catch (IOException e) {
            System.out.println("Failed to create backup: " + e.getMessage());
        }
    }

    //    public static boolean checkFileOfFreeDisk(String storageDir, String did) {
//        String path = FileTools.getSubPathForFreeDisk(did);
//        File file = new File(storageDir+path, did);
//        if(!file.exists())return false;
//        byte[] existBytes;
//        try(FileInputStream fileInputStream = new FileInputStream(file)) {
//            existBytes = fileInputStream.readAllBytes();
//        } catch (IOException e) {
//            return false;
//        }
//        String existDid = Hex.toHex(Hash.Sha256x2(existBytes));
//        return did.equals(existDid);
//    }
    public static enum CreateNewFileMode {
        REWRITE,ADD_1,RETURN_NULL,THROW_EXCEPTION
    }

    @Test
    public void test(){
        String name = "a.b.txt";
        System.out.println(getFileNameTail(name,false));
        getNewFile(null,"config.json", CreateNewFileMode.REWRITE);
    }
    public static String getFileNameHead(String fileName) {
        int dotIndex = fileName.lastIndexOf(".");
        return fileName.substring(0,dotIndex);
    }
    public static String getFileNameTail(String fileName,boolean withDot) {
        int dotIndex = fileName.lastIndexOf(".");
        if(!withDot)dotIndex+=1;
        return fileName.substring(dotIndex);
    }

    /**
     * Creates a directory if it doesn't already exist
     * @param dirPath the path of the directory to create
     * @return true if the directory was created or already exists, false if creation failed
     */
    public static boolean makeDir(String dirPath) {
        File directory = new File(dirPath);
        if (!directory.exists()) {
            return directory.mkdirs();
        }
        return true;
    }
}
