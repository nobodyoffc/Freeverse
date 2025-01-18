package nettyClient;

import appTools.Settings;
import appTools.Starter;
import clients.DiskClient;
import clients.TalkClient;
import configure.ServiceType;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class Talk{

    private Settings settings;

    private String clientName= ServiceType.TALK.name();
    private String[] serviceAliases = new String[]{ServiceType.APIP.name(),ServiceType.TALK.name()};
    private Map<String,Object> settingMap = new HashMap<>();

    public static void main(String[] args) {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        Talk talk = new Talk();

        talk.settings = Starter.startClient(talk.clientName, talk.serviceAliases, talk.getSettingMap(), br);
        if(talk.settings == null) return;

        TalkClient talkClient = (TalkClient) talk.settings.getApiAccount(ServiceType.TALK).getClient();
        talkClient.setDiskClient((DiskClient) talk.settings.getClient(ServiceType.DISK));

        try {
            talkClient.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Settings getSettings() {
        return settings;
    }

    public void setSettings(Settings settings) {
        this.settings = settings;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public String[] getServiceAliases() {
        return serviceAliases;
    }

    public void setServiceAliases(String[] serviceAliases) {
        this.serviceAliases = serviceAliases;
    }

    public Map<String, Object> getSettingMap() {
        return settingMap;
    }

    public void setSettingMap(Map<String, Object> settingMap) {
        this.settingMap = settingMap;
    }
}
