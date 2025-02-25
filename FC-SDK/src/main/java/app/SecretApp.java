package app;

import appTools.Menu;
import appTools.Settings;
import appTools.Starter;
import handlers.SecretHandler;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class SecretApp {

    public static void main(String[] args) {

        Menu.welcome(SecretHandler.name);

        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        Settings settings = Starter.startClient(SecretHandler.name, null, br, SecretHandler.modules);

        if(settings==null)return;

        SecretHandler secretHandler = new SecretHandler(settings);

        secretHandler.freshOnChainSecrets(br);

        secretHandler.menu(br, true);
    }
}
