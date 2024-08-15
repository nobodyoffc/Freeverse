package javaTools;

import crypto.Hash;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileTools {
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
                System.err.println("File does not exist: " + filename);
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
        }else if(Boolean.TRUE.equals(checkFileOfFreeDisk(path, did)))return did;
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

    public static Boolean checkFileOfFreeDisk(String path, String did) {
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
        return Hex.toHex(BytesTools.getRandomBytes(4));
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
}
