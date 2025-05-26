package test;

import config.Settings;
import clients.ApipClient;
import data.feipData.Service;
import data.feipData.serviceParams.TalkParams;
import utils.ObjectUtils;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static constants.Strings.N_PRICE;

@SuppressWarnings("unused")
public class TalkUdpServer {

    public static final List<DatagramSocket> sockets = new ArrayList<>();
    public static Map<String,List<Socket>> fidSocketsMap;
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
    private Settings settings;

    public TalkUdpServer(Settings settings, Service service, byte[] accountPriKey, ApipClient apipClient, JedisPool jedisPool) {
        this.settings = settings;
        this.service = service;
        sid = service.getId();
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
            nPriceMap = ObjectUtils.convertToLongMap(nPriceStrMap);
        }

    }

    public void start() {
        // Create a thread pool
        // Get the number of available processors
        int availableCores = Runtime.getRuntime().availableProcessors();

        // For I/O-bound tasks, you can multiply by a factor
        int poolSize = availableCores * 5;
        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
        try (DatagramSocket socket = new DatagramSocket(port)) {
            byte[] buf = new byte[4096];
            while (true) {
                // Receive data
                DatagramPacket datagramPacket = new DatagramPacket(buf, buf.length);
                // Process the packet data
                byte[] receivedData = new byte[datagramPacket.getLength()];
                System.arraycopy(datagramPacket.getData(), datagramPacket.getOffset(), receivedData, 0, datagramPacket.getLength());
                System.out.println("Received byte array of length: " + receivedData.length);
                // Handle the data in a separate thread
                executor.execute(new WorkerThread(receivedData));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
