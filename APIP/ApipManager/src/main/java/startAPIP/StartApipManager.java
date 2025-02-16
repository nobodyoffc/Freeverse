package startAPIP;


import apip.apipData.WebhookInfo;
import appTools.Starter;
import appTools.Inputer;
import appTools.Menu;
import constants.FieldNames;
import handlers.AccountHandler;
import handlers.Handler;
import tools.EsTools;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import configure.Configure;
import server.ApipApiNames;
import fch.fchData.Address;
import feip.feipData.Service;
import feip.feipData.serviceParams.ApipParams;
import nasa.NaSaRpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
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
//	private static MempoolScanner mempoolScanner =null;
//	private static Counter counter=null;
//	private static Pusher pusher = null;
//	private static MempoolCleaner mempoolCleaner=null;


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
		sid = service.getSid();
		params = ObjectTools.objectToClass(service.getParams(),ApipParams.class);

		//Prepare API clients
		esClient = (ElasticsearchClient) settings.getClient(Service.ServiceType.ES);
		jedisPool = (JedisPool) settings.getClient(Service.ServiceType.REDIS);
		naSaRpcClient = (NaSaRpcClient) settings.getClient(Service.ServiceType.NASA_RPC);

		Configure.checkWebConfig(settings);

		//Check indices in ES
		checkApipIndices(esClient);

		AccountHandler accountHandler = (AccountHandler) settings.getHandler(Handler.HandlerType.ACCOUNT);
//		accountHandler.start();
//		WebhookHandler webhookHandler = (WebhookHandler) settings.getHandler(Handler.HandlerType.WEBHOOK);
//		webhookHandler.startPusherThread();


		// settings.startScanThread(webhookHandler.new PushWebhookDataRunnable(webhookHandler));
//		//However, swap indices and APIs should be moved to SwapHall service from APIP service.
//		checkSwapIndices(esClient);
//
//		//Check user balance
//		checkUserBalance(sid,jedisPool,esClient,br);
//
//		Order.setNPrices(sid, ApiNames.ApipApiList,jedisPool,br,false);
//
//		Rewarder.checkRewarderParams(sid,params,jedisPool,br);
//
//		startCounterThread(symKey, settings,params);
//
//		startMempoolScan();



//		if(counter.isRunning().get()) System.out.println("Order scanner is running...");
//		if(mempoolScanner!=null && mempoolScanner.getRunning().get()) System.out.println("Mempool scanner is running...");
//		if(mempoolCleaner!=null && mempoolCleaner.getRunning().get()) System.out.println("Mempool cleaner is running...");

//		startPusher(esClient);
//		if(pusher!=null && pusher.isRunning().get()) System.out.println("Webhook pusher is running.");
//		System.out.println();

		while(true) {
			Menu menu = new Menu();

			ArrayList<String> menuItemList = new ArrayList<>();

			menuItemList.add("Manage service");
//			menuItemList.add("Manage order");
//			menuItemList.add("Manage balance");
//			menuItemList.add("Manage reward");
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
//				case 2 -> new OrderManager(service, counter, br, esClient, jedisPool).menu();
//				case 3 -> new BalanceManager(service, br, esClient,jedisPool).menu();
//				case 4 -> manageReward(sid, params,esClient,naSaRpcClient, jedisPool, br);
				case 2 -> accountHandler.menu(br);
				case 3 -> new IndicesApip(esClient,br).menu();
				case 4 -> repairAddress();
				case 5 -> Order.setNPrices(sid, ApipApiNames.apiList,jedisPool,br,true);
				case 6 -> settings.setting(symKey, br, serverType);
				case 0 -> {
					if (close()) return;
				}
				default -> {}
			}
		}
	}

	private static boolean close() throws IOException {
		// if (counter != null && counter.isRunning().get())
		// 	System.out.println("Order scanner is running.");
		// if (mempoolScanner != null && mempoolScanner.getRunning().get())
		// 	System.out.println("Mempool scanner is running.");
		// if (mempoolCleaner != null && mempoolCleaner.getRunning().get())
		// 	System.out.println("Mempool cleaner is running.");
		// if (pusher != null && pusher.isRunning().get()) System.out.println("Webhook pusher is running.");

		System.out.println("Do you want to quit? 'q' to quit.");
		String input = br.readLine();
		if ("q".equals(input)) {
			// if (counter != null) counter.close();
			// if (pusher != null) pusher.shutdown();
			// if (mempoolScanner != null) mempoolScanner.shutdown();
			// if (mempoolCleaner != null) mempoolCleaner.shutdown();
			// if (counter == null || !counter.isRunning().get())
			// 	System.out.println("Order scanner is set to stop.");
			// if (mempoolScanner == null || !mempoolScanner.getRunning().get())
			// 	System.out.println("Mempool scanner is set to stop.");
			// if (mempoolCleaner == null || !mempoolCleaner.getRunning().get())
			// 	System.out.println("Mempool cleaner is set to stop.");
			// if (pusher == null || !pusher.isRunning().get())
			// 	System.out.println("Webhook pusher is set to stop.");
			br.close();
			settings.close();
			System.out.println("Exited, see you again.");
			System.exit(0);
			return true;
		}
		return false;
	}

//	private static void startCounterThread(byte[] symKey, Settings settings, Params params) {
//		byte[] priKey = Settings.getMainFidPriKey(symKey, settings);
//		counter = new Counter(settings,priKey, params);
//		Thread thread = new Thread(counter);
//		thread.start();
//	}

	private static void repairAddress() {
		List<String> addrList = Inputer.inputStringList(br, "Input address list:", 0);
		try {
			EsTools.MgetResult<Address> result = EsTools.getMultiByIdList(esClient, ADDRESS, addrList, Address.class);
			List<Address> addressList = result.getResultList();

			addrList = addressList.stream().map(Address::getFid).collect(Collectors.toList());
			Address.makeAddress(addressList,esClient);
			BulkResponse bulkResponse = EsTools.bulkWriteList(esClient, ADDRESS, addressList, addrList, Address.class);
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

//	private static void manageReward(String sid, Params params, ElasticsearchClient esClient, NaSaRpcClient naSaRpcClient,JedisPool jedisPool, BufferedReader br) {
//		RewardManager rewardManager = new RewardManager(sid, params.getDealer(),null,esClient, naSaRpcClient,jedisPool, br);
//		rewardManager.menu(params.getConsumeViaShare(),params.getOrderViaShare());
//	}


//	private static void startMempoolClean(ElasticsearchClient esClient) {
//		mempoolCleaner = new MempoolCleaner((String) settings.getSettingMap().get(LISTEN_PATH), esClient,jedisPool);
//		Thread thread = new Thread(mempoolCleaner);
//		thread.start();
//	}

//	private static void startPusher(ElasticsearchClient esClient){
//		String listenPath = (String) settings.getSettingMap().get(LISTEN_PATH);
//		pusher = new Pusher(sid,listenPath, esClient);
//		Thread thread3 = new Thread(pusher);
//		thread3.start();
//
//		log.debug("Webhook pusher is running.");
//	}

//	private static void startMempoolScan()  {
//		startMempoolClean(esClient);
//		mempoolScanner = new MempoolScanner(naSaRpcClient,esClient,jedisPool);
//		Thread thread1 = new Thread(mempoolScanner);
//		thread1.start();
//	}

	public static String getNameOfService(String name) {
		String finalName;
		try(Jedis jedis = StartApipManager.jedisPool.getResource()) {
			finalName = Settings.addSidBriefToName(sid,name).toLowerCase();
		}
		return finalName;
	}
}

