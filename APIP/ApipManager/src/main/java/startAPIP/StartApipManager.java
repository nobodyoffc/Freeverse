package startAPIP;


import apip.apipData.WebhookInfo;
import appTools.Inputer;
import appTools.Menu;
import clients.esClient.EsTools;
import clients.redisClient.RedisTools;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import configure.ServiceType;
import configure.Configure;
import constants.ApiNames;
import constants.Strings;
import fch.fchData.Address;
import feip.feipData.Service;
import feip.feipData.serviceParams.ApipParams;
import feip.feipData.serviceParams.Params;
import mempool.MempoolCleaner;
import mempool.MempoolScanner;
import nasa.NaSaRpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.Counter;
import settings.Settings;
import server.balance.BalanceInfo;
import server.balance.BalanceManager;
import server.order.Order;
import server.order.OrderManager;
import server.reward.RewardInfo;
import server.reward.RewardManager;
import server.reward.Rewarder;
import swap.SwapAffair;
import swap.SwapLpData;
import swap.SwapPendingData;
import swap.SwapStateData;
import webhook.Pusher;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;

import static constants.IndicesNames.ORDER;
import static constants.IndicesNames.WEBHOOK;
import static constants.IndicesNames.*;
import static constants.Strings.*;
import static server.Counter.checkUserBalance;

public class StartApipManager {

	private static final Logger log = LoggerFactory.getLogger(StartApipManager.class);
	public static Service service;
	private static ElasticsearchClient esClient = null;
	private static MempoolScanner mempoolScanner =null;
	private static Counter counter=null;
	private static Pusher pusher = null;
	private static MempoolCleaner mempoolCleaner=null;


	private static BufferedReader br;
	private static IndicesApip indicesAPIP;
	public static JedisPool jedisPool;
	public static NaSaRpcClient naSaRpcClient;
	private static String sid;
	public static ApipParams params;
	private static ApipManagerSettings settings;
	public static final ServiceType serviceType = ServiceType.APIP;

