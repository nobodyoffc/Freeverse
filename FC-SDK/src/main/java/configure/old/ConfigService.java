package configure.old;

import constants.FieldNames;
import redis.clients.jedis.Jedis;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;

import static constants.Strings.*;

public class ConfigService extends ConfigFEIP {

    protected String serviceName;
    protected long windowTime = 5000;
    protected boolean checkOrderOpReturn = true;
    protected boolean forbidFreeGet = false;
    protected boolean scanMempool = true;
    protected String tomcatBasePath;
    protected String listenPath;
    protected String rpcIp = "127.0.0.1";
    protected int rpcPort = 8332;
    protected String rpcUser = "user";
    protected String rpcPassword = "password";

    public long getWindowTime() {
        return windowTime;
    }

    public void setWindowTime(BufferedReader br) {
        System.out.println("The windowTime is: " + windowTime);
        while (true) {
            System.out.println("Input the windowTime. Input 's' to skip:");
            try {
                String input = br.readLine();
                if ("s".equals(input)) return;
                setWindowTime(Long.parseLong(input));
                break;
            } catch (Exception ignore) {
                System.out.println("A integer is required.");
            }
        }
    }

    public void setWindowTime(long windowTime) {
        this.windowTime = windowTime;
    }

    public boolean isCheckOrderOpReturn() {
        return checkOrderOpReturn;
    }

    protected void setCheckOrderOpReturn(BufferedReader br) {

        System.out.println("Check OpReturn of orders: " + checkOrderOpReturn);
        while (true) {
            System.out.println("Input true or false. Input 's' to skip:");
            try {
                String input = br.readLine();
                if ("s".equals(input)) return;
                if ("true".equals(input)) {
                    setCheckOrderOpReturn(true);
                    return;
                } else if ("false".equals(input)) {
                    setCheckOrderOpReturn(false);
                    return;
                } else {
                    System.out.println("Wrong input.");
                }
            } catch (Exception ignore) {
                System.out.println("Wrong input.");
            }
        }
    }

    public void setCheckOrderOpReturn(boolean checkOrderOpReturn) {
        this.checkOrderOpReturn = checkOrderOpReturn;
    }

    public boolean isForbidFreeGet() {
        return forbidFreeGet;
    }

    protected void setForbidFreeGet(BufferedReader br) {
        System.out.println("Forbid free get APIs:  " + forbidFreeGet);
        while (true) {
            System.out.println("Input true or false. Input 's' to skip:");
            try {
                String input = br.readLine();
                if ("s".equals(input)) return;
                if ("true".equals(input)) {
                    setForbidFreeGet(true);
                    return;
                } else if ("false".equals(input)) {
                    setForbidFreeGet(false);
                    return;
                } else {
                    System.out.println("Wrong input.");
                }
            } catch (Exception ignore) {
                System.out.println("Wrong input.");
            }
        }
    }

    public void setForbidFreeGet(boolean forbidFreeGet) {
        this.forbidFreeGet = forbidFreeGet;
    }

    public String getTomcatBasePath() {
        return tomcatBasePath;
    }

    public void setTomcatBasePath(String tomcatBasePath) {
        this.tomcatBasePath = tomcatBasePath;
    }

    protected void setTomcatBasePath(BufferedReader br) throws IOException {

        while (true) {
            if (this.tomcatBasePath != null)
                System.out.println("The path for tomcat base is: " + this.tomcatBasePath);
            System.out.println("Input the path for tomcat base. Press 's' to skip:");
            String str1 = br.readLine();
            if ("s".equals(str1)) return;
            if (!str1.endsWith("/")) str1 = str1 + "/";
            File file = new File(str1);
            if (!file.exists()) {
                System.out.println("\nPath doesn't exist. ");
            } else {
                this.tomcatBasePath = str1;
                return;
            }
        }
    }

