package server;

import config.Settings;
import clients.ApipClient;
import clients.DiskClient;
import constants.CodeMessage;
import core.crypto.Decryptor;
import core.crypto.KeyTools;
import data.fcData.TalkUnit;
import handlers.TalkUnitManager;
import handlers.*;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import data.feipData.Service;
import data.feipData.serviceParams.TalkParams;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import clients.NaSaClient.NaSaRpcClient;
import utils.BytesUtils;
import redis.clients.jedis.JedisPool;
import utils.FchUtils;

import java.io.BufferedReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


public class TalkServer {

    private final ConcurrentHashMap<String, Set<Channel>> fidChannelsMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Channel,String> channelFidMap = new ConcurrentHashMap<>();
//    public static Map<Channel,Map<String,Integer>> channelPendingContentsMap;
    private static boolean isRunning=false;
//    public static String sid = null;

    public static final String[] chargeType = new String[]{
            TalkUnit.DataType.TEXT.name(),
            TalkUnit.DataType.HAT.name(),
            TalkUnit.DataType.BYTES.name(),
            TalkUnit.DataType.REPLY.name()
    };
    private final Service service;
    private final ApipClient apipClient;
    private final DiskClient diskClient;
    private final ElasticsearchClient esClient;
    private final SessionManager sessionHandler;
    private final CashManager cashHandler;
    private final AccountManager accountHandler;
    private final HatManager hatHandler;
    private final DiskManager diskHandler;
    private final NaSaRpcClient nasaClient;
    private final TalkUnitManager talkUnitHandler;
    private final TeamManager teamHandler;
    private final GroupManager groupHandler;

    private final JedisPool jedisPool;
    private static int port;
    public static Map<String, Long> nPriceMap;
    private final Settings settings;
    private final long price;
//    private MapQueue<String,byte[]> fidSessionKeyMapQueue;
//    private final MapQueue<byte[], FcSession> keyNameSessionKeyMapQueue;
    private final String dealer;
    private final byte[] dealerPrikey;
    private final BufferedReader br;
    private long dealerMinBalance;
    private TalkParams talkParams;

    private final ConcurrentHashMap<String, TalkUnit> pendingSentMap = new ConcurrentHashMap<>();
    public TalkServer(Settings settings, double price) {
        this.br = settings.getBr();
        this.settings = settings;
        this.price = FchUtils.coinToSatoshi(price);
        this.service = settings.getService();
        this.talkParams = (TalkParams)this.service.getParams();
        this.dealer = talkParams.getDealer();
        
        // Get clients from settings
        this.apipClient = (ApipClient) settings.getClient(Service.ServiceType.APIP);
        this.diskClient = (DiskClient) settings.getClient(Service.ServiceType.DISK);
        this.esClient = (ElasticsearchClient) settings.getClient(Service.ServiceType.ES);
        this.jedisPool = (JedisPool) settings.getClient(Service.ServiceType.REDIS);
        this.nasaClient = (NaSaRpcClient) settings.getClient(Service.ServiceType.NASA_RPC);

        // Get handlers from settings
        this.hatHandler = (HatManager) settings.getManager(Manager.ManagerType.HAT);
        this.sessionHandler = (SessionManager) settings.getManager(Manager.ManagerType.SESSION);
        this.cashHandler = (CashManager) settings.getManager(Manager.ManagerType.CASH);
        this.diskHandler = (DiskManager) settings.getManager(Manager.ManagerType.DISK);
        this.talkUnitHandler = (TalkUnitManager) settings.getManager(Manager.ManagerType.TALK_UNIT);
        this.teamHandler = (TeamManager) settings.getManager(Manager.ManagerType.TEAM);
        this.groupHandler = (GroupManager) settings.getManager(Manager.ManagerType.GROUP);
        this.accountHandler = (AccountManager) settings.getManager(Manager.ManagerType.ACCOUNT);

        this.dealerMinBalance = (Long) settings.getSettingMap().get(Settings.DEALER_MIN_BALANCE);

        try {
            URL url1 = new URL(talkParams.getUrlHead());
            port = url1.getPort();
        } catch (MalformedURLException e) {
            System.out.println("Failed to get the URL from:" + talkParams.getUrlHead());
        }

        dealerPrikey = Decryptor.decryptPrikey(settings.getMyPrikeyCipher(), settings.getSymkey());
    }

    public enum ApiName {
        ASK_KEY("askKey"),
        ASK_ROOM_INFO("askRoomInfo"),
        ASK_HISTORY("askHistory"),
        ASK_DATA("askData"),
        ASK_HAT("askHat"),
        SEND_TEXT("sendText"),
        REPLY("reply"),
        QUERY("query");

        private final String value;