	public static void main(String[] args)throws Exception{
		br = new BufferedReader(new InputStreamReader(System.in));

		//Load config info from the file
		Configure.loadConfig(br);
		Configure configure = Configure.checkPassword(br);
		byte[] symKey = configure.getSymKey();
		while(true) {
			sid = configure.chooseSid(serviceType);
			//Load the local settings from the file of localSettings.json
			settings = ApipManagerSettings.loadFromFile(sid, ApipManagerSettings.class);//new ApipClientSettings(configure,br);
			if (settings == null) settings = new ApipManagerSettings(configure);
			//Check necessary APIs and set them if anyone can't be connected.
			service = settings.initiateServer(sid, symKey, configure, br);
			if(service!=null)break;
			System.out.println("Try again.");
		}
		sid = service.getSid();
		params = (ApipParams) service.getParams();

		//Prepare API clients
		esClient = (ElasticsearchClient) settings.getEsAccount().getClient();
		jedisPool = (JedisPool) settings.getRedisAccount().getClient();
		naSaRpcClient = (NaSaRpcClient) settings.getNasaAccount().getClient();

		Configure.checkWebConfig(configure.getPasswordName(),sid,configure, settings,symKey, serviceType,jedisPool,br);

		//Check indices in ES
		checkApipIndices(esClient);

		//However, swap indices and APIs should be moved to SwapHall service from APIP service.
		checkSwapIndices(esClient);

		//Check user balance
		checkUserBalance(sid,jedisPool,esClient,br);

		Order.setNPrices(sid, ApiNames.ApipApiList,jedisPool,br,false);

		Rewarder.checkRewarderParams(sid,params,jedisPool,br);

		startCounterThread(symKey, settings,params);

		startMempoolScan();

		startPusher(esClient);

		if(counter.isRunning().get()) System.out.println("Order scanner is running...");
		if(mempoolScanner!=null && mempoolScanner.getRunning().get()) System.out.println("Mempool scanner is running...");
		if(mempoolCleaner!=null && mempoolCleaner.getRunning().get()) System.out.println("Mempool cleaner is running...");
		if(pusher!=null && pusher.isRunning().get()) System.out.println("Webhook pusher is running.");
		System.out.println();

		while(true) {
			Menu menu = new Menu();

			ArrayList<String> menuItemList = new ArrayList<>();

			menuItemList.add("Manage service");
			menuItemList.add("Manage order");
			menuItemList.add("Manage balance");
			menuItemList.add("Manage reward");
			menuItemList.add("Manage indices");
			menuItemList.add("Repair address");
			menuItemList.add("Settings");

			menu.add(menuItemList);
			menu.setName("APIP Manager");
			menu.show();

			int choice = menu.choose(br);
			switch (choice) {
				case 1 -> new ApipManager(service,null,br,symKey,ApipParams.class).menu();
				case 2 -> new OrderManager(service, counter, br, esClient, jedisPool).menu();
				case 3 -> new BalanceManager(service, br, esClient,jedisPool).menu();
				case 4 -> manageReward(sid, params,esClient,naSaRpcClient, jedisPool, br);
				case 5 -> manageIndices();
				case 6 -> repairAddress();
				case 7 -> settings.setting(symKey, br, serviceType);
				case 0 -> {
					if (counter != null && counter.isRunning().get())
						System.out.println("Order scanner is running.");
					if (mempoolScanner != null && mempoolScanner.getRunning().get())
						System.out.println("Mempool scanner is running.");
					if (mempoolCleaner != null && mempoolCleaner.getRunning().get())
						System.out.println("Mempool cleaner is running.");
					if (pusher != null && pusher.isRunning().get()) System.out.println("Webhook pusher is running.");
					System.out.println("Do you want to quit? 'q' to quit.");
					String input = br.readLine();
					if ("q".equals(input)) {
						if (mempoolScanner != null) mempoolScanner.shutdown();
						if (counter != null) counter.close();
						if (mempoolCleaner != null) mempoolCleaner.shutdown();
						if (pusher != null) pusher.shutdown();
						br.close();
						if (counter == null || !counter.isRunning().get())
							System.out.println("Order scanner is set to stop.");
						if (mempoolScanner == null || !mempoolScanner.getRunning().get())
							System.out.println("Mempool scanner is set to stop.");
						if (mempoolCleaner == null || !mempoolCleaner.getRunning().get())
							System.out.println("Mempool cleaner is set to stop.");
						if (pusher == null || !pusher.isRunning().get())
							System.out.println("Webhook pusher is set to stop.");
						System.out.println("Exited, see you again.");
						System.exit(0);
						return;
					}
				}
				default -> {}
			}
		}
	}

	private static void startCounterThread(byte[] symKey, Settings settings, Params params) {
		byte[] priKey = Settings.getMainFidPriKey(symKey, settings);
		counter = new Counter(settings,priKey, params);
		Thread thread = new Thread(counter);
		thread.start();
	}

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
		nameMappingList.put(Settings.addSidBriefToName(sid,BALANCE), BalanceInfo.MAPPINGS);
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

	private static void manageReward(String sid, Params params, ElasticsearchClient esClient, NaSaRpcClient naSaRpcClient,JedisPool jedisPool, BufferedReader br) {
		RewardManager rewardManager = new RewardManager(sid, params.getDealer(),null,esClient, naSaRpcClient,jedisPool, br);
		rewardManager.menu(params.getConsumeViaShare(),params.getOrderViaShare());
	}

	private static void checkPublicSessionKey() throws IOException {
		try(Jedis jedis = jedisPool.getResource()) {
			if (jedis.hget(Settings.addSidBriefToName(sid,FID_SESSION_NAME), PUBLIC) == null) {
				System.out.println("Public sessionKey for getFreeService API is null. Set it? 'y' to set.");
				String input = StartApipManager.br.readLine();
				if ("y".equals(input)) {
					setPublicSessionKey(br);
				}
			}
		}
	}

