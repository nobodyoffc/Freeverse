//package talkClient;
//
//
//import apip.apipData.RequestBody;
//import config.Settings;
//import clients.ApipClient;
//import feip.feipData.Service.ServiceType;
//import crypto.CryptoDataByte;
//import crypto.Decryptor;
//import crypto.Encryptor;
//import crypto.KeyTools;
//import fcData.FcReplier;
//import fcData.FcSession;
//import fcData.Signature;
//import fcData.TalkUnit;
//import fch.ParseTools;
//import tools.Hex;
//import tools.JsonTools;
//import tools.StringTools;
//import org.jetbrains.annotations.Nullable;
//
//import java.util.concurrent.TimeUnit;
//
//import static fcData.AlgorithmId.FC_AesCbc256_No1_NrC7;
//
//public class Get extends Thread {
//    private volatile boolean running = true;
//    private final ClientTalk clientTalk;
//    private final byte[] symKey;
//    private byte[] sessionKey;
//    private final ApipClient apipClient;
//
//
//    public Get(ClientTalk clientTalk, Settings settings) {
//        this.clientTalk = clientTalk;
//        this.symKey = clientTalk.getSymKey();
//        this.apipClient = (ApipClient) settings.getClient(ServiceType.APIP);
//        this.sessionKey = clientTalk.getSessionKeyMap().get(clientTalk.getDealer());
//    }
//
//    public void run() {
//        if (!checkServiceInfo()) return;
//        if (!checkSignInReply()) return;
//
//        while (running) {
//            try {
//                synchronized (clientTalk.getReceivedQueue()) {
//                    clientTalk.getReceivedQueue().wait();
//                }
//
//                TalkUnit talkUnit = clientTalk.getReceivedQueue().poll();
//                if (talkUnit == null) {
//                    try {
//                        TimeUnit.SECONDS.sleep(5);
//                    } catch (InterruptedException e) {
//                        throw new RuntimeException(e);
//                    }
//                    continue;
//                }
//
//                clientTalk.getDisplayMessageQueue().add(talkUnit.toJson());
//                synchronized (clientTalk.getDisplayMessageQueue()) {
//                    clientTalk.getDisplayMessageQueue().notify();
//                }
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        }
//    }
//
//    private boolean decryptData(TalkUnit talkUnit) {
//        if(talkUnit==null)return false;
//
//        switch (talkUnit.getDataType()){
//            case BYTES,TEXT,HAT,REPLY,REQUEST,SIGNED_TEXT,SIGNED_BYTES,SIGNED_REQUEST,SIGNED_HAT,SIGNED_REPLY->{
//                return false;
//            }
//            default -> {
//                break;
//            }
//        }
//
//        CryptoDataByte cryptoDataByte = CryptoDataByte.fromBundle((byte[]) talkUnit.getData());
//        Decryptor decryptor = new Decryptor();
//        decryptor.decrypt(cryptoDataByte);
//        if(cryptoDataByte.getCode()!=0)return false;
//        talkUnit.setData(cryptoDataByte.getData());
//        switch (talkUnit.getDataType()){
//            case ENCRYPTED_BYTES -> talkUnit.setDataType(TalkUnit.DataType.BYTES);
//            case ENCRYPTED_TEXT -> talkUnit.setDataType(TalkUnit.DataType.TEXT);
//            case ENCRYPTED_HAT -> talkUnit.setDataType(TalkUnit.DataType.HAT);
//            case ENCRYPTED_REQUEST -> talkUnit.setDataType(TalkUnit.DataType.REQUEST);
//            case ENCRYPTED_REPLY -> talkUnit.setDataType(TalkUnit.DataType.REPLY);
//            case ENCRYPTED_SIGNED_BYTES ->talkUnit.setDataType(TalkUnit.DataType.SIGNED_BYTES);
//            case ENCRYPTED_SIGNED_TEXT -> talkUnit.setDataType(TalkUnit.DataType.SIGNED_TEXT);
//            case ENCRYPTED_SIGNED_HAT -> talkUnit.setDataType(TalkUnit.DataType.SIGNED_HAT);
//            case ENCRYPTED_SIGNED_REQUEST -> talkUnit.setDataType(TalkUnit.DataType.SIGNED_REQUEST);
//            case ENCRYPTED_SIGNED_REPLY -> talkUnit.setDataType(TalkUnit.DataType.SIGNED_REPLY);
//            default -> {return false;}
//        }
//        return true;
//    }
//
//    private void dealText(TalkUnit talkUnit) {
//        String text = (String) talkUnit.getData();
//        StringBuilder sb = new StringBuilder();
//        TalkUnit.IdType toType = talkUnit.getIdType();
//        switch (toType){
//            case FID -> sb.append(StringTools.omitMiddle(talkUnit.getFrom(),13));
//
//            case GROUP,TEAM -> {
//                sb.append(StringTools.omitMiddle(talkUnit.getFrom(),13));
//                sb.append("@");
//                sb.append(StringTools.omitMiddle(talkUnit.getTo(),13));
//            }
//
//
//            default -> {
//                sb.append(StringTools.omitMiddle(talkUnit.getFrom(),13));
//                sb.append("@");
//                sb.append("Broadcast");
//            }
//        }
//        sb.append(":\n");
//        sb.append(text);
//        System.out.println(sb);
//    }
//
//    private void dealReply(TalkUnit talkUnit) {
//        FcReplier fcReplier = (FcReplier) talkUnit.getData();
//        TalkUnit talkUnitRequest = clientTalk.getPendingRequestMap().get(fcReplier.getNonce());
//        if(talkUnitRequest==null)return;
//        RequestBody requestBody = (RequestBody) talkUnitRequest.getData();
//        if(requestBody==null)return;
//        System.out.println("Received reply from "+talkUnit.getFrom()+" for the request "+requestBody.getOp());
//        //TODO: deal the reply
//
//        clientTalk.getPendingRequestMap().remove(fcReplier.getNonce());
//    }
//
//    private boolean dealRequest(TalkUnit talkUnit) {
//        RequestBody requestBody = (RequestBody) talkUnit.getData();
//        if(requestBody ==null)return false;
//        switch (requestBody.getOp()){
//            case ASK_ROOM_INFO -> {
//            }
//            case SHARE_ROOM_INFO -> {
//            }
//            case ASK_KEY -> {
//            }
//            case SHARE_KEY -> {
//            }
//            case ASK_DATA -> {
//            }
//            case SHARE_DATA -> {
//            }
//            case ASK_HAT -> {
//            }
//            case SHARE_HAT -> {
//            }
//            default -> {}
//        }
//
//        return true;
//    }
//
//    public void stopThread() {
//        running = false;
//        synchronized (clientTalk.getReceivedQueue()) {
//            clientTalk.getReceivedQueue().notify();
//        }
//    }
//
//    private boolean checkServiceInfo() {
//        while (running) {
//            synchronized (clientTalk.getReceivedQueue()) {
//                try {
//                    clientTalk.getReceivedQueue().wait(5000);
//                } catch (InterruptedException e) {
//                    return false;
//                }
//            }
//
//            TalkUnit talkUnitReceived = clientTalk.getReceivedQueue().poll();
//            if (talkUnitReceived == null) continue;
//
//            Signature signature = (Signature) talkUnitReceived.getData();
//            if (signature != null) {
//                System.out.println("Received signed information from service.");
//                if (signature.verify()) {
//                    System.out.println("Verified.");
//                    if (signature.getFid().equals(clientTalk.getApiProvider().getApiParams().getDealer())) {
//                        System.out.println("It is from the dealer of the service.");
//                        System.out.println("Server: \n" + JsonTools.jsonToNiceJson(signature.getMsg()));
//                        return true;
//                    } else {
//                        System.out.println("The signer " + signature.getFid() + " is not the dealer of the service.");
//                    }
//                } else {
//                    System.out.println("Failed to verify the signature from the service.");
//                }
//            }
//        }
//        return false;
//    }
//
//    private boolean checkSignInReply() {
//        while (sessionKey == null && running) {
//            synchronized (clientTalk.getReceivedQueue()) {
//                try {
//                    clientTalk.getReceivedQueue().wait(5000);
//                } catch (InterruptedException e) {
//                    return false;
//                }
//            }
//
//            TalkUnit talkUnit = clientTalk.getReceivedQueue().poll();
//            if (talkUnit == null) continue;
//
//            if (!talkUnit.getDataType().equals(fcData.TalkUnit.DataType.REPLY)) {
//                synchronized (clientTalk.getReceivedQueue()) {
//                    clientTalk.getReceivedQueue().add(talkUnit);
//                }
//                return false;
//            }
//
//            FcReplier fcReplier = (FcReplier) talkUnit.getData();
//            if (fcReplier == null) return false;
//
//            if (fcReplier.getCode() != 0) {
//                System.out.println("Server:" + fcReplier.getMessage());
//                if (fcReplier.getData() != null) {
//                    System.out.println("Data from server:" + JsonTools.toNiceJson(fcReplier.getData()));
//                }
//                if (fcReplier.getCode() == 1004) {
//                    apipClient.getApiAccount().buyApi(symKey, apipClient, null);
//                    try {
//                        TimeUnit.SECONDS.sleep(10);
//                    } catch (InterruptedException ignore) {
//                    }
//                    synchronized (clientTalk.getSessionKeyMap()) {
//                        clientTalk.getSessionKeyMap().notifyAll();
//                    }
//                    return false;
//                }
//                return false;
//            }
//
//            sessionKey = getSessionKeyFromReplier(clientTalk.getApiProvider().getApiParams().getDealer(), fcReplier);
//            if (sessionKey == null) return false;
//            synchronized (clientTalk.getSessionKeyMap()) {
//                clientTalk.getSessionKeyMap().put(clientTalk.getDealer(), sessionKey);
//                clientTalk.getSessionKeyMap().notifyAll();
//            }
//            System.out.println("Signed in. Balance: "+ ParseTools.satoshiToCoin(fcReplier.getBalance()));
//        }
//        return true;
//    }
//
//    @Nullable
//    private byte[] getSessionKeyFromReplier(String fid, FcReplier replier) {
//        if (replier == null || fid==null) return null;
//
//        Integer code = replier.getCode();
//
//        if (!code.equals(0)) {
//            if (replier.getMessage() != null)
//                System.out.println("Failed to sign in. " + code + ":" + replier.getMessage());
//            else
//                System.out.println("Failed to sign in. " + code + ":" + replier.getMessage());
//            return null;
//        }
//        FcSession session = FcSession.fromJson((String)replier.getData());
//        sessionKey = Hex.fromHex(session.getKey());
//        try {
//            synchronized (clientTalk.getSessionKeyMap()) {
//                clientTalk.getSessionKeyMap().put(fid, sessionKey);
//            }
//
//            Encryptor encryptor = new Encryptor(FC_AesCbc256_No1_NrC7);
//            CryptoDataByte cryptoDataByte = encryptor.encryptBySymKey(sessionKey,symKey);
//            if(cryptoDataByte.getCode()!=0)return null;
//            clientTalk.getSessionKeyDB().put(KeyTools.addrToHash160(fid),cryptoDataByte.toBundle());
//
//        } catch (Exception e) {
//            System.out.println("Failed to get talk server account.");
//        }
//        return sessionKey;
//    }
//}
