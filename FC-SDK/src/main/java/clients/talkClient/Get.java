package clients.talkClient;


import apip.apipData.RequestBody;
import appTools.Settings;
import clients.apipClient.ApipClient;
import configure.ServiceType;
import crypto.CryptoDataByte;
import crypto.Decryptor;
import crypto.Encryptor;
import crypto.KeyTools;
import fcData.FcReplier;
import fcData.Signature;
import fcData.TalkUnit;
import fch.ParseTools;
import javaTools.Hex;
import javaTools.JsonTools;
import javaTools.StringTools;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

import static clients.talkClient.TalkTcpClient.*;
import static fcData.AlgorithmId.FC_AesCbc256_No1_NrC7;

public class Get extends Thread {
    private volatile boolean running = true;
    private final Socket socket;
    private final TalkTcpClient talkTcpClient;
    private final byte[] symKey;
    private final Settings settings;
    private byte[] sessionKey;
    private final ApipClient apipClient;


    public Get(TalkTcpClient talkTcpClient, Settings settings) {
        this.talkTcpClient = talkTcpClient;
        this.settings = settings;
        this.socket = talkTcpClient.getSocket();
        this.symKey = talkTcpClient.getSymKey();
        this.apipClient = (ApipClient) settings.getClient(ServiceType.APIP);
        this.sessionKey = sessionKeyMap.get(dealer);
    }