        ApiName(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    public void start() {
        try {
            System.out.println("Starting talk server on port " + port);
            EventLoopGroup bossGroup = new NioEventLoopGroup(1);
            EventLoopGroup workerGroup = new NioEventLoopGroup();
            TalkServer talkServer = this;
            try {
                ServerBootstrap bootstrap = new ServerBootstrap()
                        .group(bossGroup, workerGroup)
                        .channel(NioServerSocketChannel.class)
                        .childHandler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel ch) {
                                ChannelPipeline pipeline = ch.pipeline();
                                pipeline.addLast(new IdleStateHandler(60, 0, 0));
                                pipeline.addLast("handler", new TalkServerHandler(talkServer));
                            }
                        });
                ChannelFuture f = bootstrap.bind(port).sync();
                isRunning=true;
                System.out.println("Chat server started on port " + getPort());
                f.channel().closeFuture().sync();
            } finally {
                bossGroup.shutdownGracefully();
                workerGroup.shutdownGracefully();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public int pay(String payerFid, Map<String, Long> paymentMap) {

        // Extract payment details from request data
        if (paymentMap == null || paymentMap.isEmpty()) {
            System.out.println(CodeMessage.Msg1011DataNotFound);
            return CodeMessage.Code1011DataNotFound;
        }

        Long sum = 0L;
        TalkUnitSender talkUnitSender = new TalkUnitSender(this);
        // Process each payment
        for (Map.Entry<String, Long> entry : paymentMap.entrySet()) {
            String recipientFid = entry.getKey();
            Long amount = entry.getValue();

            // Validate FIDs and amount
            if (!KeyTools.isGoodFid(recipientFid) || amount <= 0) {
                continue;
            }

            // Deduct from sender
            Long newBalance = accountHandler.updateUserBalance(payerFid, -amount);
            if (newBalance == null || newBalance < 0) {
                System.out.println(CodeMessage.Msg1004InsufficientBalance);
                return CodeMessage.Code1004InsufficientBalance;
            }
            sum += amount;

            // Add to recipient
            accountHandler.updateUserBalance(recipientFid, amount);

            String msg = payerFid+" paid "+amount+" to "+recipientFid+".";
            // Send notification to recipient
//            FcSession recipientSession = sessionHandler.getSessionById(recipientFid);
//            if(recipientSession==null){
//                String pubkey = getPubkey(recipientFid,sessionHandler,null,null,apipClient);
//                recipientSession = sessionHandler.addNewSession(recipientFid,pubkey);
//            }
            TalkUnit talkUnit = TalkUnit.createNotifyUnit(dealer,recipientFid, msg);
            talkUnitSender.sendTalkUnit(talkUnit,fidChannelsMap.get(payerFid));
        }
        String msgToPayer = "You paid "+sum+" to "+paymentMap.keySet()+".";

        TalkUnit talkUnit = TalkUnit.createNotifyUnit(dealer,payerFid, msgToPayer);
        talkUnitSender.sendTalkUnit(talkUnit,fidChannelsMap.get(payerFid));
        return CodeMessage.Code0Success;
    }

    public void close(){
        BytesUtils.clearByteArray(dealerPrikey);
    }

    public int getPort() {
        return port;
    }

    public long getPrice() {
        return price;
    }

    public Service getService() {
        return service;
    }

    public Settings getSettings() {
        return settings;
    }

    public Map<String, Long> getnPriceMap() {
        return nPriceMap;
    }

    public Map<String, Set<Channel>> getFidChannelsMap() {
        return fidChannelsMap;
    }

    public Map<Channel, String> getChannelFidMap() {
        return channelFidMap;
    }

    public JedisPool getJedisPool() {
        return jedisPool;
    }

    public static boolean isRunning() {
        return isRunning;
    }

    public static void setRunning(boolean isRunning) {
        TalkServer.isRunning = isRunning;
    }

    public ApipClient getApipClient() {
        return apipClient;
    }

    public ElasticsearchClient getEsClient() {
        return esClient;
    }

    public static void setPort(int port) {
        TalkServer.port = port;
    }

    public void setTalkParams(TalkParams talkParams) {
        this.talkParams = talkParams;
    }

    public static void setnPriceMap(Map<String, Long> nPriceMap) {
        TalkServer.nPriceMap = nPriceMap;
    }

    public SessionManager getFcSessionHandler() {
        return sessionHandler;
    }

    public byte[] getDealerPrikey() {
        return dealerPrikey;
    }

    public CashManager getCashClient() {
        return cashHandler;
    }

    public AccountManager getAccountHandler() {
        return accountHandler;
    }

    public String getDealer() {
        return dealer;
    }

    public HatManager getHatHandler() {
        return hatHandler;
    }

    public static boolean isIsRunning() {
        return isRunning;
    }

    public static void setIsRunning(boolean isRunning) {
        TalkServer.isRunning = isRunning;
    }

    public SessionManager getSessionHandler() {
        return sessionHandler;
    }

    public CashManager getCashHandler() {
        return cashHandler;
    }

    public NaSaRpcClient getNasaClient() {
        return nasaClient;
    }

    public DiskManager getDiskHandler() {
        return diskHandler;
    }

    public TalkUnitManager getTalkUnitHandler() {
        return talkUnitHandler;
    }

    public ConcurrentHashMap<String, TalkUnit> getPendingSentMap() {
        return pendingSentMap;
    }

    public DiskClient getDiskClient() {
        return diskClient;
    }

    public TeamManager getTeamHandler() {
        return teamHandler;
    }

    public GroupManager getGroupHandler() {
        return groupHandler;
    }

    public String[] getChargeType() {
        return chargeType;
    }

    public long getDealerMinBalance() {
        return dealerMinBalance;
    }

    public void setDealerMinBalance(long dealerMinBalance) {
        this.dealerMinBalance = dealerMinBalance;
    }

    public BufferedReader getBr() {
        return br;
    }
    public TalkParams getTalkParams() {
        return talkParams;
    }
}