	static void setPublicSessionKey(BufferedReader br) {
		try(Jedis jedis = StartApipManager.jedisPool.getResource()) {
			setPublicSessionKey();
			String balance = jedis.hget(Settings.addSidBriefToName(sid, BALANCE), PUBLIC);
			System.out.println("The balance of public session is: " + balance + ". \nWould you reset it? Input a number satoshi to set. Enter to skip.");
			while (true) {
				try {
					String num = br.readLine();
					if ("".equals(num)) return;
					Long.parseLong(num);
					jedis.hset(Settings.addSidBriefToName(sid, BALANCE), PUBLIC, num);
					break;
				} catch (Exception ignore) {
					System.out.println("It's not a integer. Input again:");
				}
			}
		}
	}


	private static void setPublicSessionKey() {
		SecureRandom secureRandom = new SecureRandom();
		byte[] randomBytes = new byte[32];
		secureRandom.nextBytes(randomBytes);
		String sessionKey = HexFormat.of().formatHex(randomBytes);
		String oldSession = null;
		try(Jedis jedis = StartApipManager.jedisPool.getResource()) {
			try {
				oldSession = jedis.hget(Settings.addSidBriefToName(sid,Strings.FID_SESSION_NAME), PUBLIC);
			} catch (Exception ignore) {
			}

			jedis.hset(Settings.addSidBriefToName(sid,Strings.FID_SESSION_NAME), PUBLIC, sessionKey.substring(0, 12));

			jedis.select(1);
			try {
				jedis.del(oldSession);
			} catch (Exception ignore) {
			}

			jedis.hset(sessionKey.substring(0, 12), SESSION_KEY, sessionKey);
			jedis.hset(sessionKey.substring(0, 12), FID, PUBLIC);
			jedis.select(0);
			System.out.println("Public session key set into redis: " + sessionKey);
		}
	}

	private static void manageIndices() throws IOException, InterruptedException {
		indicesAPIP.menu();
	}

	private static void startMempoolClean(ElasticsearchClient esClient) {
		mempoolCleaner = new MempoolCleaner(settings.getListenPath(), esClient,jedisPool);
		Thread thread = new Thread(mempoolCleaner);
		thread.start();
	}

	private static void startPusher(ElasticsearchClient esClient) throws IOException {
		String listenPath = settings.getListenPath();
		pusher = new Pusher(sid,listenPath, esClient);
		Thread thread3 = new Thread(pusher);
		thread3.start();

		log.debug("Webhook pusher is running.");
	}

	private static void startMempoolScan()  {
		startMempoolClean(esClient);
		mempoolScanner = new MempoolScanner(naSaRpcClient,esClient,jedisPool);
		Thread thread1 = new Thread(mempoolScanner);
		thread1.start();
	}

	public static String getNameOfService(String name) {
		String finalName;
		try(Jedis jedis = StartApipManager.jedisPool.getResource()) {
			finalName = Settings.addSidBriefToName(sid,name).toLowerCase();
		}
		return finalName;
	}

	private static void checkServiceParams() {
		try(Jedis jedis = jedisPool.getResource()) {
			if (jedis.hget(Settings.addSidBriefToName(sid,Strings.PARAMS) , Strings.ACCOUNT) == null) {
				writeParamsToRedis(service,jedis);
			}
		}
	}

	private static void writeParamsToRedis(Service service,Jedis jedis) {

		ApipParams params = (ApipParams) service.getParams();
		String paramsKey = Settings.addSidBriefToName(service.getSid(),PARAMS);

		RedisTools.writeToRedis(params,paramsKey,jedis,ApipParams.class);

		System.out.println("Service parameters has been wrote into redis.");
	}
}

