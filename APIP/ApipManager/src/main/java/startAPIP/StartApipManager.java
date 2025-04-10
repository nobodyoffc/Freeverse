package startAPIP;


import apip.apipData.WebhookInfo;
import appTools.Starter;
import appTools.Inputer;
import appTools.Menu;
import constants.FieldNames;
import fcData.AutoTask;
import fch.fchData.Cid;
import handlers.AccountHandler;
import handlers.Handler;
import utils.EsUtils;
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
import utils.FchUtils;
import utils.ObjectUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

import static appTools.Settings.*;
import static appTools.Settings.AVATAR_ELEMENTS_PATH;
import static appTools.Settings.AVATAR_PNG_PATH;
import static appTools.Settings.LISTEN_PATH;
import static constants.Constants.*;
import static constants.Constants.SEC_PER_DAY;
import static constants.IndicesNames.CID;
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
			Service.ServiceType.REDIS,
			Service.ServiceType.NASA_RPC,
			Service.ServiceType.ES,
			Handler.HandlerType.MEMPOOL,
			Handler.HandlerType.CASH,
			Handler.HandlerType.ACCOUNT,
			Handler.HandlerType.NONCE,
			Handler.HandlerType.SESSION,
			Handler.HandlerType.WEBHOOK
    };

	public static void main(String[] args) {
		Map<String,Object>  settingMap = new HashMap<> ();
		settingMap.put(Settings.FORBID_FREE_API,false);
		settingMap.put(Settings.WINDOW_TIME,DEFAULT_WINDOW_TIME);
		settingMap.put(LISTEN_PATH,System.getProperty(UserHome)+"/fc_data/blocks");
		settingMap.put(AVATAR_ELEMENTS_PATH,System.getProperty(UserDir)+"/avatar/elements");
		settingMap.put(AVATAR_PNG_PATH,System.getProperty(UserDir)+"/avatar/png");
		settingMap.put(AccountHandler.DISTRIBUTE_DAYS,AccountHandler.DEFAULT_DISTRIBUTE_DAYS);
		settingMap.put(AccountHandler.MIN_DISTRIBUTE_BALANCE,AccountHandler.DEFAULT_MIN_DISTRIBUTE_BALANCE);
		settingMap.put(AccountHandler.DEALER_MIN_BALANCE,AccountHandler.DEFAULT_DEALER_MIN_BALANCE);

		List<AutoTask> autoTaskList = new ArrayList<>();
		autoTaskList.add(new AutoTask(Handler.HandlerType.MEMPOOL, "checkMempool", (String)settingMap.get(LISTEN_PATH)));
		autoTaskList.add(new AutoTask(Handler.HandlerType.WEBHOOK, "pushWebhookData", (String)settingMap.get(LISTEN_PATH)));
		autoTaskList.add(new AutoTask(Handler.HandlerType.NONCE, "removeTimeOutNonce", SEC_PER_DAY));
		autoTaskList.add(new AutoTask(Handler.HandlerType.ACCOUNT, "updateIncome", (String)settingMap.get(Settings.LISTEN_PATH)));
		autoTaskList.add(new AutoTask(Handler.HandlerType.ACCOUNT, "distribute", 10*SEC_PER_DAY));
		autoTaskList.add(new AutoTask(Handler.HandlerType.ACCOUNT, "saveMapsToLocalDB", SEC_PER_DAY));

		Menu.welcome("APIP Manager");

		br = new BufferedReader(new InputStreamReader(System.in));
		settings = Starter.startServer(serverType, settingMap, ApipApiNames.apiList, modules, br, autoTaskList);
		if(settings==null)return;

		byte[] symKey = settings.getSymKey();
		service =settings.getService();
		sid = service.getId();
		params = ObjectUtils.objectToClass(service.getParams(),ApipParams.class);

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
			Menu menu = new Menu("APIP Manager", () -> close(br));

			menu.add("Manage service", () -> new ApipManager(service,null,br,symKey,ApipParams.class).menu());
			menu.add("Manage account", () -> accountHandler.menu(br, false));
			menu.add("Manage indices", () -> new IndicesApip(esClient,br).menu());
			menu.add("Repair address", () -> repairAddress());
			menu.add("Reset nPrice", () -> Order.setNPrices(sid, ApipApiNames.apiList,jedisPool,br,true));
			menu.add("Settings", () -> settings.setting(br, serverType));

			menu.showAndSelect(br);
		}
	}



	private static void close(BufferedReader br)  {
		try {
			System.out.println("Do you want to quit? 'q' to quit.");
			String input = br.readLine();
		if ("q".equals(input)) {
			br.close();
			settings.close();
			System.out.println("Exited, see you again.");
			System.exit(0);
			}
		} catch (IOException e) {
			log.error("Failed to close resources", e);
		}
	}

	private static void repairAddress() {
		List<String> addrList = Inputer.inputStringList(br, "Input address list:", 0);
		try {
			EsUtils.MgetResult<Cid> result = EsUtils.getMultiByIdList(esClient, CID, addrList, Cid.class);
			List<Cid> cidList = result.getResultList();

			addrList = cidList.stream().map(Cid::getId).collect(Collectors.toList());
			FchUtils.makeAddress(cidList,esClient);
			BulkResponse bulkResponse = EsUtils.bulkWriteList(esClient, CID, cidList, addrList, Cid.class);
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
		EsUtils.checkEsIndices(esClient,nameMappingList);
	}

	public static void checkSwapIndices(ElasticsearchClient esClient) {
		Map<String,String> nameMappingList = new HashMap<>();
		nameMappingList.put(Settings.addSidBriefToName(sid,SWAP_STATE), SwapStateData.swapStateJsonStr);
		nameMappingList.put(Settings.addSidBriefToName(sid,SWAP_LP), SwapLpData.swapLpMappingJsonStr);
		nameMappingList.put(Settings.addSidBriefToName(sid,SWAP_FINISHED), SwapAffair.swapFinishedMappingJsonStr);
		nameMappingList.put(Settings.addSidBriefToName(sid,SWAP_PENDING), SwapPendingData.swapPendingMappingJsonStr);
		nameMappingList.put(Settings.addSidBriefToName(sid,SWAP_STATE), SwapStateData.swapStateJsonStr);
		EsUtils.checkEsIndices(esClient,nameMappingList);

	}

	public static String getNameOfService(String name) {
		return 	Settings.addSidBriefToName(sid,name).toLowerCase();
	}
}

