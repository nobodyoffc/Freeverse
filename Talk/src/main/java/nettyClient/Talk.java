package nettyClient;

import appTools.Settings;
import appTools.Starter;
import clients.DiskClient;
import clients.TalkClient;
import feip.feipData.Service;
import handlers.Handler;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class Talk{

    private Settings settings;

    private String clientName= Service.ServiceType.TALK.name();
    private Service.ServiceType[] serviceAliases = new Service.ServiceType[]{Service.ServiceType.APIP, Service.ServiceType.TALK};
    public static final Object[] modules = new Object[]{
            Service.ServiceType.APIP,
            Service.ServiceType.TALK,
            Handler.HandlerType.CID,
            Handler.HandlerType.CASH,
            Handler.HandlerType.SESSION,
            Handler.HandlerType.MAIL,
            Handler.HandlerType.CONTACT,
            Handler.HandlerType.GROUP,
            Handler.HandlerType.TEAM,
            Handler.HandlerType.HAT,
            Handler.HandlerType.DISK,
            Handler.HandlerType.TALK_ID,
            Handler.HandlerType.TALK_UNIT
    };

    private Map<String,Object> settingMap = new HashMap<>();

    public static void main(String[] args) {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        Talk talk = new Talk();

        talk.settings = Starter.startClient(talk.clientName,
                talk.getSettingMap(), br, modules);
        if(talk.settings == null) return;

        TalkClient talkClient = (TalkClient) talk.settings.getApiAccount(Service.ServiceType.TALK).getClient();
        talkClient.setDiskClient((DiskClient) talk.settings.getClient(Service.ServiceType.DISK));

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

    public Service.ServiceType[] getServiceAliases() {
        return serviceAliases;
    }

    public void setServiceAliases(Service.ServiceType[] serviceAliases) {
        this.serviceAliases = serviceAliases;
    }

    public Map<String, Object> getSettingMap() {
        return settingMap;
    }

    public void setSettingMap(Map<String, Object> settingMap) {
        this.settingMap = settingMap;
    }
}
