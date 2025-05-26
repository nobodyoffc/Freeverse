package app;

import config.Settings;
import config.Configure;
import core.crypto.Decryptor;

import static ui.Inputer.askIfYes;

public abstract class FcApp {
    private Settings settings;

    public byte[] requestPrikey(){
        while(true){
            if(Configure.checkPassword(settings.getBr(),settings.getSymkey(),settings.getConfig())){
                byte[] priKey = Decryptor.decryptPrikey(settings.getMyPrikeyCipher(),settings.getSymkey());
                if(priKey!=null)return priKey;
            }
            if(settings.getBr()!=null && !askIfYes(settings.getBr(),"Wrong password. Try again?"))
                return null;
        }
    }

    public static boolean verifyPassword(Settings settings){
        return Configure.checkPassword(settings.getBr(),settings.getSymkey(),settings.getConfig());
    }
    public void close() {
        settings.close();
    }

    public Settings getSettings() {
        return settings;
    }

    public void setSettings(Settings settings) {
        this.settings = settings;
    }
}
