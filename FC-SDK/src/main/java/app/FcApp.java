package app;

import appTools.Inputer;
import appTools.Settings;
import configure.Configure;
import crypto.Decryptor;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;

import static appTools.Inputer.askIfYes;

public class FcApp {
    private Settings settings;

    public static byte[] requestPriKey(Settings settings){
        while(true){
            if(Configure.checkPassword(settings.getBr(),settings.getSymKey(),settings.getConfig())){
                byte[] priKey = Decryptor.decryptPriKey(settings.getMyPriKeyCipher(),settings.getSymKey());
                if(priKey!=null)return priKey;
            }
            if(!askIfYes(settings.getBr(),"Wrong password. Try again?"))return null;
        }
    }

    public static boolean verifyPassword(Settings settings){
        return Configure.checkPassword(settings.getBr(),settings.getSymKey(),settings.getConfig());
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
