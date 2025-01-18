package startFEIP;

import appTools.Menu;
import appTools.Settings;
import appTools.Starter;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.json.JsonData;
import com.google.gson.Gson;
import configure.ServiceType;
import constants.IndicesNames;
import tools.JsonTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static constants.Constants.UserHome;
import static startFEIP.IndicesFEIP.createAllIndices;
import static startFEIP.IndicesFEIP.deleteAllIndices;

public class StartFEIP {
	public static long CddCheckHeight=3000000;
	public static long CddRequired=1;

	private static final Logger log = LoggerFactory.getLogger(StartFEIP.class);


	private final static String serverName = "FEIP Parser";
	public static String[] serviceAliases = new String[]{
			ServiceType.ES.name(),
	};

	public static Map<String,Object>  settingMap = new HashMap<> ();

	static {
		settingMap.put(Settings.LISTEN_PATH,System.getProperty(UserHome)+"/fc_data/blocks");
		settingMap.put(Settings.OP_RETURN_PATH,"/home/user1/freeverse/opreturn");
	}

	public static void main(String[] args)throws Exception{
		Menu.welcome(serverName);
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		Settings settings = Starter.startMuteServer(serverName, serviceAliases, settingMap, br);
		if(settings ==null)return;
		String opReturnJsonPath = (String) settingMap.get(Settings.OP_RETURN_PATH);
		byte[] symKey = settings.getSymKey();

		//Prepare API clients
		ElasticsearchClient esClient = (ElasticsearchClient) settings.getClient(ServiceType.ES);

		Menu menu = new Menu("FEIP Parser");

		ArrayList<String> menuItemList = new ArrayList<>();
		menuItemList.add("Start New Parse from file");
		menuItemList.add("Restart from interruption");
		menuItemList.add("Manual start from a height");
		menuItemList.add("Reparse ID list");
		menuItemList.add("Config");

		menu.add(menuItemList);

		System.out.println(" << FEIP parser >> \n");
		menu.show();
		int choice = menu.choose(br);
		switch (choice) {
			case 1 -> startNewParse(opReturnJsonPath, br, esClient);
			case 2 -> restartFromFile(esClient, opReturnJsonPath);
			case 3 -> restartSinceHeight(opReturnJsonPath, br, esClient);
			case 4 -> reparseIdList(br, esClient);
			case 5 -> settings.setting(symKey, br, null);
			case 0 -> settings.close();
			default -> {
			}
		}

	}

	private static void reparseIdList(BufferedReader br, ElasticsearchClient esClient) throws Exception {
		System.out.println("Input the name of ES index:");
		String index = br.readLine();
		System.out.println("Input the ID list in compressed Json string:");
		String idListJsonStr = br.readLine();
		Gson gson = new Gson();
		List idList = gson.fromJson(idListJsonStr, ArrayList.class);
		FileParser fileParser = new FileParser();
		fileParser.reparseIdList(esClient, index, idList);
	}

	private static void restartSinceHeight(String opReturnJsonPath, BufferedReader br, ElasticsearchClient esClient) throws Exception {
		System.out.println("Input the height that parsing begin with: ");
		long bestHeight;
		while (true) {
			String input = br.readLine();
			try{
				bestHeight = Long.parseLong(input);
				break;
			}catch (Exception e){
				System.out.println("\nInput the number of the height:");
			}
		}
		manualRestartFromFile(esClient, opReturnJsonPath, bestHeight);
	}

	private static void startNewParse(String opReturnJsonPath, BufferedReader br, ElasticsearchClient esClient) throws Exception {
		System.out.println("Start from 0, all indices will be deleted. Do you want? y or n:");
		String delete = br.readLine();
		if (delete.equals("y")) {
			System.out.println("Do you sure? y or n:");
			delete = br.readLine();
			if (delete.equals("y")) {

				System.out.println("Deleting indices...");
				deleteAllIndices(esClient);
				TimeUnit.SECONDS.sleep(3);

				System.out.println("Creating indices...");
				createAllIndices(esClient);
				TimeUnit.SECONDS.sleep(2);

				startNewFromFile(esClient, opReturnJsonPath);

			}
		}
	}

	private static void startNewFromFile(ElasticsearchClient esClient, String path) throws Exception {
		
		System.out.println("startNewFromFile.");
		
		FileParser fileParser = new FileParser();
		
		fileParser.setPath(path);
		fileParser.setFileName("opreturn0.byte");
		fileParser.setPointer(0);
		fileParser.setLastHeight(0);
		fileParser.setLastIndex(0);
		
		boolean isRollback = false;
		fileParser.parseFile(esClient, isRollback);
		// TODO Auto-generated method stub
	}

	private static void restartFromFile(ElasticsearchClient esClient, String path) throws Exception {
		
		SearchResponse<ParseMark> result = esClient.search(s->s
				.index(IndicesNames.FEIP_MARK)
				.size(1)
				.sort(s1->s1
						.field(f->f
								.field("lastIndex").order(SortOrder.Desc)
								.field("lastHeight").order(SortOrder.Desc)
								)
						)
				, ParseMark.class);

		ParseMark parseMark = result.hits().hits().get(0).source();

		if (parseMark == null) throw new AssertionError();
		JsonTools.printJson(parseMark);
		
		FileParser fileParser = new FileParser();
		
		fileParser.setPath(path);
		fileParser.setFileName(parseMark.getFileName());
		fileParser.setPointer(parseMark.getPointer());
		fileParser.setLength(parseMark.getLength());
		fileParser.setLastHeight(parseMark.getLastHeight());
		fileParser.setLastIndex(parseMark.getLastIndex());
		fileParser.setLastId(parseMark.getLastId());
		
		boolean isRollback = false;
		boolean error = fileParser.parseFile(esClient,isRollback);
		
		System.out.println("restartFromFile.");
	}

	private static void manualRestartFromFile(ElasticsearchClient esClient, String path, long height) throws Exception {
		
		SearchResponse<ParseMark> result = esClient.search(s->s
				.index(IndicesNames.FEIP_MARK)
				.query(q->q.range(r->r.field("lastHeight").lte(JsonData.of(height))))
				.size(1)
				.sort(s1->s1
						.field(f->f
								.field("lastIndex").order(SortOrder.Desc)
								.field("lastHeight").order(SortOrder.Desc)))
				, ParseMark.class);

		if (result.hits().total() == null) throw new AssertionError();
		if(result.hits().total().value()==0) {
			restartFromFile(esClient, path);
			return;
		}
		
		ParseMark parseMark = result.hits().hits().get(0).source();
		
		FileParser fileParser = new FileParser();
		
		fileParser.setPath(path);
		if (null == parseMark) throw new AssertionError();
		fileParser.setFileName(parseMark.getFileName());
		fileParser.setPointer(parseMark.getPointer());
		fileParser.setLength(parseMark.getLength());
		fileParser.setLastHeight(parseMark.getLastHeight());
		fileParser.setLastIndex(parseMark.getLastIndex());
		fileParser.setLastId(parseMark.getLastId());
		
		boolean isRollback = true;
		
		boolean error = fileParser.parseFile(esClient,isRollback);
		
		System.out.println("manualRestartFromFile");

	}
}

