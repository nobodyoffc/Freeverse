package startTalkServer;

import appTools.Settings;
import clients.apipClient.ApipClient;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import configure.ServiceType;
import feip.feipData.Service;
import feip.feipData.serviceParams.TalkParams;
import javaTools.ObjectTools;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.*;

import static constants.Strings.N_PRICE;

public class TalkTcpServer {

    public final static Map<String, Set<Socket>> fidSocketsMap = new HashMap<>();
//    public static Map<String,List<String>> roomIdFidsMap;
//    public static Map<Socket,String> socketFidMap;
    public static Map<Socket,Map<String,Integer>> socketPendingContentsMap;
    private static boolean isRunning = true;
    public static String sid = null;

    private final Service service;
    private final ApipClient apipClient;
    private final ElasticsearchClient esClient;
    private final JedisPool jedisPool;
    private final byte[] dealerPriKey;
    private static String ip = null;
    private static int port;

    public static TalkParams talkParams;
    public static Map<String, Long> nPriceMap;
    private Settings settings;
    private final long price;
    private Map<String,byte[]> fidSessionKeyMap;

    public TalkTcpServer(Settings settings, Service service,long price, byte[] dealerPriKey) {
        this.settings = settings;
        this.service = service;
        sid = service.getSid();
        talkParams = (TalkParams)service.getParams();
        this.dealerPriKey = dealerPriKey;
        this.apipClient = (ApipClient) settings.getClient(ServiceType.APIP);
        this.esClient = (ElasticsearchClient) settings.getClient(ServiceType.ES);
        this.jedisPool = (JedisPool) settings.getClient(ServiceType.REDIS);
        try {
            URL url1 = new URL(talkParams.getUrlHead());
            ip = url1.getHost();
            port = url1.getPort();
        } catch (MalformedURLException e) {
            System.out.println("Failed to get the URL from:"+talkParams.getUrlHead());
        }

        try(Jedis jedis = jedisPool.getResource()){
            Map<String, String> nPriceStrMap = jedis.hgetAll(Settings.addSidBriefToName(sid, N_PRICE));
            nPriceMap = ObjectTools.convertToLongMap(nPriceStrMap);
        }
        this.price = price;
        this.fidSessionKeyMap=new HashMap<>();
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            serverSocket.setReuseAddress(true);  // Allow immediate reuse of the port
            while (isRunning) {
                System.out.println("Wait for client...");
                Socket socket = serverSocket.accept();
                System.out.println("New client is connected.");

                ServerTcpThread serverTcpThread = new ServerTcpThread(socket, fidSocketsMap, dealerPriKey, price, nPriceMap, service, this.settings,fidSessionKeyMap);
                serverTcpThread.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // Close all client sockets
            synchronized (fidSocketsMap) {
                for (String fid : fidSocketsMap.keySet()) {
                    try {
                        for(Socket socket:fidSocketsMap.get(fid))
                            socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                fidSocketsMap.clear();
            }
        }
    }
}
