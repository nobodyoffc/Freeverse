package startTalkServer;

import apip.apipData.RequestBody;
import apip.apipData.Session;
import clients.apipClient.ApipClient;
import clients.fcspClient.TalkItem;
import clients.redisClient.RedisTools;
import com.google.gson.Gson;
import constants.Constants;
import constants.FieldNames;
import constants.ReplyCodeMessage;
import constants.Strings;
import crypto.KeyTools;
import fcData.AlgorithmId;
import fcData.FcReplier;
import fcData.Signature;
import javaTools.Hex;
import javaTools.JsonTools;
import javaTools.ObjectTools;
import javaTools.http.AuthType;
import javaTools.http.HttpRequestMethod;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.Settings;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

import static apip.apipData.Session.getSessionFromJedis;
import static apip.apipData.Session.makeNewSession;
import static constants.Strings.*;
import static server.RequestChecker.*;

public class TalkServer {

    private static final List<Socket> sockets = new ArrayList<>();
    private static boolean isRunning = true;
    private static Map<String,List<Socket>> fidSocketsMap;
    private static Map<String,List<String>> roomIdFidsMap;
    private static Map<Socket,String> socketFidMap;
    private static Map<Socket,Map<String,Integer>> socketPendingContentsMap;
    private static long count;
    private final ApipClient apipClient;
    private final JedisPool jedisPool;
    private static String sid = null;
    private final byte[] waiterPriKey;

