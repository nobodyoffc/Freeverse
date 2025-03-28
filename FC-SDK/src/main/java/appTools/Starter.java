package appTools;

import static clients.FeipClient.*;

import java.io.BufferedReader;
import java.util.*;

import app.CidInfo;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import fch.fchData.Cid;
import clients.ApipClient;
import configure.Configure;
import feip.feipData.Service;
import handlers.Handler;
import nasa.NaSaRpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Starter {
    protected static final Logger log = LoggerFactory.getLogger(Starter.class);


    public static Settings startClient(String clientName,
                                       Map<String, Object> settingMap, BufferedReader br, Object[] modules) {
        // Load config info from the file of config.json

        Configure.loadConfig(br);
        Configure configure = Configure.checkPassword(br);
        if(configure == null) return null;
        byte[] symKey = configure.getSymKey();
    
        String fid = configure.chooseMainFid(symKey);
        Settings settings;

        settings = Settings.loadSettings(fid, clientName);

        if(settings == null) {
            settings = new Settings(configure,clientName);
            settings.setModules(modules);
            if(settingMap != null) {
                settings.setSettingMap(settingMap);
                settings.checkSetting(br);
            }
        }

        // Initialize clients and handlers
        settings.initiateClient(fid, clientName, symKey, configure, br);
        ElasticsearchClient esClient ;
        NaSaRpcClient naSaRpcClient;
        ApipClient apipClient = (ApipClient) settings.getClient(Service.ServiceType.APIP);

        if(apipClient==null){
            esClient = (ElasticsearchClient)settings.getClient(Service.ServiceType.ES);
            if(esClient==null) {
                naSaRpcClient = (NaSaRpcClient) settings.getClient(Service.ServiceType.NASA_RPC);
                if(naSaRpcClient==null) {
                    log.info("Failed to fresh bestHeight due to the absence of apipClient, nasaClient, and esClient.");
                    return settings;
                }
            }
        }
        long bestHeight = settings.getBestHeight();//new Wallet(apipClient).getBestHeight();

        if(apipClient!=null) {
            Cid cid = settings.checkFidInfo(apipClient, br);
            
            if(cid!=null){
            String userPriKeyCipher = configure.getMainCidInfoMap().get(fid).getPriKeyCipher();
                CidInfo cidInfo = CidInfo.fromCid(cid, userPriKeyCipher);
                settings.getConfig().getMainCidInfoMap().put(cidInfo.getId(), cidInfo);
                Configure.saveConfig();
                if (cid.getCid() == null) {
                    if (fch.Inputer.askIfYes(br, "No CID yet. Set CID?")) {
                        setCid(fid, userPriKeyCipher, bestHeight, symKey, apipClient, br);
                    }
                }

                if (cid.getMaster() == null) {
                    if (fch.Inputer.askIfYes(br, "No master yet. Set master for this FID?")) {
                        setMaster(fid, userPriKeyCipher, bestHeight, symKey, apipClient, br);
                    }
                }
            }
        }
        return settings;
    }


    public static Settings startTool(String toolName,
                                       Map<String, Object> settingMap, BufferedReader br, Object[] modules) {
        Configure.loadConfig(br);
        Configure configure = Configure.checkPassword(br);
        if(configure == null) return null;
        byte[] symKey = configure.getSymKey();

        Settings settings = Settings.loadSettings(null, toolName);

        if(settings == null) {
            settings = new Settings(configure,toolName);
            settings.setModules(modules);
            if(settingMap != null) settings.setSettingMap(settingMap);
            settings.checkSetting(br);
        }

        settings.initiateTool(toolName, symKey, configure, br);
        return settings;
    }

    public static Settings startServer(Service.ServiceType serverType,
                                       Map<String, Object> settingMap, List<String> apiList, Object[] modules, Handler.HandlerType[] runningHandlers, BufferedReader br) {
        // Load config info from the file of config.json
        Configure.loadConfig(br);
        Configure configure = Configure.checkPassword(br);
        if(configure == null) return null;
        byte[] symKey = configure.getSymKey();
        while(true) {
            String sid = configure.chooseSid(serverType);

            Settings settings =null;
            if(sid!=null)settings = Settings.loadSettings(null, sid);
            if (settings == null) {
                settings = new Settings(configure, serverType, settingMap, modules, runningHandlers);
//                settings.checkSetting(br);
            }

            settings.initiateServer(sid, symKey, configure,apiList);
            if(settings.getService()!=null){
                return settings;
            }
            System.out.println("Try again.");
        }
    }
    public static Settings startMuteServer(String serverName, Map<String,Object> settingMap, BufferedReader br, Object[] modules) {
        Configure.loadConfig(br);

        Configure configure = Configure.checkPassword(br);
        if(configure==null)return null;
        byte[] symKey = configure.getSymKey();
            //Load the local settings from the file of localSettings.json
        Settings settings = Settings.loadSettings(null, serverName);

        if (settings == null) settings = new Settings(configure, null, settingMap, modules, null);
        //Check necessary APIs and set them if anyone can't be connected.
        settings.initiateMuteServer(serverName, symKey, configure);
        return settings;
    }

}
