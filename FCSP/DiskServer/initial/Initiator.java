package initial;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import configure.*;
import javaTools.JsonTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPool;
import startManager.DiskManagerSettings;
import startManager.StartDiskManager;

import javax.servlet.ServletConfig;
import javax.servlet.http.HttpServlet;
import java.io.File;
import java.io.IOException;

import static configure.Configure.makeConfigFileName;

public class Initiator extends HttpServlet {
    public static final ServiceType SERVICE_TYPE = ServiceType.DISK;
    public static String dataPath;
    public static ApiAccount esAccount;
    public static ElasticsearchClient esClient;
    public static JedisPool jedisPool;
    public static String sid;
    final static Logger log = LoggerFactory.getLogger(Initiator.class);

    public void init(ServletConfig config) {
        System.out.println("Initiating FCSP server...");
        String configFileName = makeConfigFileName(SERVICE_TYPE);
        WebServerConfig webServerConfig;
        Configure configure;
        DiskManagerSettings settings;

        try {
            webServerConfig = JsonTools.readJsonFromFile(configFileName,WebServerConfig.class);
            configure = JsonTools.readJsonFromFile(webServerConfig.getConfigPath(),Configure.class);
            settings = JsonTools.readJsonFromFile(webServerConfig.getSettingPath(), DiskManagerSettings.class);
            dataPath = webServerConfig.getDataPath();
            sid = webServerConfig.getSid();
        } catch (IOException e) {
            log.error("Failed to read the config file of "+configFileName+".");
            return;
        }

        ApiAccount redisAccount = configure.getApiAccountMap().get(settings.getRedisAccountId());
        jedisPool = redisAccount.connectRedis();
        byte[] symKey = Configure.getSymKeyForWebServer(webServerConfig, configure,jedisPool);

        esAccount = configure.getApiAccountMap().get(settings.getEsAccountId());
        ApiProvider esProvider = configure.getApiProviderMap().get(esAccount.getProviderId());
        esClient = (ElasticsearchClient)esAccount.connectApi(esProvider,symKey);

        File directory = new File(StartDiskManager.STORAGE_DIR);
        if (!directory.exists()) {
            if(directory.mkdirs()){
                log.debug("{} is Created.", StartDiskManager.STORAGE_DIR);
            }else log.error("Failed to create {}", StartDiskManager.STORAGE_DIR);
        }

        System.out.println("FCSP server is initiated.");
    }

    @Override
    public void destroy(){
        log.debug("Destroying FCSP server...");
        jedisPool.close();
        esAccount.closeEs();
        log.debug("FCSP server is Destroyed.");
    }
}
