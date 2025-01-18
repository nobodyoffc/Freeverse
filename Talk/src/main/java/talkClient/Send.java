//package talkClient;
//
//import appTools.Settings;
//import fcData.TalkUnit;
//import clients.TalkClient;
//import tools.JsonTools;
//
//import java.util.concurrent.ConcurrentHashMap;
//import java.util.concurrent.ConcurrentLinkedQueue;
//
//import static clients.Client.decryptPriKey;
//
//public class Send extends Thread {
//    private volatile boolean running = true;
//
//    private byte[] sessionKey;
//    private final byte[] symKey;
//    private final ClientTalk clientTalk;
//    private final TalkClient talkClient;
//
//    public Send(ClientTalk clientTalk, Settings settings) {
//        this.clientTalk = clientTalk;
//        this.talkClient = clientTalk.getClientNetty();
//        this.symKey = clientTalk.getSymKey();
//    }
//
//    public void run() {
//        if (!checkSessionKey()) return;
//
//        while (running) {
//            ConcurrentLinkedQueue<TalkUnit> sendingQueue = clientTalk.getSendingQueue();
//            synchronized (sendingQueue) {
//                try {
//                    sendingQueue.wait();
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                    continue;
//                }
//            }
//
//            if (sendingQueue.size() > 0) {
//                while (sendingQueue.size() > 0) {
//                    TalkUnit talkUnit = sendingQueue.poll();
//                    talkClient.sendMessage(talkUnit.toJson());
//                }
//            }
//        }
//    }
//
//    private boolean checkSessionKey() {
//        System.out.println("Sign in...");
//
//        ConcurrentHashMap<String,byte[]> sessionKeyMap = clientTalk.getSessionKeyMap();
//        sessionKey = sessionKeyMap.get(clientTalk.getDealer());
//        if (sessionKey == null) {
//            TalkUnit talkUnitRequest = ClientTalk.makeSignInRequest(
//                clientTalk.getMyFid(),
//                clientTalk.getSid(),
//                clientTalk.getDealer()
//            );
//
//            byte[] priKey = decryptPriKey(clientTalk.getMyPriKeyCipher(), symKey);
//            if (priKey == null) {
//                System.out.println("Failed to decrypt priKey.");
//                return false;
//            }
//
//            String message = JsonTools.toJson(talkUnitRequest);
//            talkClient.sendMessage(message);
//
//            System.out.println("Waiting sessionKey...");
//            synchronized (sessionKeyMap) {
//                try {
//                    sessionKeyMap.wait();
//                    sessionKey = sessionKeyMap.get(clientTalk.getDealer());
//                    return sessionKey != null;
//                } catch (InterruptedException e) {
//                    return false;
//                }
//            }
//        }
//        return true;
//    }
//
//    public void stopThread() {
//        running = false;
//    }
//}
