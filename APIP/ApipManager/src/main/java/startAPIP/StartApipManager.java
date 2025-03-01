package startAPIP;


import apip.apipData.WebhookInfo;
import appTools.Starter;
import appTools.Inputer;
import appTools.Menu;
import constants.FieldNames;
import fch.ParseTools;
import fch.fchData.Cid;
import handlers.AccountHandler;
import handlers.Handler;
import tools.EsTools;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import configure.Configure;
import server.ApipApiNames;
import feip.feipData.Service;
import feip.feipData.serviceParams.ApipParams;
import nasa.NaSaRpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPool;
import appTools.Settings;
import server.balance.BalanceInfo;
import server.order.Order;
import server.reward.RewardInfo;
import swap.SwapAffair;
import swap.SwapLpData;
import swap.SwapPendingData;
import swap.SwapStateData;
import tools.ObjectTools;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

import static appTools.Settings.*;
import static appTools.Settings.AVATAR_ELEMENTS_PATH;
import static appTools.Settings.AVATAR_PNG_PATH;
import static appTools.Settings.LISTEN_PATH;
import static constants.Constants.UserDir;
import static constants.Constants.UserHome;
import static constants.IndicesNames.ORDER;
import static constants.IndicesNames.WEBHOOK;
import static constants.IndicesNames.*;
import static constants.Strings.*;

public class StartApipManager {

	private static final Logger log = LoggerFactory.getLogger(StartApipManager.class);
	public static Service service;
	private static ElasticsearchClient esClient = null;

	private static BufferedReader br;
	public static JedisPool jedisPool;
	public static NaSaRpcClient naSaRpcClient;
	public static String sid;
	public static ApipParams params;
	private static Settings settings;
	public static final Service.ServiceType serverType = Service.ServiceType.APIP;

	public static final Object[] modules = new Object[]{
			Service.ServiceType.NASA_RPC,
			Service.ServiceType.REDIS,
			Service.ServiceType.ES,
			Handler.HandlerType.MEMPOOL,
			Handler.HandlerType.CASH,
			Handler.HandlerType.ACCOUNT,
			Handler.HandlerType.WEBHOOK
    };

	public static final Handler.HandlerType[] runningHandlers = new Handler.HandlerType[]{
		Handler.HandlerType.ACCOUNT,
		Handler.HandlerType.WEBHOOK,
		Handler.HandlerType.MEMPOOL
	};

	public static Map<String,Object>  settingMap = new HashMap<> ();

	static {
		settingMap.put(Settings.FORBID_FREE_API,false);
		settingMap.put(Settings.WINDOW_TIME,DEFAULT_WINDOW_TIME);
		settingMap.put(LISTEN_PATH,System.getProperty(UserHome)+"/fc_data/blocks");
		settingMap.put(AVATAR_ELEMENTS_PATH,System.getProperty(UserDir)+"/avatar/elements");
		settingMap.put(AVATAR_PNG_PATH,System.getProperty(UserDir)+"/avatar/png");
	}

