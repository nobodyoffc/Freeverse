package appTools;

import static clients.FeipClient.setCid;
import static clients.FeipClient.setMaster;
import static constants.Strings.DOT_JSON;
import static constants.Strings.SETTINGS;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.*;

import apip.apipData.CidInfo;
import clients.apipClient.ApipClient;
import configure.Configure;
import configure.ServiceType;
import fch.Wallet;
import javaTools.FileTools;
import javaTools.JsonTools;

public class Starter {

    public static Settings startClient(String clientName, String[] serviceAliases, Map<String, Object> settingMap, BufferedReader br)  {
        //Load config info from the file of config.json
        Configure.loadConfig(br);
        Configure configure = Configure.checkPassword(br);
        if(configure ==null)return null;
        byte[] symKey = configure.getSymKey();
    
        String fid = configure.chooseMainFid(symKey);

        Settings settings;

        try {
            String fileName = FileTools.makeFileName(fid, clientName, SETTINGS, DOT_JSON);
            settings = JsonTools.readObjectFromJsonFile(Configure.getConfDir(), fileName, Settings.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if(settings ==null) {
            settings = new Settings(configure);
            if(serviceAliases!=null)settings.setServiceAliases(serviceAliases);
            if(settingMap!=null)settings.setSettingMap(settingMap);
        }

        fid = settings.initiateClient(fid, clientName, symKey,configure, br);

        ApipClient apipClient = (ApipClient) settings.getClient(ServiceType.APIP);

        Long bestHeight = new Wallet(apipClient).getBestHeight();
        CidInfo fidInfo = settings.checkFidInfo(apipClient, br);
        String userPriKeyCipher = configure.getFidCipherMap().get(fid);

        if(fidInfo !=null && fidInfo.getCid()==null){
            if(fch.Inputer.askIfYes(br,"No CID yet. Set CID?")){
                setCid(fid, userPriKeyCipher, bestHeight, symKey,apipClient,br);
            }
        }

        if(fidInfo !=null && fidInfo.getMaster()==null){
            if(fch.Inputer.askIfYes(br,"No master yet. Set master for this FID?")){
                setMaster(fid, userPriKeyCipher, bestHeight, symKey,apipClient,br);
            }
        }

        return settings;
    }

    public static Settings startServer(ServiceType serverType, String[] serviceAliases, Map<String,Object> settingMap, BufferedReader br) {
        Configure.loadConfig(br);

        Configure configure = Configure.checkPassword(br);
        if(configure==null)return null;
        byte[] symKey = configure.getSymKey();
        while(true) {
            String sid = configure.chooseSid(serverType);
            //Load the local settings from the file of localSettings.json
            Settings settings=null;
            if(sid!=null)
                try {
                    String fileName = FileTools.makeFileName(null, sid, SETTINGS, DOT_JSON);
                    settings = JsonTools.readObjectFromJsonFile(Configure.getConfDir(), fileName, Settings.class);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

            if (settings == null) settings = new Settings(configure, serverType,serviceAliases,settingMap);
            //Check necessary APIs and set them if anyone can't be connected.
            settings.initiateServer(sid, symKey, configure);
            if(settings.getService()!=null){
                return settings;
            }
            System.out.println("Try again.");
        }
    }
    public static Settings startMuteServer(String serverName, String[] serviceAliases, Map<String,Object> settingMap, BufferedReader br) {
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

        if (settings == null) settings = new Settings(configure, null,serviceAliases,settingMap);
        //Check necessary APIs and set them if anyone can't be connected.
        settings.initiateMuteServer(serverName, symKey, configure);
        return settings;
    }

}
