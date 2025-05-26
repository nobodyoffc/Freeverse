package startFCH;

import ui.Menu;
import config.Settings;
import config.Starter;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import constants.Constants;
import constants.UpStrings;
import core.fch.OpReFileUtils;
import data.fchData.Block;
import data.feipData.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import parser.Preparer;
import utils.EsUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static constants.Constants.UserHome;


public class StartFCH {

    private static final Logger log = LoggerFactory.getLogger(StartFCH.class);


    public static final Object[] modules = new Object[]{
            Service.ServiceType.ES
    };

    public static Map<String,Object> settingMap = new HashMap<>();

    static {
        settingMap.put(Settings.LISTEN_PATH,System.getProperty(UserHome)+"/fc_data/blocks");
    }
    public static void main(String[] args)  {
        String name = "Freecash Chain Parser";
        Menu.welcome(name);

        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        Settings settings = Starter.startTool(UpStrings.CHAIN, settingMap, br, modules, null);
        if(settings==null) return;

        //Prepare API clients
        ElasticsearchClient esClient = (ElasticsearchClient) settings.getClient(Service.ServiceType.ES);
        log.debug("Freecash blockchain parser is starting...");
        checkOpReturnFile();

        Menu menu = new Menu(name, () -> handleExit(settings));

        menu.add("Start new parse", () -> handleNewParse(br, settings, esClient));
        menu.add("Restart from interruption", () -> handleRestart(br, esClient, settings));
        menu.add("Manually start from a height", () -> handleManualStart(br, esClient, settings));
        menu.add("Settings", () -> settings.setting(br, null));

        menu.showAndSelect(br);
    }

    private static void handleExit(Settings settings) {
        settings.close();
        System.out.println("Exited. See you again.");
    }

    private static void handleNewParse(BufferedReader br, Settings settings, ElasticsearchClient esClient) {
        try {
            System.out.println("Start from 0, all indices and opreturn*.byte will be deleted. Do you want? y or n:");
            String delete = br.readLine();
            if (delete.equals("y")) {
                System.out.println("Do you sure? y or n:");
                delete = br.readLine();
                if (delete.equals("y")) {
                    String blockDir = (String)settings.getSettingMap().get(Settings.LISTEN_PATH);
                    File blk = new File(blockDir, "blk00000.dat");
                    if (!blk.exists()) {
                        System.out.println("blk00000.dat isn't found in " + blockDir + ". Config the path:");
                        return;
                    }
                    deleteOpReFiles();
                    IndicesFCH.deleteAllIndices(esClient);
                    TimeUnit.SECONDS.sleep(3);
                    IndicesFCH.createAllIndices(esClient);
                    try {
                        new Preparer().prepare(esClient, blockDir, -1);
                    } catch (Exception e) {
                        restart(esClient, blockDir);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error during start new parse", e);
        }
    }

    private static void handleRestart(BufferedReader br, ElasticsearchClient esClient, Settings settings) {
        try {
            System.out.println("Do you sure to restart from bestHeight in ES? y or n:");
            String restart = br.readLine();
            if (restart.equals("y")) {
                String blockDir = (String)settings.getSettingMap().get(Settings.LISTEN_PATH);
                restart(esClient, blockDir);
            }
        } catch (IOException e) {
            log.error("Error during restart", e);
        }
    }

    private static void handleManualStart(BufferedReader br, ElasticsearchClient esClient, Settings settings) {
        try {
            long bestHeight;
            while (true) {
                System.out.println("Input the height you want to rolling back to:");
                String input = br.readLine();
                try {
                    bestHeight = Long.parseLong(input);
                    break;
                } catch (Exception e) {
                    System.out.println("Input the height you want to rolling back to:");
                }
            }
            String blockDir = (String)settings.getSettingMap().get(Settings.LISTEN_PATH);
            try {
                new Preparer().prepare(esClient, blockDir, bestHeight);
            } catch (Exception e) {
                restart(esClient, blockDir);
            }
        } catch (Exception e) {
            log.error("Error during manual start", e);
        }
    }

    private static void restart(ElasticsearchClient esClient,String blockDir) {
        long bestHeight;
        Block bestBlock;
        try {
            bestBlock = EsUtils.getBestBlock(esClient);
            if (bestBlock==null){
                log.error("Failed to get bestHeight wrong.");
                return;
            }
        } catch (IOException e) {
            log.error("Get bestHeight wrong.",e);
            return;
        }
        bestHeight = bestBlock.getHeight();

        log.debug("Restarting from BestHeight: " + (bestHeight - 1) + " ...");

        bestHeight = bestHeight - 1;

        try {
            new Preparer().prepare(esClient, blockDir, bestHeight);
        } catch (Exception e) {
            e.printStackTrace();
            restart(esClient,blockDir);
        }
    }

    private static void checkOpReturnFile() {

        Path path = Paths.get(Constants.OPRETURN_FILE_DIR);

        // Check if the directory exists
        if (!Files.exists(path)) {
            try {
                // Create the directory
                Files.createDirectories(path);
            } catch (Exception e) {
                log.error("Error creating opreturn directory: " + e.getMessage());
            }
        }
    }

    private static void deleteOpReFiles() {

        String fileName = Constants.OPRETURN_FILE_NAME;
        File file;

        while (true) {
            file = new File(Constants.OPRETURN_FILE_DIR,fileName);
            if (file.exists()) {
                boolean done = file.delete();
                if(!done)log.error("Failed to delete file:"+fileName);
                fileName = OpReFileUtils.getNextFile(fileName);
            } else break;
        }
    }
}
