package talkClient;

import clients.FcClient;
import data.fcData.*;
import data.apipData.RequestBody;
import ui.Menu;
import config.Settings;
import clients.ApipClient;
import config.ApiAccount;
import config.ApiProvider;
import core.crypto.CryptoDataByte;
import core.crypto.Decryptor;
import core.crypto.Encryptor;
import data.feipData.Service;
import utils.Hex;
import utils.UdpUtils;
import utils.http.AuthType;
import utils.http.RequestMethod;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("unused")
public class TalkUdpClient extends FcClient {
    private static final Logger log = LoggerFactory.getLogger(TalkUdpClient.class);
    public Socket socket;
    public String ip;
    public Integer port;
    public Map<Integer, TalkUnit> pendingRequestMap;
    public Map<Integer, TalkUnit> pendingReplierMap;
    public static BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
    public transient Map<String,byte[]> sessionKeyMap;
    public TalkUdpClient() {
    }

    public TalkUdpClient(ApiProvider apiProvider, ApiAccount apiAccount, byte[] symkey, ApipClient apipClient) {
        super(apiProvider, apiAccount, symkey, apipClient);
    }


    public FcSession ping(String version, AuthType authType, Service.ServiceType serviceType) {
        System.out.println("Sign in...");
        return null;
    }

    public TalkUnit signInRequest(String dealer) {
        System.out.println("Sign in...");

        TalkUnit talkUnitRequest = new TalkUnit();
        talkUnitRequest.setFrom(apiAccount.getUserId());
        talkUnitRequest.setIdType(TalkUnit.IdType.FID);
        talkUnitRequest.setTo(dealer);
        talkUnitRequest.setDataType(TalkUnit.DataType.ENCRYPTED_REQUEST);

        CryptoDataByte cryptoDataByte;
        RequestBody requestBody = new RequestBody();

        requestBody.setNonce(talkUnitRequest.getNonce());
        requestBody.setTime(talkUnitRequest.getTime());
        requestBody.setSid(this.apiAccount.getProviderId());
        requestBody.setOp(Op.SIGN_IN);

        String request = requestBody.toJson();

        byte[] key  = Decryptor.decryptPrikey(apiAccount.getUserPrikeyCipher(), symkey);
        if(key== null){
            System.out.println("Failed to decrypt prikey.");
            return null;
        }

        Encryptor encryptor = new Encryptor(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7);
        String serverPubkey = apiProvider.getDealerPubkey();

        if(serverPubkey==null)
            apipClient.getPubkey(apiProvider.getApiParams().getDealer(), RequestMethod.POST, AuthType.FC_SIGN_BODY);

        if(serverPubkey==null)return null;
        cryptoDataByte = encryptor.encryptByAsyTwoWay(request.getBytes(),key, Hex.fromHex(serverPubkey));

        String cipher = cryptoDataByte.toJson();

        talkUnitRequest.setData(cipher);

        try {
            UdpUtils.send(ip,port, talkUnitRequest.toBytes());
        } catch (IOException e) {
            System.out.println("Failed to sign in.");
            e.printStackTrace();
        }

        if(pendingRequestMap==null)pendingRequestMap=new HashMap<>();
        pendingRequestMap.put(talkUnitRequest.getNonce(), talkUnitRequest);

        return talkUnitRequest;
    }

    public ReplyBody signInReply(){
        ReplyBody replier = null;
        TalkUnit talkUnitReply;
        while(!socket.isClosed()) {
            byte[] bytes = UdpUtils.receive(port);
            if(bytes==null)continue;
            talkUnitReply = TalkUnit.fromBytes(bytes);

            Signature signature = Signature.fromJson((String) talkUnitReply.getData());
            signature.setKey(sessionKey);
            if(!signature.verify()){
                log.debug("Failed to verify signature when checking sign in reply.");
                continue;
            }

            Integer nonce = talkUnitReply.getNonce();
            if(nonce==null)continue;

            if (pendingRequestMap.remove(nonce) == null) continue;

            replier = gson.fromJson(signature.getMsg(), ReplyBody.class);
            if(replier==null)continue;



            return replier;
        }
        return replier;
    }


