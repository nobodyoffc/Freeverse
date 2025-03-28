package initial;

import appTools.Settings;
import clients.ApipClient;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import configure.*;
import feip.feipData.Service;
import handlers.*;
import utils.FileUtils;
import utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPool;
//import handlers.AccountHandler;

import javax.servlet.ServletConfig;
import javax.servlet.http.HttpServlet;
import java.io.IOException;
import java.util.Map;

import static configure.Configure.makeConfigFileName;
import static feip.feipData.Service.ServiceType.APIP;
import static feip.feipData.Service.ServiceType.ES;
import static handlers.Handler.HandlerType.*;
import static utils.FileUtils.makeServerDataDir;

public class Initiator extends HttpServlet {
    public static final String SERVICE_TYPE = Service.ServiceType.DISK.name();
    public static String dataPath;
    public static ApiAccount esAccount;
    public static String listenPath;
    public static ElasticsearchClient esClient;
    public static JedisPool jedisPool;
    public static String sid;
    public static SessionHandler sessionHandler;
    public static CashHandler cashHandler;
    public static AccountHandler accountHandler;
    public static ApipClient apipClient;
    public static Settings settings;
    final static Logger log = LoggerFactory.getLogger(Initiator.class);
    public static final Object[] modules = new Object[]{
            Service.ServiceType.REDIS,
            Handler.HandlerType.SESSION,
            Service.ServiceType.APIP,
            Handler.HandlerType.CASH,
            Handler.HandlerType.ACCOUNT
    };
    public void init(ServletConfig config) {
        System.out.println("Initiating DISK server...");
        String configFileName = makeConfigFileName(SERVICE_TYPE);

        WebServerConfig webServerConfig;
        Configure configure;
        Map<String,Configure> configureMap;
        try {
            webServerConfig = JsonUtils.readJsonFromFile(configFileName,WebServerConfig.class);
            configureMap = JsonUtils.readMapFromJsonFile(null,webServerConfig.getConfigPath(),String.class,Configure.class);
            if(configureMap==null){
                log.error("Failed to read the config file of "+configFileName+".");
                return;
            }
            configure = configureMap.get(webServerConfig.getPasswordName());
            settings = JsonUtils.readJsonFromFile(webServerConfig.getSettingPath(), Settings.class);
            settings.setConfig(configure);

            listenPath = (String)settings.getSettingMap().get(appTools.Settings.LISTEN_PATH);
            dataPath = webServerConfig.getDataPath();
            sid = webServerConfig.getSid();
            sessionHandler = new SessionHandler(null, sid, jedisPool, settings.getDbDir());

        } catch (IOException e) {
            log.error("Failed to read the config file of "+configFileName+".");
            return;
        }

        settings.initModulesMute(modules);

        jedisPool = (JedisPool) settings.getClientGroup(Service.ServiceType.REDIS).getClient();
        esClient = (ElasticsearchClient) settings.getClientGroup(ES).getClient();
        apipClient = (ApipClient) settings.getClientGroup(APIP).getClient();
        cashHandler = (CashHandler) settings.getHandler(CASH);
        accountHandler = (AccountHandler)settings.getHandler(ACCOUNT);

        accountHandler.start();

        String storageDir = makeServerDataDir(sid, Service.ServiceType.DISK);
        FileUtils.checkDirOrMakeIt(storageDir);

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