    public TalkServer(String sid1, byte[] waiterPriKey, ApipClient apipClient, JedisPool jedisPool) {
        sid = sid1;
        this.waiterPriKey = waiterPriKey;
        this.apipClient = apipClient;
        this.jedisPool = jedisPool;
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(3333)) {
            serverSocket.setReuseAddress(true);  // Allow immediate reuse of the port
            int count = 0;
            while (isRunning) {
                System.out.println("Wait for client...");
                Socket socket = serverSocket.accept();
                new ServerThread(socket, waiterPriKey, apipClient, jedisPool).start();
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

    static class ServerThread extends Thread {
        private final Socket socket;
        private final ApipClient apipClient;
        private final JedisPool jedisPool;
        private String fid;
        private String sessionKey;
        private final byte[]  waiterPriKey;

        public ServerThread(Socket socket, byte[] waiterPriKey, ApipClient apipClient, JedisPool jedisPool) {
            this.socket = socket;
            this.apipClient=apipClient;
            this.jedisPool = jedisPool;
            this.waiterPriKey =waiterPriKey;
        }

        public void run() {
            synchronized (sockets) {
                sockets.add(socket);
            }

            System.out.println("Clients number: " + sockets.size());
            String fid = "client" + (++count);
            System.out.println(fid + " connected.");
            try (BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                String talkItemStr;
                while ((talkItemStr = br.readLine()) != null) {
                    TalkItem talkItem = TalkItem.fromJson(talkItemStr);
                    switch (talkItem.getDataType()){
                        case TEXT, SIGNATURE,CIPHER,HAT,ASK_KEY,SHARE_KEY -> handleText(talkItemStr,talkItem,socket,apipClient, sessionKey);
                        case SIGN_IN -> handleSignIn(talkItem, waiterPriKey, socket, jedisPool);
//                        case CREAT_ROOM -> handleCreatRoom(talkItem);
//                        case CLOSE_ROOM -> handleCloseRoom(talkItem);
//                        case ADD_MEMBER -> handleAddMember(talkItem);
//                        case REMOVE_MEMBER -> handleQuitRoom(talkItem);
//                        case ROOM_INFO -> handleRoomInfo();
//                        case ASK_ROOM_INFO -> handleAskRoomInfo(talkItem);
//                        case UPDATE -> handleUpdate(talkItem);
//                        case EXIT -> handleExit(talkItem);
                        default -> {
                            continue;
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    socket.close();
                    synchronized (sockets) {
                        sockets.remove(socket);
                    }
                    System.out.println(fid + " disconnected. Clients number: " + sockets.size());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }



        private void handleSignIn(TalkItem talkItem, byte[] waiterPriKey, Socket socket, JedisPool jedisPool) {
            TalkItem talkItemReply = new TalkItem();
            TalkItem.ServerMsg serverMsg = new TalkItem.ServerMsg();
            talkItemReply.setData(JsonTools.toJson(serverMsg));

            FcReplier replier = new FcReplier();
            replier.setNonce(Long.valueOf(talkItem.getNonce()));

            Signature signature = new Signature().fromJson(talkItem.getData());
            Session session;
            try(Jedis jedis = jedisPool.getResource()) {
                Map<String, String> paramsMap = jedis.hgetAll(Settings.addSidBriefToName(sid, PARAMS));
                long windowTime = RedisTools.readHashLong(jedis, Settings.addSidBriefToName(sid, SETTINGS), WINDOW_TIME);
                boolean isSymSign = signature.getSymKeyName() != null;

                if (isSymSign) {
                    jedis.select(1);
                    session = getSession(signature.getSymKeyName(), jedis);
                    if(session==null || session.getSessionKey()==null){
                        reply(ReplyCodeMessage.Code1009SessionTimeExpired, null, null, AlgorithmId.BTC_EcdsaSignMsg_No1_NrC7, socket,talkItemReply, waiterPriKey, replier, jedis);
                        return;
                        }
                    fid = session.getFid();
                    this.sessionKey = session.getSessionKey();
                    signature.setSymKey(Hex.fromHex(this.sessionKey));
                    jedis.select(0);
                } else {
                    fid = signature.getFid();
                    signature.setFid(fid);
                }

                if (!signature.verify()) return;

                RequestBody requestBody = ObjectTools.objectToClass(signature.getMsg(), RequestBody.class);

                if (isBadBalance(sid, fid, null, jedis)) {
                    String data = "Send at lest " + paramsMap.get(MIN_PAYMENT) + " F to " + paramsMap.get(ACCOUNT) + " to buy the service #" + sid + ".";
                    reply(ReplyCodeMessage.Code1004InsufficientBalance, data, null, AlgorithmId.BTC_EcdsaSignMsg_No1_NrC7, socket,talkItemReply, waiterPriKey, replier, jedis);
                    return;
                }

                if (!sid.equals(requestBody.getSid())) {
                    Map<String,String> dataMap= new HashMap<>();
                    dataMap.put("Signed SID:", requestBody.getSid());
                    dataMap.put("Requested SID:",sid);
                    reply(ReplyCodeMessage.Code1025WrongSid, dataMap, null, AlgorithmId.BTC_EcdsaSignMsg_No1_NrC7, socket,talkItemReply, waiterPriKey, replier, jedis);
                    return;
                }

                if (isBadNonce(requestBody.getNonce(), windowTime, jedis)) {
                    reply(ReplyCodeMessage.Code1007UsedNonce, null, null, AlgorithmId.BTC_EcdsaSignMsg_No1_NrC7, socket,talkItemReply, waiterPriKey, replier, jedis);
                    return;
                }

                if (isBadTime(requestBody.getTime(), windowTime)) {
                    Map<String, String> dataMap = new HashMap<>();
                    dataMap.put("windowTime", String.valueOf(windowTime));
                    reply(ReplyCodeMessage.Code1006RequestTimeExpired, dataMap, null, AlgorithmId.BTC_EcdsaSignMsg_No1_NrC7, socket,talkItemReply, waiterPriKey, replier, jedis);
                    return;
                }
                byte[] waiterPubKey = KeyTools.priKeyToPubKey(waiterPriKey);
                if (signature.getSymKeyName() != null) {
                    reply(ReplyCodeMessage.Code0Success, null, null, AlgorithmId.BTC_EcdsaSignMsg_No1_NrC7, socket,talkItemReply, waiterPriKey, replier, jedis);
                }else {
                    Map<String, String> signInDataMap = ObjectTools.objectToMap(requestBody.getData(), String.class, String.class);
                    RequestBody.SignInMode mode =null;
                    if(signInDataMap!=null) {
                        String modeStr = signInDataMap.get(FieldNames.MODE);
                        mode = RequestBody.SignInMode.valueOf(modeStr);
                    }

                    String sessionDaysStr = paramsMap.get(SESSION_DAYS);
                    long sessionDays;
                    if(sessionDaysStr==null)sessionDays= Constants.DEFAULT_SESSION_DAYS;
                    else sessionDays = Long.parseLong(sessionDaysStr);

                    if ((!jedis.hexists(Settings.addSidBriefToName(sid, Strings.FID_SESSION_NAME), fid)) || RequestBody.SignInMode.REFRESH.equals(mode)) {
                        session = makeNewSession(sid, replier, Hex.toHex(waiterPubKey), jedis, fid, sessionDays);
                    } else {
                        session = getSessionFromJedis(sid, replier, Hex.toHex(waiterPubKey), jedis, fid, sessionDays);
                    }

                    if(session==null)return;
                    reply(ReplyCodeMessage.Code0Success, session, null, AlgorithmId.BTC_EcdsaSignMsg_No1_NrC7, socket,talkItemReply, waiterPriKey, replier, jedis);
                }
            }
        }

        private void handleText(String content, TalkItem talkItem, Socket socket, ApipClient apipClient, String key) {
            String fid = socketFidMap.get(socket);
            String roomId = talkItem.getTo();
            if(roomIdFidsMap.get(roomId)==null) {
                if (roomId.startsWith("F")) {
                    ArrayList<String> fidList = new ArrayList<>();
                    fidList.add(roomId.substring(0, roomId.indexOf("-")));
                    fidList.add(roomId.substring(roomId.indexOf("-") + 1));
                    roomIdFidsMap.put(roomId, fidList);
                } else if (roomId.startsWith("G")) {
                    String gid = roomId.substring(2);
                    Map<String, String[]> gidMembersMap = apipClient.groupMembers(HttpRequestMethod.POST, AuthType.FC_SIGN_BODY, gid);
                    roomIdFidsMap.put(gid, Arrays.stream(gidMembersMap.get(gid)).toList());
                } else if (roomId.startsWith("T")) {
                    String tid = roomId.substring(2);
                    Map<String, String[]> tidMembersMap = apipClient.teamMembers(HttpRequestMethod.POST, AuthType.FC_SIGN_BODY, tid);
                    roomIdFidsMap.put(tid, Arrays.stream(tidMembersMap.get(tid)).toList());
                }else if(Hex.isHexString(roomId)){
                    try {
                        pendingContent(content, socket);
                        askRoomInfo(talkItem, socket, key);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                if (!roomIdFidsMap.get(roomId).contains(fid)) return;
                broadcastMessage(content, talkItem.getTo());
            }
        }


        private void broadcastMessage(String message,String roomId) {
            for (String fid : roomIdFidsMap.get(roomId)) {
                for(Socket socket:fidSocketsMap.get(fid)) {
                    try {
                        OutputStreamWriter osw = new OutputStreamWriter(socket.getOutputStream());
                        osw.write(message + "\n");
                        osw.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
    private static void reply(int code, Socket socket, byte[] waiterPriKey,  TalkItem talkItem, FcReplier replier, Jedis jedis) {
        reply(code,null,null,AlgorithmId.BTC_EcdsaSignMsg_No1_NrC7,socket,talkItem, waiterPriKey, replier,jedis);
    }
    private static void reply(int code, Object data, String otherError, AlgorithmId alg, Socket socket, TalkItem talkItem, byte[] waiterPriKey, FcReplier replier, Jedis jedis) {
        String replierJson = replier.replyJson(code, otherError, data, jedis);
        Signature signature1 = new Signature().sign(replierJson,waiterPriKey, alg);
        TalkItem.ServerMsg serverMsg = new Gson().fromJson(talkItem.getData(), TalkItem.ServerMsg.class);
        serverMsg.setSign(signature1.toJson());
        try {
            OutputStreamWriter osw = new OutputStreamWriter(socket.getOutputStream());
            osw.write(talkItem.toJson() + "\n");
            osw.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void askRoomInfo(TalkItem fromTalkItem, Socket socket, String key) throws IOException {
        TalkItem toTalkItem = new TalkItem();
        toTalkItem.setDataType(TalkItem.DataType.ASK_ROOM_INFO);
        toTalkItem.setTo(fromTalkItem.getTo());
        toTalkItem.setNonce(fromTalkItem.getNonce());
        String json = toTalkItem.toJson();
        Signature sign = new Signature().sign(key, json.getBytes(),AlgorithmId.BTC_EcdsaSignMsg_No1_NrC7);
        toTalkItem.setData(JsonTools.toJson(sign));
        OutputStreamWriter osw = new OutputStreamWriter(socket.getOutputStream());
        osw.write(toTalkItem.getData()+ "\n");
        osw.flush();
    }

    private static void pendingContent(String content, Socket socket) {
        if (socketPendingContentsMap == null) socketPendingContentsMap = new HashMap<>();
        Map<String, Integer> contentTimesMap;
        if (socketPendingContentsMap.get(socket) == null) {
            contentTimesMap = new HashMap<>();
            contentTimesMap.put(content, 1);
            socketPendingContentsMap.put(socket, contentTimesMap);
        } else {
            contentTimesMap = socketPendingContentsMap.get(socket);
            contentTimesMap.put(content, contentTimesMap.get(content)+1);
            if(contentTimesMap.get(content)>3) {
                contentTimesMap.remove(content);
                return;
            }
            socketPendingContentsMap.put(socket, contentTimesMap);
        }
    }
}