    public void start(Settings settings, String userPrikeyCipher) {
        System.out.println("Starting client...");

//        //Get sessionKeys for all recipients.
//        Map<String,byte[]> sessionKeyMap = decryptSessionKeys(settings.getSessionCipherMap(),symkey);
        if(sessionKeyMap==null)sessionKeyMap=new HashMap<>();

        try {
            URL url = new URL(apiProvider.getApiUrl());
            ip = url.getHost();
            port = url.getPort();

        } catch (IOException e) {
            System.out.println("Failed to create Talk socket.");
            return;
        }

        try  {
            Thread sendThread = new Send(ip,port,this,symkey,sessionKeyMap,settings,userPrikeyCipher);
            Thread getThread = new Get(ip,port,this,symkey,sessionKeyMap,settings,userPrikeyCipher);

            sendThread.start();
            getThread.start();

            // Wait for threads to finish
            sendThread.join();
            getThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static class Send extends Thread {
        private final TalkUdpClient talkUdpClient;
        private byte[] sessionKey;
        private String talkId;
        private byte[] symkey;
        private String host;
        private int port;
        private String dealer;

        private final Map<String, byte[]> sessionKeyMap;

        public Send(String ip,int port,TalkUdpClient talkUdpClient, byte[] symkey, Map<String, byte[]> sessionKeyMap, Settings settings, String userPrikeyCipher) {
            this.talkUdpClient = talkUdpClient;
            this.symkey = symkey;
            this.sessionKeyMap=sessionKeyMap;
            this.host = ip;
            this.port = port;
            this.dealer = talkUdpClient.getApiProvider().getApiParams().getDealer();
        }

        public void run() {

            talkId=null;
            if (!signInRequest(dealer)) return;

            Menu menu = new Menu("Talk Client");
            menu.add("Contacts");
            menu.add("Teams");
            menu.add("Groups");
            menu.add("Rooms");
            menu.add("Sign in");

            int choice = menu.choose(br);
            switch (choice){
                case 1 -> contacts(br, talkUdpClient.getApipClient());
                case 2 -> teams(br, talkUdpClient.getApipClient());
                case 3 -> groups(br, talkUdpClient.getApipClient());
                case 4 -> rooms(br);
                case 5 -> signInRequest(dealer);
            }

            while (true) {
                try {
                    String content = br.readLine();
                    if (content == null || "exit".equalsIgnoreCase(content)) {
                        break;
                    }
                    UdpUtils.send(host,port,content.getBytes());
                } catch (UnknownHostException e) {
                    log.error("Failed to send with UDP on {} : {}. Error:{}",host,port,e.getMessage());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

        }

        private boolean signInRequest(String dealer) {
            TalkUnit talkUnitRequest = talkUdpClient.signInRequest(dealer);
            if(talkUnitRequest ==null){
                System.out.println("Failed to sign in.");
                return false;
            }
            while (true) {
                try {
                    TimeUnit.SECONDS.sleep(1000*3);
                } catch (InterruptedException e) {
                    System.out.println(e.getMessage());
                }
                if(talkUdpClient.pendingRequestMap.get(talkUnitRequest.getNonce())==null && sessionKey!=null)
                    return true;
            }
        }

        private void contacts(BufferedReader br, ApipClient apipClient) {
            System.out.println("Choose the contact...");
        }

        private void teams(BufferedReader br, ApipClient apipClient) {
            System.out.println("Choose the team...");
        }

        private void groups(BufferedReader br, ApipClient apipClient) {
            System.out.println("Choose the group...");
        }

        private void rooms(BufferedReader br) {
            System.out.println("Choose the room...");
        }
    }

    public static class Get extends Thread {
        private final String fid;
        private final String host;
        private final int port;
        private final TalkUdpClient talkTcpClient;
        private final byte[] symkey;
        private final Map<String, byte[]> sessionKeyMap;
        private final Settings settings;
        private final String userPrikeyCipher;

        public Get(String host, int port,TalkUdpClient talkTcpClient, byte[] symkey, Map<String, byte[]> sessionKeyMap, Settings settings, String userPrikeyCipher) {
            this.talkTcpClient = talkTcpClient;
            this.symkey = symkey;
            this.sessionKeyMap = sessionKeyMap;
            this.settings = settings;
            this.fid = settings.getMainFid();
            this.userPrikeyCipher = userPrikeyCipher;
            this.host = host;
            this.port = port;
        }

        public void run() {
            TalkUnit talkUnit;
            byte[] receivedBytes;
            receivedBytes = UdpUtils.receive(port);
            talkUnit = TalkUnit.fromBytes(receivedBytes);
            System.out.println(talkUnit.toNiceJson());
            ReplyBody replier = talkTcpClient.signInReply();
            System.out.println(replier.toJson());

            while ((receivedBytes = UdpUtils.receive(port))!= null) {


                byte[] serverSessionKey;
                serverSessionKey = getSessionKey(replier);
                if (serverSessionKey == null) return;

                receivedBytes = UdpUtils.receive(port);
                talkUnit = TalkUnit.fromBytes(receivedBytes);
                System.out.println(talkUnit.toNiceJson());
            }

        }

        @Nullable
        private byte[] getSessionKey(ReplyBody replier) {
            if (replier == null) {
                System.out.println("Failed to sing in.");
                return null;
            }

            Integer code = replier.getCode();

            if (!code.equals(0)) {
                if (replier.getMessage() != null)
                    System.out.println("Failed to sign in. " + code + ":" + replier.getMessage());
                else
                    System.out.println("Failed to sign in. " + code + ":" + replier.getMessage());
                return null;
            }

            String sessionKeyCipher = (String)replier.getData();

            CryptoDataByte result = new Decryptor().decryptJsonBySymkey(userPrikeyCipher, symkey);
            if(result.getCode()!=0)return null;
            byte[] userPrikey = result.getData();

            result = new Decryptor().decryptJsonByAsyOneWay(sessionKeyCipher,userPrikey);
            if(result.getCode()!=0)return null;
            byte[] sessionKey = result.getData();

            try {
                String serverAccount = talkTcpClient.getApiProvider().getApiParams().getDealer();
                if(serverAccount!=null) {
                    sessionKeyMap.put(serverAccount, sessionKey);
//                    settings.getSessionCipherMap().put(serverAccount, Client.encryptBySymkey(sessionKey, symkey));
                    settings.saveServerSettings(fid);
                }
            }catch (Exception e){
                log.error("Failed to get talk server account.");
            }
            return sessionKey;
        }
    }


//TEST

    public static void main(String[] args) {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        new TalkUdpClient().start("127.0.0.1", 3333);
    }
    public void start(String ip, int port) {
        System.out.println("Starting client...");
        try (Socket socket = new Socket(ip, port)) {
            Thread sendThread = new SendThread(socket);
            Thread getThread = new GetThread(socket);

            sendThread.start();
            getThread.start();

            // Wait for threads to finish
            sendThread.join();
            getThread.join();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static class SendThread extends Thread {
        private final Socket socket;

        public SendThread(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try (OutputStreamWriter osw = new OutputStreamWriter(socket.getOutputStream());
                 BufferedReader br = new BufferedReader(new InputStreamReader(System.in))) {

                while (!socket.isClosed()) {
                    String content = br.readLine();
                    if (content == null || "exit".equalsIgnoreCase(content)) {
                        break;
                    }
                    osw.write(content + "\n");
                    osw.flush();
                }
            } catch (IOException e) {
                if (!socket.isClosed()) {
                    e.printStackTrace();
                }
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static class GetThread extends Thread {
        private final Socket socket;

        public GetThread(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                String content;
                while ((content = br.readLine()) != null && !socket.isClosed()) {
                    System.out.println(content);
                }
            } catch (IOException e) {
                if (!socket.isClosed()) {
                    e.printStackTrace();
                }
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
