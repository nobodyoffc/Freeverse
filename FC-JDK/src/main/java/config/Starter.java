package config;

import static clients.FeipClient.*;

import java.io.BufferedReader;
import java.util.*;

import core.fch.Inputer;
import data.fcData.CidInfo;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import data.fcData.AutoTask;
import data.fchData.Cid;
import clients.ApipClient;
import data.feipData.Service;
import clients.NaSaClient.NaSaRpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Starter {
    protected static final Logger log = LoggerFactory.getLogger(Starter.class);


    public static Settings startClient(String clientName,
                                       Map<String, Object> settingMap, BufferedReader br, Object[] modules, List<AutoTask> autoTaskList) {
        // Load config info from the file of config.json

        Configure.loadConfig(br);
        Configure configure = Configure.checkPassword(br);
        if(configure == null) return null;
        byte[] symkey = configure.getSymkey();
    
        String fid = configure.chooseMainFid(symkey);
        Settings settings;

        settings = Settings.loadSettings(fid, clientName);

        if(settings == null)
            settings = new Settings(configure,clientName, modules, settingMap, autoTaskList);

        // Initialize clients and handlers
        settings.initiateClient(fid, clientName, symkey, configure, br);
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
                String userPrikeyCipher = configure.getMainCidInfoMap().get(fid).getPrikeyCipher();
                CidInfo cidInfo = CidInfo.fromCid(cid, userPrikeyCipher);
                settings.getConfig().getMainCidInfoMap().put(cidInfo.getId(), cidInfo);
                Configure.saveConfig();
                if (cid.getCid() == null) {
                    if (Inputer.askIfYes(br, "No CID yet. Set CID?")) {
                        setCid(fid, userPrikeyCipher, bestHeight, symkey, apipClient, br);
                    }
                }

                if (cid.getMaster() == null) {
                    if (Inputer.askIfYes(br, "No master yet. Set master for this FID?")) {
                        setMaster(fid, userPrikeyCipher, bestHeight, symkey, apipClient, br);
                    }
                }
            }
        }

        return settings;
    }


    public static Settings startTool(String toolName,
                                     Map<String, Object> settingMap, BufferedReader br, Object[] modules, List<AutoTask> autoTaskList) {
        Configure.loadConfig(br);
        Configure configure = Configure.checkPassword(br);
        if(configure == null) return null;
        byte[] symkey = configure.getSymkey();

        Settings settings = Settings.loadSettings(null, toolName);

        if(settings == null)
            settings = new Settings(configure,toolName, modules, settingMap, autoTaskList);

        settings.initiateTool(toolName, symkey, configure, br);
        return settings;
    }

    public static Settings startServer(Service.ServiceType serverType,
                                       Map<String, Object> settingMap, List<String> apiList, Object[] modules, BufferedReader br, List<AutoTask> autoTaskList) {
        // Load config info from the file of config.json
        Configure.loadConfig(br);
        Configure configure = Configure.checkPassword(br);
        if(configure == null) return null;
        byte[] symkey = configure.getSymkey();
        while(true) {
            String sid = configure.chooseSid(serverType);

            Settings settings =null;
            if(sid!=null)settings = Settings.loadSettings(null, sid);
            if (settings == null) {
                settings = new Settings(configure, serverType, settingMap, modules, autoTaskList);
            }

            settings.initiateServer(sid, symkey, configure,apiList);
            if(settings.getService()!=null){
                return settings;
            }
            System.out.println("Try again.");
        }
    }
    public static Settings startMuteServer(String serverName, Map<String,Object> settingMap, BufferedReader br, Object[] modules, List<AutoTask> autoTaskList) {
        Configure.loadConfig(br);

        Configure configure = Configure.checkPassword(br);
        if(configure==null)return null;
        byte[] symkey = configure.getSymkey();
            //Load the local settings from the file of localSettings.json
        Settings settings = Settings.loadSettings(null, serverName);

        if (settings == null) settings = new Settings(configure, null, settingMap, modules, autoTaskList);
        //Check necessary APIs and set them if anyone can't be connected.
        settings.initiateMuteServer(serverName, symkey, configure);
        return settings;
    }

}
