package startFEIP;

import appTools.Menu;
import appTools.Settings;
import appTools.Starter;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.json.JsonData;

import constants.IndicesNames;
import feip.feipData.Service;
import utils.FileUtils;
import utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static constants.Constants.UserHome;
import static constants.FieldNames.LAST_HEIGHT;
import static constants.FieldNames.LAST_INDEX;
import static startFEIP.IndicesFEIP.createAllIndices;
import static startFEIP.IndicesFEIP.deleteAllIndices;

public class StartFEIP {
	public static long CddCheckHeight=3000000;
	public static long CddRequired=1;

	private static final Logger log = LoggerFactory.getLogger(StartFEIP.class);


	private final static String serverName = "FEIP";

	public static final Object[] modules = new Object[]{
			Service.ServiceType.ES
	};

	public static void main(String[] args) {
		Menu.welcome(serverName);
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

		Map<String,Object>  settingMap = new HashMap<> ();
		settingMap.put(Settings.OP_RETURN_PATH, FileUtils.getUserDir()+"/opreturn");
		settingMap.put(Settings.LISTEN_PATH,settingMap.get(Settings.OP_RETURN_PATH));

		Settings settings = Starter.startMuteServer(serverName, settingMap, br, modules,null);
		if(settings ==null)return;
		String opReturnJsonPath = (String) settings.getSettingMap().get(Settings.OP_RETURN_PATH);

		//Prepare API clients
		ElasticsearchClient esClient = (ElasticsearchClient) settings.getClient(Service.ServiceType.ES);

		Menu menu = new Menu("FEIP Parser");
		menu.add("Start New Parse from file", () -> startNewParse(opReturnJsonPath, br, esClient));
		menu.add("Restart from interruption", () -> restartFromFile(esClient, opReturnJsonPath));
		menu.add("Manual start from a height", () -> restartSinceHeight(opReturnJsonPath, br, esClient));
		menu.add("Reparse ID list", () -> reparseIdList(br, esClient));
		menu.add("Config", () -> settings.setting(br, null));

		menu.showAndSelect(br);
	}

	private static void reparseIdList(BufferedReader br, ElasticsearchClient esClient)  {
		System.out.println("Input the name of ES index:");
		try{
			String index = br.readLine();
			System.out.println("Input the ID list in compressed Json string:");
			String idListJsonStr = br.readLine();
			List<String> idList = JsonUtils.listFromJson(idListJsonStr,String.class);
			FileParser fileParser = new FileParser();
			fileParser.reparseIdList(esClient, index, idList);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void restartSinceHeight(String opReturnJsonPath, BufferedReader br, ElasticsearchClient esClient)  {
		System.out.println("Input the height that parsing begin with: ");
		long bestHeight;
		try{

			while (true) {
				String input = br.readLine();
					bestHeight = Long.parseLong(input);
					break;
			}
			manualRestartFromFile(esClient, opReturnJsonPath, bestHeight);
		}catch (Exception e){
			System.out.println("\nInput the number of the height:");
		}
	}

	private static void startNewParse(String opReturnJsonPath, BufferedReader br, ElasticsearchClient esClient)  {
		System.out.println("Start from 0, all indices will be deleted. Do you want? y or n:");
		try {
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
		}catch (Exception e){
			e.printStackTrace();
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
	}

	private static void restartFromFile(ElasticsearchClient esClient, String path)  {
		try {
		SearchResponse<ParseMark> result = esClient.search(s->s
				.index(IndicesNames.FEIP_MARK)
				.size(1)
				.sort(s1->s1
						.field(f->f
								.field(LAST_INDEX).order(SortOrder.Desc)
								.field(LAST_HEIGHT).order(SortOrder.Desc)
								)
						)
				, ParseMark.class);
			
		ParseMark parseMark = result.hits().hits().get(0).source();

		if (parseMark == null) throw new AssertionError();
		JsonUtils.printJson(parseMark);
		
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
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("restartFromFile.");
	}

	private static void manualRestartFromFile(ElasticsearchClient esClient, String path, long height) throws Exception {
		
		SearchResponse<ParseMark> result = esClient.search(s->s
				.index(IndicesNames.FEIP_MARK)
				.query(q->q.range(r->r.field(LAST_HEIGHT).lte(JsonData.of(height))))
				.size(1)
				.sort(s1->s1
						.field(f->f
								.field(LAST_INDEX).order(SortOrder.Desc)
								.field(LAST_HEIGHT).order(SortOrder.Desc)))
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

