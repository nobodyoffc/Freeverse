package initial;

import appTools.Settings;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import configure.*;
import tools.JsonTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPool;
import handlers.SessionHandler;

import javax.servlet.ServletConfig;
import javax.servlet.http.HttpServlet;
import java.io.File;
import java.io.IOException;
import java.util.Map;

import static configure.Configure.makeConfigFileName;
import static feip.feipData.Service.makeServerDataDir;

public class Initiator extends HttpServlet {
    public static final ServiceType SERVICE_TYPE = ServiceType.DISK;
    public static String dataPath;
    public static ApiAccount esAccount;
    public static String listenPath;
    public static ElasticsearchClient esClient;
    public static JedisPool jedisPool;
    public static String sid;
    public static SessionHandler sessionHandler;
    final static Logger log = LoggerFactory.getLogger(Initiator.class);

    public void init(ServletConfig config) {
        System.out.println("Initiating DISK server...");
        String configFileName = makeConfigFileName(SERVICE_TYPE);

        WebServerConfig webServerConfig;
        Configure configure;
        Settings settings;
        Map<String,Configure> configureMap;
        try {
            webServerConfig = JsonTools.readJsonFromFile(configFileName,WebServerConfig.class);
            configureMap = JsonTools.readMapFromJsonFile(null,webServerConfig.getConfigPath(),String.class,Configure.class);
            if(configureMap==null){
                log.error("Failed to read the config file of "+configFileName+".");
                return;
            }
            configure = configureMap.get(webServerConfig.getPasswordName());
            settings = JsonTools.readJsonFromFile(webServerConfig.getSettingPath(), Settings.class);
            listenPath = (String)settings.getSettingMap().get(appTools.Settings.LISTEN_PATH);
            dataPath = webServerConfig.getDataPath();
            sid = webServerConfig.getSid();
            sessionHandler = new SessionHandler(null, sid, jedisPool);
        } catch (IOException e) {
            log.error("Failed to read the config file of "+configFileName+".");
            return;
        }

        ApiAccount redisAccount = configure.getApiAccountMap().get(settings.getApiAccountId(ServiceType.REDIS));
        jedisPool = redisAccount.connectRedis();
        byte[] symKey = Configure.getSymKeyForWebServer(webServerConfig, configure,jedisPool);

        esAccount = configure.getApiAccountMap().get(settings.getApiAccountId(ServiceType.ES));
        ApiProvider esProvider = configure.getApiProviderMap().get(esAccount.getProviderId());
        esClient = (ElasticsearchClient)esAccount.connectApi(esProvider,symKey);

        String storageDir = makeServerDataDir(sid, ServiceType.DISK);
        File directory = new File(storageDir);
        if (!directory.exists()) {
            if(directory.mkdirs()){
                log.debug("{} is Created.", storageDir);
            }else log.error("Failed to create {}", storageDir);
        }

        System.out.println("DISK server is initiated.");
    }

    @Override
    public void destroy(){
        log.debug("Destroying DISK server...");
        jedisPool.close();
        esAccount.closeEs();
        log.debug("DISK server is Destroyed.");
    }
}
