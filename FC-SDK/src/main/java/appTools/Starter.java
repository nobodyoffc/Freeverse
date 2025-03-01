package appTools;

import static clients.FeipClient.*;
import static constants.Strings.DOT_JSON;
import static constants.Strings.SETTINGS;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.*;

import fch.fchData.Cid;
import clients.ApipClient;
import configure.Configure;
import feip.feipData.Service;
import handlers.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.FileTools;
import tools.JsonTools;

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

        try {
            String fileName = FileTools.makeFileName(fid, clientName, SETTINGS, DOT_JSON);
            settings = JsonTools.readObjectFromJsonFile(Configure.getConfDir(), fileName, Settings.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if(settings == null) {
            settings = new Settings(configure,clientName);
            settings.setModules(modules);
            if(settingMap != null) settings.setSettingMap(settingMap);
        }

        // Initialize clients and handlers
        settings.initiateClient(fid, clientName, symKey, configure, br);

        ApipClient apipClient = (ApipClient) settings.getClient(Service.ServiceType.APIP);

        if(apipClient==null){
            log.error("Failed to fresh bestHeight due to the absence of apipClient, nasaClient, and esClient.");
            return settings;
        }
        long bestHeight = apipClient.bestHeight();//new Wallet(apipClient).getBestHeight();

        Cid fidInfo = settings.checkFidInfo(apipClient, br);
        String userPriKeyCipher = configure.getFidCipherMap().get(fid);

        if (fidInfo != null && fidInfo.getCid() == null) {
            if (fch.Inputer.askIfYes(br, "No CID yet. Set CID?")) {
                setCid(fid, userPriKeyCipher, bestHeight, symKey, apipClient, br);
            }
        }

        if (fidInfo != null && fidInfo.getMaster() == null) {
            if (fch.Inputer.askIfYes(br, "No master yet. Set master for this FID?")) {
                setMaster(fid, userPriKeyCipher, bestHeight, symKey, apipClient, br);
            }
        }
        return settings;
    }


    public static Settings startTool(String toolName,
                                       Map<String, Object> settingMap, BufferedReader br, Object[] modules) {
        // Load config info from the file of config.json

        Configure.loadConfig(br);
        Configure configure = Configure.checkPassword(br);
        if(configure == null) return null;
        byte[] symKey = configure.getSymKey();

        Settings settings;

        try {
            String fileName = FileTools.makeFileName(null, toolName, SETTINGS, DOT_JSON);
            settings = JsonTools.readObjectFromJsonFile(Configure.getConfDir(), fileName, Settings.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if(settings == null) {
            settings = new Settings(configure,toolName);
            settings.setModules(modules);
            if(settingMap != null) settings.setSettingMap(settingMap);
        }

        // Initialize clients and handlers
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
            //Load the local settings from the file of localSettings.json
            Settings settings=null;
            if(sid!=null)
                try {
                    String fileName = Settings.makeSettingsFileName(null,sid);//FileTools.makeFileName(null, sid, SETTINGS, DOT_JSON);
                    settings = JsonTools.readObjectFromJsonFile(Configure.getConfDir(), fileName, Settings.class);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

            if (settings == null) settings = new Settings(configure, serverType, settingMap, modules, runningHandlers);
            //Check necessary APIs and set them if anyone can't be connected.
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
        Settings settings=null;
        try {
            String fileName = FileTools.makeFileName(null, serverName, SETTINGS, DOT_JSON);
            settings = JsonTools.readObjectFromJsonFile(Configure.getConfDir(), fileName, Settings.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (settings == null) settings = new Settings(configure, null, settingMap, modules, null);
        //Check necessary APIs and set them if anyone can't be connected.
        settings.initiateMuteServer(serverName, symKey, configure);
        return settings;
    }

}