    public boolean loadConfigToRedis() {
        try (Jedis jedis = new Jedis()) {
            jedis.hset(CONFIG, ES_IP, esIp);
            jedis.hset(CONFIG, ES_PORT, String.valueOf(esPort));
            jedis.hset(CONFIG, CONFIG_FILE_PATH, configFilePath);
            jedis.hset(CONFIG, OP_RETURN_FILE_PATH, this.getOpReturnFilePath());
            jedis.hset(CONFIG, LISTEN_PATH, this.getListenPath());
//            jedis.hset(CONFIG, SERVICE_NAME, serviceName);
            jedis.hset(CONFIG, FieldNames.FORBID_FREE_API, String.valueOf(forbidFreeGet));
            jedis.hset(CONFIG, CHECK_ORDER_OPRETURN, String.valueOf(checkOrderOpReturn));
            jedis.hset(CONFIG, SCAN_MEMPOOL, String.valueOf(scanMempool));
            jedis.hset(CONFIG, WINDOW_TIME, String.valueOf(this.windowTime));

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public boolean isScanMempool() {
        return scanMempool;
    }

    protected void setScanMempool(BufferedReader br) {
        System.out.println("Run mempool scanner when starting:  " + scanMempool);
        while (true) {
            System.out.println("Input true or false. Input 's' to skip:");
            try {
                String input = br.readLine();
                if ("s".equals(input)) return;
                if ("true".equals(input)) {
                    setScanMempool(true);
                    return;
                } else if ("false".equals(input)) {
                    setScanMempool(false);
                    return;
                } else {
                    System.out.println("Wrong input.");
                }
            } catch (Exception ignore) {
                System.out.println("Wrong input.");
            }
        }
    }

    public void setScanMempool(boolean scanMempool) {
        this.scanMempool = scanMempool;
    }

    @Override
    public void config(BufferedReader br) throws IOException {
        System.out.println("Config is needed. The content of " + configFileName + " :");
        printConfigJson();
        setTomcatBasePath(br);
        setBlockFilePath(br);
        setOpReturnFilePath(br);
        setListenPath(br);
//        setServiceName(br);
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
//    public void setServiceName(BufferedReader br) throws IOException {
//        System.out.println("The service name is: "+serviceName);
//        System.out.println("Input the service name. Input 's' to skip:");
//        String name = br.readLine();
//        if ("s".equals(name)) return;
//        setServiceName(name);
//    }

    public String getRpcUser() {
        return rpcUser;
    }

    public void setRpcUser(String rpcUser) {
        this.rpcUser = rpcUser;
    }

    public void setRpcUsername(BufferedReader br) throws IOException {

        String name;

        System.out.println("The username of freecash RPC is: " + rpcUser);
        System.out.println("Input the username of freecash RPC. Input 's' to skip:");
        name = br.readLine();
        if ("s".equals(name)) return;
        setRpcName(name);
        System.out.println("\nThe name of freecash RPC was set.");
    }

    public void setRpcName(String rpcName) {
        this.rpcUser = rpcName;
    }

    public String getRpcIp() {
        return rpcIp;
    }

    public void setRpcIp(String rpcIp) {
        this.rpcIp = rpcIp;
    }

    protected void setRpcIp(BufferedReader br) throws IOException {
        if (this.rpcIp != null)
            System.out.println("IP of freecash RPC is: " + this.rpcIp);

        System.out.println("Input the IP of freecash RPC server. Press 's' to skip:");

        while (true) {
            String str = br.readLine();
            if ("s".equals(str)) return;
            if (str.matches("((25[0-5]|2[0-4]\\d|((1\\d{2})|([1-9]?\\d)))\\.){3}(25[0-5]|2[0-4]\\d|((1\\d{2})|([1-9]?\\d)))")) {
                this.rpcIp = str;
                System.out.println("The freecash RPC IP was set.");
                break;
            }
            System.out.println("It must be a IP address, like \"100.102.102.10\". Input again.");
        }
    }

    public int getRpcPort() {
        return rpcPort;
    }

    public void setRpcPort(int rpcPort) {
        this.rpcPort = rpcPort;
    }

    public void setRpcPort(BufferedReader br) throws IOException {
        if (this.rpcPort != 0)
            System.out.println("Port of freecash RPC is: " + this.rpcPort);

        System.out.println("Input the port of freecash RPC. Press 's' to skip:");

        while (true) {
            String str = br.readLine();
            if ("s".equals(str)) return;
            int i = 0;
            try {
                i = Integer.parseInt(str);
            } catch (Exception e) {
                System.out.println("It must be a port. It's a integer between 0 and 655350. Input again.\"");
            }
            if (i > 0 && i < 65535) {
                this.rpcPort = i;
                return;
            }
            System.out.println("It has to be between 0 and 655350. Input again.");
        }
    }

    public String getRpcPassword() {
        return rpcPassword;
    }

    public void setRpcPassword(BufferedReader br) throws IOException {

        String password;

        System.out.println("The password of freecash RPC is: " + rpcPassword);
        System.out.println("Input the password of freecash RPC. Input 's' to skip:");
        password = br.readLine();
        if ("s".equals(password)) return;
        setRpcPassword(password);
        System.out.println("\nThe password of freecash RPC was set.");
    }

    public void setRpcPassword(String rpcPassword) {
        this.rpcPassword = rpcPassword;
    }

    public String getListenPath() {
        return listenPath;
    }

    public void setListenPath(String listenPath) {
        this.listenPath = listenPath;
    }

    protected void setListenPath(BufferedReader br) throws IOException {
        if (this.listenPath != null)
            System.out.println("The listenPath is: " + this.listenPath);
        String input;
        while (true) {
            System.out.println("""
                    Input the path to be listen when scanning new order.\s
                    * 'b' for block directory.\s
                    * 'o' for opreturn directory.\s
                    * The path for a new directory
                    * 's' to skip:""");
            input = br.readLine();

            if ("s".equals(input)) return;
            if ("b".equals(input)) {
                this.listenPath = getBlockFilePath();
                return;
            }
            if ("o".equals(input)) {
                this.listenPath = getOpReturnFilePath();
                return;
            }

            File file = new File(input);
            if (!file.exists()) {
                System.out.println("\nPath doesn't exist. Input again.");
            } else {
                if (!(input.endsWith("/"))) input = input + "/";
                this.listenPath = input;
                return;
            }
        }
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }
}
