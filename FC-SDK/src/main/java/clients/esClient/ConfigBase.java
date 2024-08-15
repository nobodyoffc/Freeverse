package clients.esClient;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import javaTools.JsonTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.HexFormat;

import static constants.Constants.UserDir;

public class ConfigBase {
    private static final Logger log = LoggerFactory.getLogger(ConfigBase.class);
    protected String esIp;
    protected int esPort;
    protected String esUsername;
    protected String randomSymKeyHex;
    protected String blockFilePath;
    protected String configFileName = "config.json";
    protected String configFilePath = System.getProperty(UserDir);
    private String opReturnFilePath;

    public static void createConfigFile(String filePath, String fileName) {
        Path path = Paths.get(filePath, fileName);

        try {
            if (!Files.exists(path)) {
                Files.createFile(path);
                System.out.println("File '" + fileName + "' created successfully.");
            } else {
                System.out.println("File '" + fileName + "' already exists.");
            }
        } catch (IOException e) {
            System.out.println("Error creating the file: " + fileName);
            log.debug(e.getMessage());
        }
    }

    public void config(BufferedReader br) throws IOException {
        printConfigJson();

        setEsIp(br);
        setEsPort(br);
        setEsUsername(br);
        setSymKey();
        setBlockFilePath(br);
        setOpReturnFilePath(br);
        setConfigFilePath(br);

        writeConfigToFile();
    }

    public <T> T getClassInstanceFromFile(BufferedReader br, Class<T> tClass) throws IOException {

        T t;
        if (configFilePath == null) configFilePath = System.getProperty("user.home") + "/";
        System.out.println("Get config from path: " + configFilePath + " config name:" + configFileName);
        File configFile = new File(configFilePath, configFileName);
        System.out.println("Path of config file: " + configFile.getAbsolutePath());
        if (configFile.exists()) {
            t = JsonTools.readObjectFromJsonFile(configFilePath, configFileName, tClass);
            if (t == null) {
                System.out.println("Can't get parameters from " + configFileName + ". Config again...\n");
                config(br);
                t = JsonTools.readObjectFromJsonFile(configFilePath, configFileName, tClass);
            } else {
                System.out.println("Config json: " + JsonTools.toNiceJson(t));
            }
        } else {
            createConfigFile(configFilePath, configFileName);
            System.out.println("The file of " + configFileName + " was not found. Config it now...\n");
            config(br);
            t = JsonTools.readObjectFromJsonFile(configFilePath, configFileName, tClass);
        }
        return t;
    }

    public <T> T getClassInstanceFromFile(Class<T> tClass) throws IOException {

        T t;
        File configFile = new File(configFilePath, configFileName);
        System.out.println("Path of config file: " + configFile.getAbsolutePath());
        if (configFile.exists()) {
            t = JsonTools.readObjectFromJsonFile(configFilePath, configFileName, tClass);
            if (t == null) {
                System.out.println("Can't get parameters from config file. Config again...\n");
                t = JsonTools.readObjectFromJsonFile(configFilePath, configFileName, tClass);
            } else {
                System.out.println("Config json: " + JsonTools.toNiceJson(t));
            }
        } else {
            createConfigFile(configFilePath, configFileName);
            System.out.println("The file of " + configFileName + " was not found. Config it now...\n");
            t = JsonTools.readObjectFromJsonFile(configFilePath, configFileName, tClass);
        }
        return t;
    }

    public void writeConfigToFile() {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.setPrettyPrinting();
        Gson gson = gsonBuilder.create();

        File configFile = new File(configFilePath, configFileName);
        if (!configFile.exists()) {
            try {

                if (!configFile.createNewFile()) {
                    System.out.println("Failed to create config file.");
                    return;
                }
            } catch (Exception e) {
                System.out.println("Wrong file path.");
                log.debug(e.getMessage());
                return;
            }
        }

        FileOutputStream fos;
        try {
            fos = new FileOutputStream(configFile);
            fos.write(gson.toJson(this).getBytes());
            fos.close();
        } catch (IOException e) {
            System.out.println("Write config to file wrong.");
        }
    }

    public int getEsPort() {
        return esPort;
    }

    public void setEsPort(BufferedReader br) throws IOException {
        if (this.esPort != 0)
            System.out.println("Port of ES is: " + this.esPort);

        System.out.println("Input the port of ES server. Press 's' to skip:");

        while (true) {
            String str = br.readLine();
            if ("s".equals(str) || "".equals(str)) return;
            int i = 0;
            try {
                i = Integer.parseInt(str);
            } catch (Exception e) {
                System.out.println("It must be a port. It's a integer between 0 and 655350. Input again.\"");
                log.debug(e.getMessage());
            }
            if (i > 0 && i < 65535) {
                this.esPort = i;
                return;
            }
            System.out.println("It has to be between 0 and 655350. Input again.");
        }
    }

