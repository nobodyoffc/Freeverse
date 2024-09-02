package start;

import appTools.Menu;
import clients.apipClient.ApipClient;
import configure.Configure;
import crypto.old.EccAes256K1P7;
import javaTools.BytesTools;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class startWorld {
    private static BufferedReader br;
    public static ApipClient apipClient;
    private static WorldSettings worldSettings;
    public static void main(String[] args) {
        br = new BufferedReader(new InputStreamReader(System.in));
        Menu.welcome("World");

        //Load config info from the file of config.json
        Configure.loadConfig(br);
        Configure configure = Configure.checkPassword(br);
        byte[] symKey = configure.getSymKey();

        String fid = worldSettings.chooseFid(configure,br,symKey);
        //Need a chat service and a disk service.
        worldSettings = WorldSettings.loadFromFile(fid,WorldSettings.class);
        if(worldSettings==null)worldSettings=new WorldSettings(configure,br);
        worldSettings.initiateServer(fid, symKey,configure, br);

        String priKeyCipher = worldSettings.getFidPriKeyCipherMap().get(fid);
        byte[] priKey = EccAes256K1P7.decryptJsonBytes(priKeyCipher,symKey);
        System.out.println(fid+" is ready.");
        Menu menu = new Menu();
        menu.setName("World");
        menu.add("home","wallet","them","post","build","team","chain","world","general");
        while (true) {
            menu.show();
            int choice = menu.choose(br);
            switch (choice) {
                case 1 -> home(br);//id,safe,disk
                case 2 -> wallet(br);//cash,token,proof,swap
                case 3 -> them(br);//contact,mail,chat,group
                case 4 -> post(br);//statement,post
                case 5 -> build(br);//nid,protocol,code,service,app
                case 6 -> team(br);//team,multiSign
                case 7 -> chain(br);//cash,tx,block,chain
                case 8 -> general(br);//total,general
                case 9 -> worldSettings.setting(symKey, br, null);
                case 0 -> {
                    close(priKey,br);
                    return;
                }
            }
        }
    }
    public static void home(BufferedReader br){
        System.out.println("home...");
    }
    public static void wallet(BufferedReader br){
        System.out.println("assets...");
    }
    public static void chain(BufferedReader br){
        System.out.println("chain...");
    }
    public static void build(BufferedReader br){
        System.out.println("build...");
    }
    public static void post(BufferedReader br){
        System.out.println("post...");
    }
    public static void team(BufferedReader br){
        System.out.println("team...");
    }
    public static void general(BufferedReader br){
        System.out.println("world...");
    }
    public static void them(BufferedReader br){
        System.out.println("them...");
    }
    public static void token(BufferedReader br){
        System.out.println("them...");
    }

    public static void close(byte[] priKey, BufferedReader br){
        System.out.println("close...");
        BytesTools.clearByteArray(priKey);
        try {
            br.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