    public void run() {
        try (DataInputStream dis = new DataInputStream(socket.getInputStream())) {
            fcData.TalkUnit talkUnit;

            if (!checkServiceInfo(dis)) return;

            if(!checkSignInReply(dis)) return;

//                updateUnits();

            while (running && !socket.isClosed()) {
                talkUnit = talkTcpClient.readTalkUnit(dis);
                if (talkUnit == null) {
                    try{
                        TimeUnit.SECONDS.sleep(5);
                    }catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    continue;
                }

                displayMessageQueue.add(talkUnit.toJson());
                displayMessageQueue.notify();
//                switch (talkUnit.getDataType()) {
//                    case TEXT -> dealText(talkUnit);
//                    case REQUEST -> dealRequest(talkUnit);
//                    case REPLY -> dealReply(talkUnit);
//                    case HAT -> {
//                    }
//                    case BYTES -> {
//                    }
//                    case SIGNED_TEXT -> {
//                    }
//                    case SIGNED_REQUEST -> {
//                    }
//                    case SIGNED_BYTES -> {
//                    }
//                    case SIGNED_HAT -> {
//                    }
//                    case SIGNED_REPLY -> {
//                    }
//
//                    case ENCRYPTED_TEXT -> {
//                        decryptData(talkUnit);
//                        dealText(talkUnit);
//                    }
//                    case ENCRYPTED_REQUEST -> {
//                    }
//                    case ENCRYPTED_BYTES -> {
//                    }
//                    case ENCRYPTED_HAT -> {
//                    }
//                    case ENCRYPTED_REPLY -> {
//                    }
//                    default -> {
//                    }
//                }

//                byte[] receivedBytes;
//                receivedBytes = TcpTools.readBytes(dis);
//                while (receivedBytes != null && !socket.isClosed()) {
//                    System.out.println(new String(receivedBytes));
//                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean decryptData(TalkUnit talkUnit) {
        if(talkUnit==null)return false;

        switch (talkUnit.getDataType()){
            case BYTES,TEXT,HAT,REPLY,REQUEST,SIGNED_TEXT,SIGNED_BYTES,SIGNED_REQUEST,SIGNED_HAT,SIGNED_REPLY->{
                return false;
            }
            default -> {
                break;
            }
        }

        CryptoDataByte cryptoDataByte = CryptoDataByte.fromBundle((byte[]) talkUnit.getData());
        Decryptor decryptor = new Decryptor();
        decryptor.decrypt(cryptoDataByte);
        if(cryptoDataByte.getCode()!=0)return false;
        talkUnit.setData(cryptoDataByte.getData());
        switch (talkUnit.getDataType()){
            case ENCRYPTED_BYTES -> talkUnit.setDataType(TalkUnit.DataType.BYTES);
            case ENCRYPTED_TEXT -> talkUnit.setDataType(TalkUnit.DataType.TEXT);
            case ENCRYPTED_HAT -> talkUnit.setDataType(TalkUnit.DataType.HAT);
            case ENCRYPTED_REQUEST -> talkUnit.setDataType(TalkUnit.DataType.REQUEST);
            case ENCRYPTED_REPLY -> talkUnit.setDataType(TalkUnit.DataType.REPLY);
            case ENCRYPTED_SIGNED_BYTES ->talkUnit.setDataType(TalkUnit.DataType.SIGNED_BYTES);
            case ENCRYPTED_SIGNED_TEXT -> talkUnit.setDataType(TalkUnit.DataType.SIGNED_TEXT);
            case ENCRYPTED_SIGNED_HAT -> talkUnit.setDataType(TalkUnit.DataType.SIGNED_HAT);
            case ENCRYPTED_SIGNED_REQUEST -> talkUnit.setDataType(TalkUnit.DataType.SIGNED_REQUEST);
            case ENCRYPTED_SIGNED_REPLY -> talkUnit.setDataType(TalkUnit.DataType.SIGNED_REPLY);
            default -> {return false;}
        }
        return true;
    }

    private void dealText(TalkUnit talkUnit) {
        String text = (String) talkUnit.getData();
        StringBuilder sb = new StringBuilder();
        TalkUnit.IdType toType = talkUnit.getIdType();
        switch (toType){
            case FID -> sb.append(StringTools.omitMiddle(talkUnit.getFrom(),13));

            case GROUP,TEAM -> {
                sb.append(StringTools.omitMiddle(talkUnit.getFrom(),13));
                sb.append("@");
                sb.append(StringTools.omitMiddle(talkUnit.getTo(),13));
            }


            default -> {
                sb.append(StringTools.omitMiddle(talkUnit.getFrom(),13));
                sb.append("@");
                sb.append("Broadcast");
            }
        }
        sb.append(":\n");
        sb.append(text);
        System.out.println(sb);
    }

    private void dealReply(TalkUnit talkUnit) {
        FcReplier fcReplier = (FcReplier) talkUnit.getData();
        TalkUnit talkUnitRequest = pendingRequestMap.get(fcReplier.getNonce());
        if(talkUnitRequest==null)return;
        RequestBody requestBody = (RequestBody) talkUnitRequest.getData();
        if(requestBody==null)return;
        System.out.println("Received reply from "+talkUnit.getFrom()+" for the request "+requestBody.getOp());
        //TODO: deal the reply

        pendingRequestMap.remove(fcReplier.getNonce());
    }

    private boolean dealRequest(TalkUnit talkUnit) {
        RequestBody requestBody = (RequestBody) talkUnit.getData();
        if(requestBody ==null)return false;
        switch (requestBody.getOp()){
            case ASK_ROOM_INFO -> {
            }
            case SHARE_ROOM_INFO -> {
            }
            case ASK_KEY -> {
            }
            case SHARE_KEY -> {
            }
            case ASK_DATA -> {
            }
            case SHARE_DATA -> {
            }
            case ASK_HAT -> {
            }
            case SHARE_HAT -> {
            }
            default -> {}
        }

        return true;
    }

    public void stopThread() {
        running = false;
    }
    private boolean checkSignInReply(DataInputStream dis) {
        while (sessionKey == null) {
            TalkUnit talkUnit = talkTcpClient.readTalkUnit(dis);
            if (talkUnit == null) continue;

            if (!talkUnit.getDataType().equals(fcData.TalkUnit.DataType.REPLY)) {
                synchronized (receivedQueue) {
                    receivedQueue.add(talkUnit);
                }
                return false;
            }

            FcReplier fcReplier = (FcReplier) talkUnit.getData();
            if (fcReplier == null) return false;

            if (fcReplier.getCode() != 0) {
                System.out.println("Server:" + fcReplier.getMessage());
                if (fcReplier.getData() != null) {
                    System.out.println("Data from server:" + JsonTools.toNiceJson(fcReplier.getData()));
                }
                if (fcReplier.getCode() == 1004) {
                    apipClient.getApiAccount().buyApi(symKey, apipClient);
                    try {
                        TimeUnit.SECONDS.sleep(10);
                    } catch (InterruptedException ignore) {
                    }
                    synchronized (sessionKeyMap) {
                        sessionKeyMap.notifyAll();
                    }
                    return false;
                }
                return false;
            }

            sessionKey = getSessionKeyFromReplier(talkTcpClient.getApiProvider().getApiParams().getDealer(), fcReplier);
            if (sessionKey == null) return false;
            synchronized (sessionKeyMap) {
                sessionKeyMap.put(dealer, sessionKey);
                sessionKeyMap.notifyAll();
            }
            System.out.println("Signed in. Balance: "+ ParseTools.satoshiToCoin(fcReplier.getBalance()));
        }
        return true;
    }

    private boolean checkServiceInfo(DataInputStream dataInputStream) throws IOException {
        boolean result = false;
        fcData.TalkUnit talkUnitReceived = TalkUnit.readTalkUnitByTcp(dataInputStream);
        if (talkUnitReceived != null) {
            Signature signature = (Signature) talkUnitReceived.getData();
            if (signature != null) {
                System.out.println("Received signed information from service.");
                if (signature.verify()) {
                    System.out.println("Verified.");
                    if (signature.getFid().equals(talkTcpClient.getApiProvider().getApiParams().getDealer())) {
                        System.out.println("It is from the dealer of the service.");
                        System.out.println("Server: \n" + JsonTools.jsonToNiceJson(signature.getMsg()));
                        result = true;
                    } else {
                        System.out.println("The signer " + signature.getFid() + " is not the dealer of the service.");
                    }
                } else {
                    System.out.println("Failed to verify the signature from the service.");
                }
            }
        }

        return result;
    }

    @Nullable
    private byte[] getSessionKeyFromReplier(String fid, FcReplier replier) {
        if (replier == null || fid==null) return null;

        Integer code = replier.getCode();

        if (!code.equals(0)) {
            if (replier.getMessage() != null)
                System.out.println("Failed to sign in. " + code + ":" + replier.getMessage());
            else
                System.out.println("Failed to sign in. " + code + ":" + replier.getMessage());
            return null;
        }
        TalkSession session = TalkSession.fromJson((String)replier.getData());
        sessionKey = Hex.fromHex(session.getKey());
        try {
            synchronized (sessionKeyMap) {
                sessionKeyMap.put(fid, sessionKey);
            }

            Encryptor encryptor = new Encryptor(FC_AesCbc256_No1_NrC7);
            CryptoDataByte cryptoDataByte = encryptor.encryptBySymKey(sessionKey,symKey);
            if(cryptoDataByte.getCode()!=0)return null;
            sessionKeyFileMap.put(KeyTools.addrToHash160(fid),cryptoDataByte.toBundle());

        } catch (Exception e) {
            System.out.println("Failed to get talk server account.");
        }
        return sessionKey;
    }
}