	public static void main(String[] args)throws Exception{
		Menu.welcome("APIP Manager");
		br = new BufferedReader(new InputStreamReader(System.in));
		settings = Starter.startServer(serverType, settingMap, ApipApiNames.apiList, modules, runningHandlers, br);
		if(settings==null)return;

		byte[] symKey = settings.getSymKey();
		service =settings.getService();
		sid = service.getId();
		params = ObjectTools.objectToClass(service.getParams(),ApipParams.class);

		//Prepare API clients
		esClient = (ElasticsearchClient) settings.getClient(Service.ServiceType.ES);
		jedisPool = (JedisPool) settings.getClient(Service.ServiceType.REDIS);
		naSaRpcClient = (NaSaRpcClient) settings.getClient(Service.ServiceType.NASA_RPC);

		Configure.checkWebConfig(settings);

		//Check indices in ES
		checkApipIndices(esClient);
		checkSwapIndices(esClient);

		AccountHandler accountHandler = (AccountHandler) settings.getHandler(Handler.HandlerType.ACCOUNT);

		while(true) {
			Menu menu = new Menu();

			ArrayList<String> menuItemList = new ArrayList<>();

			menuItemList.add("Manage service");
			menuItemList.add("Manage account");
			menuItemList.add("Manage indices");
			menuItemList.add("Repair address");
			menuItemList.add("Reset nPrice");
			menuItemList.add("Settings");

			menu.add(menuItemList);
			menu.setTitle("APIP Manager");
			menu.show();

			int choice = menu.choose(br);
			switch (choice) {
				case 1 -> new ApipManager(service,null,br,symKey,ApipParams.class).menu();
				case 2 -> accountHandler.menu(br, false);
				case 3 -> new IndicesApip(esClient,br).menu();
				case 4 -> repairAddress();
				case 5 -> Order.setNPrices(sid, ApipApiNames.apiList,jedisPool,br,true);
				case 6 -> settings.setting(br, serverType);
				case 0 -> {
					if (close()) return;
				}
				default -> {}
			}
		}
	}

	private static boolean close() throws IOException {
		System.out.println("Do you want to quit? 'q' to quit.");
		String input = br.readLine();
		if ("q".equals(input)) {
			br.close();
			settings.close();
			System.out.println("Exited, see you again.");
			System.exit(0);
			return true;
		}
		return false;
	}

	private static void repairAddress() {
		List<String> addrList = Inputer.inputStringList(br, "Input address list:", 0);
		try {
			EsTools.MgetResult<Cid> result = EsTools.getMultiByIdList(esClient, CID, addrList, Cid.class);
			List<Cid> cidList = result.getResultList();

			addrList = cidList.stream().map(Cid::getId).collect(Collectors.toList());
			ParseTools.makeAddress(cidList,esClient);
			BulkResponse bulkResponse = EsTools.bulkWriteList(esClient, CID, cidList, addrList, Cid.class);
			boolean result1;
			if(bulkResponse!= null)result1 = !bulkResponse.errors();
			else result1 =  false;
			System.out.println("Making address: "+result1);
		} catch (Exception e) {
			System.out.println("Failed to repairAddress."+e.getMessage());
		}
	}

	private static void checkApipIndices(ElasticsearchClient esClient) {
		Map<String,String> nameMappingList = new HashMap<>();
		nameMappingList.put(Settings.addSidBriefToName(sid,ORDER), Order.MAPPINGS);
		nameMappingList.put(Settings.addSidBriefToName(sid, FieldNames.BALANCE), BalanceInfo.MAPPINGS);
		nameMappingList.put(Settings.addSidBriefToName(sid,REWARD), RewardInfo.MAPPINGS);
		nameMappingList.put(Settings.addSidBriefToName(sid,WEBHOOK), WebhookInfo.MAPPINGS);
		EsTools.checkEsIndices(esClient,nameMappingList);
	}

	public static void checkSwapIndices(ElasticsearchClient esClient) {
		Map<String,String> nameMappingList = new HashMap<>();
		nameMappingList.put(Settings.addSidBriefToName(sid,SWAP_STATE), SwapStateData.swapStateJsonStr);
		nameMappingList.put(Settings.addSidBriefToName(sid,SWAP_LP), SwapLpData.swapLpMappingJsonStr);
		nameMappingList.put(Settings.addSidBriefToName(sid,SWAP_FINISHED), SwapAffair.swapFinishedMappingJsonStr);
		nameMappingList.put(Settings.addSidBriefToName(sid,SWAP_PENDING), SwapPendingData.swapPendingMappingJsonStr);
		nameMappingList.put(Settings.addSidBriefToName(sid,SWAP_STATE), SwapStateData.swapStateJsonStr);
		EsTools.checkEsIndices(esClient,nameMappingList);

	}

	public static String getNameOfService(String name) {
		return 	Settings.addSidBriefToName(sid,name).toLowerCase();
	}
}

