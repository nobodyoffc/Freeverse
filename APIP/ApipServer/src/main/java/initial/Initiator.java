package initial;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import configure.*;
import javaTools.JsonTools;
import nasa.NaSaRpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPool;
import appTools.Settings;

import javax.servlet.ServletConfig;
import javax.servlet.http.HttpServlet;
import java.io.IOException;
import java.io.Serial;
import java.util.Map;

import static configure.Configure.makeConfigFileName;


public class Initiator extends HttpServlet {
    @Serial
    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(Initiator.class);
    public static final ServiceType SERVICE_TYPE = ServiceType.APIP;
    public static String dataPath;
    private ApiAccount esAccount;
    public static String sid;
    public static ElasticsearchClient esClient;
    public static NaSaRpcClient naSaRpcClient;
    public static JedisPool jedisPool;
    public static String avatarElementsPath;
    public static String avatarPngPath;


    @Override
    public void destroy(){
        log.debug("Destroy APIP server...");
        jedisPool.close();
        esAccount.closeEs();
        log.debug("APIP server is destroyed.");
    }
    @Override
    public void init(ServletConfig config) {
        System.out.println("Initiate APIP web server...");
        String configFileName = makeConfigFileName(SERVICE_TYPE);

        WebServerConfig webServerConfig;
        Configure configure;
        Settings settings;
        Map<String,Configure> configureMap;
        try {
            webServerConfig = JsonTools.readJsonFromFile(configFileName,WebServerConfig.class);
            configureMap = JsonTools.readMapFromJsonFile(null,webServerConfig.getConfigPath(),String.class,Configure.class);
            configure = configureMap.get(webServerConfig.getPasswordName());
            settings = JsonTools.readJsonFromFile(webServerConfig.getSettingPath(), Settings.class);
            dataPath = webServerConfig.getDataPath();
            sid = webServerConfig.getSid();
        } catch (IOException e) {
            log.error("Failed to read the config file of "+configFileName+".");
            return;
        }

        ApiAccount redisAccount = configure.getApiAccountMap().get(settings.getApiAccountId(ServiceType.REDIS));
        jedisPool = redisAccount.connectRedis();
        byte[] symKey = Configure.getSymKeyForWebServer(webServerConfig, configure,jedisPool);

        ApiAccount nasaAccount = configure.getApiAccountMap().get(settings.getApiAccountId(ServiceType.NASA_RPC));
        ApiProvider nasaProvider = configure.getApiProviderMap().get(nasaAccount.getProviderId());
        naSaRpcClient = (NaSaRpcClient) nasaAccount.connectApi(nasaProvider,symKey);

        esAccount = configure.getApiAccountMap().get(settings.getApiAccountId(ServiceType.ES));
        ApiProvider esProvider = configure.getApiProviderMap().get(esAccount.getProviderId());
        esClient = (ElasticsearchClient)esAccount.connectApi(esProvider,symKey);

        avatarElementsPath = (String) settings.getSettingMap().get(Settings.AVATAR_ELEMENTS_PATH);
        avatarPngPath = (String) settings.getSettingMap().get(Settings.AVATAR_PNG_PATH);
        if (!Initiator.avatarPngPath.endsWith("/")) avatarPngPath = avatarPngPath + "/";
        if (!avatarElementsPath.endsWith("/")) avatarElementsPath = avatarElementsPath + "/";

        System.out.println("APIP server initiated.");

    }

    //    public static void freshWebParams(WebServerConfig webServerConfig, Jedis jedis) {
//        try {
//            forbidFreeApi = Boolean.parseBoolean(jedis.hget(addSidBriefToName(webServerConfig.getSid(), WEB_PARAMS), FORBID_FREE_API));
//            windowTime = Long.parseLong(jedis.hget(addSidBriefToName(webServerConfig.getSid(), WEB_PARAMS), WINDOW_TIME));
//        }catch (Exception ignore){};
//        if(forbidFreeApi==null)forbidFreeApi=false;
//        if(windowTime==null)windowTime = Settings.DEFAULT_WINDOW_TIME;
//    }
}
