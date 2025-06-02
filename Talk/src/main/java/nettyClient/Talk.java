package nettyClient;

import config.Settings;
import config.Starter;
import clients.DiskClient;
import clients.TalkClient;
import data.feipData.Service;
import handlers.Manager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Talk{

    private Settings settings;

    private String clientName= Service.ServiceType.TALK.name();
    private Service.ServiceType[] serviceAliases = new Service.ServiceType[]{Service.ServiceType.APIP, Service.ServiceType.TALK};

    private Map<String,Object> settingMap = new HashMap<>();

    public static void main(String[] args) {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        Talk talk = new Talk();

        List<data.fcData.Module> modules = new ArrayList<>();
        modules.add(new data.fcData.Module(Service.class.getSimpleName(),Service.ServiceType.APIP.name()));
        modules.add(new data.fcData.Module(Service.class.getSimpleName(),Service.ServiceType.TALK.name()));
        modules.add(new data.fcData.Module(Manager.class.getSimpleName(),Manager.ManagerType.CID.name()));
        modules.add(new data.fcData.Module(Manager.class.getSimpleName(),Manager.ManagerType.CASH.name()));
        modules.add(new data.fcData.Module(Manager.class.getSimpleName(),Manager.ManagerType.SESSION.name()));
        modules.add(new data.fcData.Module(Manager.class.getSimpleName(),Manager.ManagerType.MAIL.name()));
        modules.add(new data.fcData.Module(Manager.class.getSimpleName(),Manager.ManagerType.CONTACT.name()));
        modules.add(new data.fcData.Module(Manager.class.getSimpleName(),Manager.ManagerType.GROUP.name()));
        modules.add(new data.fcData.Module(Manager.class.getSimpleName(),Manager.ManagerType.TEAM.name()));
        modules.add(new data.fcData.Module(Manager.class.getSimpleName(),Manager.ManagerType.HAT.name()));
        modules.add(new data.fcData.Module(Manager.class.getSimpleName(),Manager.ManagerType.DISK.name()));
        modules.add(new data.fcData.Module(Manager.class.getSimpleName(),Manager.ManagerType.TALK_ID.name()));
        modules.add(new data.fcData.Module(Manager.class.getSimpleName(),Manager.ManagerType.TALK_UNIT.name()));

        talk.settings = Starter.startClient(talk.clientName,
                talk.getSettingMap(), br, modules, null);
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
