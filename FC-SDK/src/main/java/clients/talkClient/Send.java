package clients.talkClient;

import appTools.Settings;
import clients.apipClient.ApipClient;
import configure.ServiceType;
import crypto.EncryptType;
import fcData.TalkUnit;
import javaTools.Hex;
import javaTools.JsonTools;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Map;

import static clients.Client.decryptPriKey;
import static clients.talkClient.TalkTcpClient.*;

public class Send extends Thread {
//    private final Queue<TalkUnit> sendingQueue;
    private volatile boolean running = true;
    private final Socket socket;
    private byte[] sessionKey;
    private final byte[] symKey;

    private final ApipClient apipClient;

    private final Map<String, byte[]> sessionKeyMap;

    public Send(TalkTcpClient talkTcpClient, Settings settings) {
        this.socket = talkTcpClient.getSocket();
        this.symKey = talkTcpClient.getSymKey();
        this.sessionKeyMap = talkTcpClient.getSessionKeyMap();
        this.apipClient = (ApipClient) settings.getClient(ServiceType.APIP);
    }

    public void run() {
        try (DataOutputStream dataOutputStream = new DataOutputStream(socket.getOutputStream())) {
            if (!checkSessionKey(dataOutputStream)) return;

            while (running && !socket.isClosed()) {

                synchronized (sendingQueue) {
                    sendingQueue.wait();
                }

                if (sendingQueue.size() > 0) {
                    while (sendingQueue.size()>0) {
                        TalkUnit talkUnit = sendingQueue.poll();
                        boolean done = TalkUnit.writeTalkUnitByTcp(dataOutputStream, talkUnit, EncryptType.SymKey, sessionKey, null);
                        if(!done) System.out.println("Failed to send:"+ JsonTools.toNiceJson(talkUnit.getData()));
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void stopThread() {
        running = false;
    }

    private boolean checkSessionKey(DataOutputStream outputStream) {
        System.out.println("Sign in...");

        sessionKey = sessionKeyMap.get(dealer);
        if (sessionKey == null) {
            TalkUnit talkUnitRequest = TalkTcpClient.makeSignInRequest(myFid, sid,dealer);

            byte[] priKey = decryptPriKey(myPriKeyCipher, symKey);
            if (priKey == null) {
                System.out.println("Failed to decrypt priKey.");
                return false;
            }

            boolean done = TalkUnit.writeTalkUnitByTcp(outputStream, talkUnitRequest, EncryptType.AsyTwoWay, priKey, Hex.fromHex(dealerPubKey));
            if (!done) return false;
            System.out.println("Waiting sessionKey...");
            synchronized (sessionKeyMap) {
                try {
                    sessionKeyMap.wait();
                    sessionKey = sessionKeyMap.get(dealer);
                    return sessionKey != null;
                } catch (InterruptedException e) {
                    return false;
                }
            }
        }
        return true;
    }
}
