package configure.old;

import constants.FieldNames;
import redis.clients.jedis.Jedis;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;

import static constants.Constants.UserDir;
import static constants.Strings.*;

public class ConfigAPIP extends ConfigService {

    private String avatarElementsPath = System.getProperty(UserDir) + "/avatar/elements";
    private String avatarPngPath = System.getProperty(UserDir) + "/avatar/png";

    @Override
    public void config(BufferedReader br) throws IOException {
        System.out.println("Config is needed. The content of " + configFileName + " :");
        printConfigJson();

        setAvatarPath(br);
        setBlockFilePath(br);
        setOpReturnFilePath(br);
        setListenPath(br);
        setTomcatBasePath(br);

        setWindowTime(br);
        setCheckOrderOpReturn(br);
        setForbidFreeGet(br);
        setScanMempool(br);
        setConfigFilePath(br);
        setEsIp(br);
        setEsPort(br);
        setEsUsername(br);
        setRpcIp(br);
        setRpcPort(br);
        setRpcUsername(br);
        setRpcPassword(br);

        writeConfigToFile();

        System.out.println("Config set. The content is :");
        printConfigJson();
    }

    @Override
    public boolean loadConfigToRedis() {
        try (Jedis jedis = new Jedis()) {
            jedis.hset(CONFIG, ES_IP, esIp);
            jedis.hset(CONFIG, ES_PORT, String.valueOf(esPort));
            jedis.hset(CONFIG, CONFIG_FILE_PATH, configFilePath);
            jedis.hset(CONFIG, OP_RETURN_FILE_PATH, this.getOpReturnFilePath());
            jedis.hset(CONFIG, TOMCAT_BASE_PATH, this.tomcatBasePath);
            jedis.hset(CONFIG, LISTEN_PATH, this.getListenPath());
            if (serviceName != null) jedis.hset(CONFIG, SERVICE_NAME, serviceName);
            jedis.hset(CONFIG, FieldNames.FORBID_FREE_API, String.valueOf(forbidFreeGet));
            jedis.hset(CONFIG, CHECK_ORDER_OPRETURN, String.valueOf(checkOrderOpReturn));
            jedis.hset(CONFIG, SCAN_MEMPOOL, String.valueOf(scanMempool));
            jedis.hset(CONFIG, WINDOW_TIME, String.valueOf(this.windowTime));
            jedis.hset(CONFIG, AVATAR_ELEMENTS_PATH, avatarElementsPath);
            jedis.hset(CONFIG, AVATAR_PNG_PATH, avatarPngPath);

        } catch (Exception e) {
            System.out.println("Error: Load config to redis. " + e.getMessage());
            return false;
        }
        return true;
    }

    public void setAvatarPath(BufferedReader br) throws IOException {
        setAvatarElementsPath(br);
        setAvatarPngPath(br);
    }

    private void setAvatarElementsPath(BufferedReader br) throws IOException {

        while (true) {
            if (this.avatarElementsPath != null)
                System.out.println("The elements path of avatar making is: " + this.avatarElementsPath);
            System.out.println("Input the elements path of avatar making. Press 's' to skip:");
            String str1 = br.readLine();
            if ("s".equals(str1)) return;
            if (!str1.endsWith("/")) str1 = str1 + "/";
            File file = new File(str1);
            if (file.exists()) {
                this.avatarElementsPath = str1;
                System.out.println("\nThe elements path of avatar making was set.");
                return;
            } else {
                System.out.println("\nPath doesn't exist. ");
            }
        }
    }


    private void setAvatarPngPath(BufferedReader br) throws IOException {

        while (true) {
            if (this.avatarPngPath != null)
                System.out.println("The path for generated png files is: " + this.avatarPngPath);
            System.out.println("Input the path for generated png files. Press 's' to skip:");
            String str1 = br.readLine();
            if ("s".equals(str1)) return;
            if (!str1.endsWith("/")) str1 = str1 + "/";
            File file = new File(str1);
            if (!file.exists()) {
                System.out.println("\nPath doesn't exist. ");
            } else {
                this.avatarPngPath = str1;
                System.out.println("\nThe path for generated png files was set.");
                return;
            }
        }
    }

}
