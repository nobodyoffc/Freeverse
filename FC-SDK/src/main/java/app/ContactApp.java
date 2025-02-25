package app;

import appTools.Menu;
import appTools.Settings;
import appTools.Starter;
import handlers.ContactHandler;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class ContactApp {

    public static void main(String[] args) {
        Menu.welcome(ContactHandler.name);

        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        Settings settings = Starter.startClient(ContactHandler.name, null, br, ContactHandler.modules);

        if(settings==null)return;

        ContactHandler contactHandler = new ContactHandler(settings);

        contactHandler.freshOnChainContacts(br);

        contactHandler.menu(br, true);
    }
} 