    public String getBlockFilePath() {
        return blockFilePath;
    }

    public void setBlockFilePath(BufferedReader br) throws IOException {
        while (true) {
            if (this.blockFilePath != null)
                System.out.println("The path of block files is: " + this.blockFilePath);
            System.out.println("Input the path of freecash block data. Press 's' to skip:");
            String str1 = br.readLine();
            if ("s".equals(str1) || "".equals(str1)) {
                return;
            }
            if (!str1.endsWith("/")) str1 = str1 + "/";
            File file = new File(str1);
            if (!file.exists()) {
                System.out.println("\nPath doesn't exist. ");
            } else {
                this.blockFilePath = str1;
                return;
            }
        }
    }

    public String getRandomSymKeyHex() {
        return randomSymKeyHex;
    }

    public void printConfigJson() {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.setPrettyPrinting();
        Gson gson = gsonBuilder.create();
        System.out.println(gson.toJson(this));
    }

    public void setSymKey() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] bytes = new byte[16];
        secureRandom.nextBytes(bytes);
        this.randomSymKeyHex = HexFormat.of().formatHex(bytes);
    }

    public String getConfigFilePath() {
        return configFilePath;
    }

    public void setConfigFilePath(BufferedReader br) throws IOException {
        while (true) {
            if (this.blockFilePath != null)
                System.out.println("The path of config file is: " + this.configFilePath);
            System.out.println("Input the path of config file. 's' to skip. 'c' to set current system path in it:");
            String str1 = br.readLine();
            if ("s".equals(str1) || "".equals(str1)) {
                return;
            }
            if ("c".equals(str1)) {
                configFilePath = System.getProperty(UserDir) + "/";
                return;
            }

            File file = new File(str1);
            if (!file.exists()) {
                System.out.println("\nPath doesn't exist. ");
            } else {
                if (!str1.endsWith("/")) str1 = str1 + "/";
                this.configFilePath = str1;
                return;
            }
        }
    }

    public void setConfigFilePath(String configFilePath) {
        this.configFilePath = configFilePath;
    }

    public String getOpReturnFilePath() {
        return opReturnFilePath;
    }

    public void setOpReturnFilePath(BufferedReader br) throws IOException {
        while (true) {
            if (this.opReturnFilePath != null)
                System.out.println("The path of OP_Return data files is: " + this.opReturnFilePath);
            System.out.println("Input the path of OP_Return data files. Press 's' to skip:");
            String str1 = br.readLine();
            if ("s".equals(str1)) return;
            if (!str1.endsWith("/")) str1 = str1 + "/";
            File file = new File(str1);
            if (!file.exists()) {
                System.out.println("\nPath doesn't exist. ");
            } else {
                this.opReturnFilePath = str1;
                return;
            }
        }
    }

    public String getEsIp() {
        return this.esIp;
    }

    public void setEsIp(BufferedReader br) throws IOException {
        if (this.esIp != null)
            System.out.println("IP of ES is: " + this.esIp);

        System.out.println("Input the IP of ES server. Press 's' to skip:");

        while (true) {
            String str = br.readLine();
            if ("s".equals(str) || "".equals(str)) return;
            if (str.matches("((25[0-5]|2[0-4]\\d|((1\\d{2})|([1-9]?\\d)))\\.){3}(25[0-5]|2[0-4]\\d|((1\\d{2})|([1-9]?\\d)))")) {
                this.esIp = str;
                break;
            }
            System.out.println("It must be an IP address, like \"100.102.102.10\". Input again.");
        }
    }

    public String getEsUsername() {
        return esUsername;
    }

    public void setEsUsername(BufferedReader br) throws IOException {
        if (this.esUsername != null)
            System.out.println("Username of ES is: " + this.esUsername);
        System.out.println("Input the username of ES for SSL or press 's' to skip when starting without SSL, 'd' to delete it:");
        String str = br.readLine();
        if ("d".equals(str)) {
            this.esUsername = null;
            return;
        }
        if ("s".equals(str) || "".equals(str)) return;
        this.esUsername = str;

    }

    public void setEsUsername(String esUsername) {
        this.esUsername = esUsername;
    }

    public String getConfigFileName() {
        return configFileName;
    }

}
