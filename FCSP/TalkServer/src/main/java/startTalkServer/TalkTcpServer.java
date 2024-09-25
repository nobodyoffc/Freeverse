package startTalkServer;

import clients.apipClient.ApipClient;
import feip.feipData.Service;
import feip.feipData.serviceParams.TalkParams;
import javaTools.ObjectTools;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import settings.Settings;
import settings.TalkServerSettings;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static constants.Strings.N_PRICE;

public class TalkTcpServer {

    public static final List<Socket> sockets = new ArrayList<>();
    public static Map<String, Set<Socket>> fidSocketsMap;
    public static Map<String,List<String>> roomIdFidsMap;
    public static Map<Socket,String> socketFidMap;
    public static Map<Socket,Map<String,Integer>> socketPendingContentsMap;
    private static boolean isRunning = true;
    public static String sid = null;

    private final Service service;
    private final ApipClient apipClient;
    private final JedisPool jedisPool;
    private final byte[] accountPriKey;
    private static String ip = null;
    private static int port;

    public static TalkParams talkParams;
    public static Map<String, Long> nPriceMap;
    private TalkServerSettings settings;
    private final long price;

    public TalkTcpServer(TalkServerSettings settings, Service service,long price, byte[] accountPriKey, ApipClient apipClient, JedisPool jedisPool) {
        this.settings = settings;
        this.service = service;
        sid = service.getSid();
        talkParams = (TalkParams)service.getParams();
        this.accountPriKey = accountPriKey;
        this.apipClient = apipClient;
        this.jedisPool = jedisPool;
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
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            serverSocket.setReuseAddress(true);  // Allow immediate reuse of the port
            while (isRunning) {
                System.out.println("Wait for client...");
                Socket socket = serverSocket.accept();
                System.out.println("New client is connected.");

                ServerTcpThread serverTcpThread = new ServerTcpThread(socket, fidSocketsMap, accountPriKey, price, nPriceMap, service, this.settings, apipClient, jedisPool);
                serverTcpThread.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // Close all client sockets
            synchronized (sockets) {
                for (Socket socket : sockets) {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                sockets.clear();
            }
        }
    }
}
