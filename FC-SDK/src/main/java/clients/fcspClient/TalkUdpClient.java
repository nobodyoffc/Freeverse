package clients.fcspClient;

import apip.apipData.RequestBody;
import apip.apipData.Session;
import appTools.Menu;
import clients.Client;
import clients.apipClient.ApipClient;
import configure.ApiAccount;
import configure.ApiProvider;
import configure.ServiceType;
import crypto.CryptoDataByte;
import crypto.Decryptor;
import crypto.Encryptor;
import fcData.*;
import fcData.TalkUnit;
import javaTools.Hex;
import javaTools.UdpTools;
import javaTools.http.AuthType;
import javaTools.http.RequestMethod;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import settings.TalkClientSettings;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class TalkUdpClient extends Client {
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

    public TalkUdpClient(ApiProvider apiProvider, ApiAccount apiAccount, byte[] symKey, ApipClient apipClient) {
        super(apiProvider, apiAccount, symKey, apipClient);
    }


    public Session ping(String version, AuthType authType, ServiceType serviceType) {
        System.out.println("Sign in...");
        return null;
    }

    public TalkUnit signInRequest() {
        System.out.println("Sign in...");

        TalkUnit talkUnitRequest = new TalkUnit();
        talkUnitRequest.setFrom(apiAccount.getUserId());
        talkUnitRequest.setToType(TalkUnit.ToType.SERVER);
        talkUnitRequest.setDataType(TalkUnit.DataType.ENCRYPTED_REQUEST);

        CryptoDataByte cryptoDataByte;
        RequestBody requestBody = new RequestBody();

        requestBody.setNonce(talkUnitRequest.getNonce());
        requestBody.setTime(talkUnitRequest.getTime());
        requestBody.setSid(this.apiAccount.getProviderId());
        requestBody.setOp(Op.SIGN_IN);

        String request = requestBody.toJson();

        byte[] key  = decryptPriKey(apiAccount.getUserPriKeyCipher(), symKey);
        if(key== null){
            System.out.println("Failed to decrypt priKey.");
            return null;
        }

        Encryptor encryptor = new Encryptor(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7);
        String serverPubKey = apiProvider.getDealerPubKey();

        if(serverPubKey==null)
            apipClient.getPubKey(apiProvider.getApiParams().getDealer(), RequestMethod.POST, AuthType.FC_SIGN_BODY);

        if(serverPubKey==null)return null;
        cryptoDataByte = encryptor.encryptByAsyTwoWay(request.getBytes(),key, Hex.fromHex(serverPubKey));

        String cipher = cryptoDataByte.toJson();

        talkUnitRequest.setData(cipher);

        try {
            UdpTools.send(ip,port, talkUnitRequest.toBundle());
        } catch (IOException e) {
            System.out.println("Failed to sign in.");
            e.printStackTrace();
        }

        if(pendingRequestMap==null)pendingRequestMap=new HashMap<>();
        pendingRequestMap.put(talkUnitRequest.getNonce(), talkUnitRequest);

        return talkUnitRequest;
    }

    public FcReplier signInReply(){
        FcReplier replier = null;
        TalkUnit talkUnitReply;
        while(!socket.isClosed()) {
            byte[] bytes = UdpTools.receive(port);
            if(bytes==null)continue;
            talkUnitReply = TalkUnit.fromBundle(bytes);

            Signature signature = Signature.fromJson((String) talkUnitReply.getData());
            signature.setKey(sessionKey);
            if(!signature.verify()){
                log.debug("Failed to verify signature when checking sign in reply.");
                continue;
            }

            Integer nonce = talkUnitReply.getNonce();
            if(nonce==null)continue;

            if (pendingRequestMap.remove(nonce) == null) continue;

            replier = gson.fromJson(signature.getMsg(), FcReplier.class);
            if(replier==null)continue;



            return replier;
        }
        return replier;
    }


    public void start(TalkClientSettings settings, String userPriKeyCipher) {
        System.out.println("Starting client...");

//        //Get sessionKeys for all recipients.
//        Map<String,byte[]> sessionKeyMap = decryptSessionKeys(settings.getSessionCipherMap(),symKey);
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
            Thread sendThread = new Send(ip,port,this,symKey,sessionKeyMap,settings,userPriKeyCipher);
            Thread getThread = new Get(ip,port,this,symKey,sessionKeyMap,settings,userPriKeyCipher);

            sendThread.start();
            getThread.start();

            // Wait for threads to finish
            sendThread.join();
            getThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    private static Map<String,byte[]> decryptSessionKeys(Map<String, String> sessionCipherMap, byte[] symKey) {
        if(sessionCipherMap==null || sessionCipherMap.isEmpty())return new HashMap<>();

        Map<String,byte[]> sessionKeyMap = new HashMap<>();

        for(String key:sessionCipherMap.keySet()){
            String cipher = sessionCipherMap.get(key);
            Decryptor decryptor = new Decryptor();
            CryptoDataByte cryptoDataByte = decryptor.decryptJsonBySymKey(cipher, symKey);
            if(cryptoDataByte.getCode()!=0)continue;
            sessionKeyMap.put(key,cryptoDataByte.getData());
        }
        return sessionKeyMap;
    }
    public static class Send extends Thread {
        private final TalkUdpClient talkUdpClient;
        private Session session;
        private byte[] sessionKey;
        private String talkId;
        private byte[] symKey;
        private String host;
        private int port;

        private final Map<String, byte[]> sessionKeyMap;

        public Send(String ip,int port,TalkUdpClient talkUdpClient, byte[] symKey, Map<String, byte[]> sessionKeyMap, TalkClientSettings settings, String userPriKeyCipher) {
            this.talkUdpClient = talkUdpClient;
            this.symKey = symKey;
            this.sessionKeyMap=sessionKeyMap;
            this.host = ip;
            this.port = port;
        }

        public void run() {

            talkId=null;
            if (!signInRequest()) return;

            Menu menu = new Menu();
            menu.setName("Talk Client");
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
                case 5 -> signInRequest();
            }

            while (true) {
                try {
                    String content = br.readLine();
                    if (content == null || "exit".equalsIgnoreCase(content)) {
                        break;
                    }
                    UdpTools.send(host,port,content.getBytes());
                } catch (UnknownHostException e) {
                    log.error("Failed to send with UDP on {} : {}. Error:{}",host,port,e.getMessage());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

        }

        private boolean signInRequest() {
            TalkUnit talkUnitRequest = talkUdpClient.signInRequest();
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
        private final byte[] symKey;
        private final Map<String, byte[]> sessionKeyMap;
        private final TalkClientSettings settings;
        private final String userPriKeyCipher;

        public Get(String host, int port,TalkUdpClient talkTcpClient, byte[] symKey, Map<String, byte[]> sessionKeyMap, TalkClientSettings settings, String userPriKeyCipher) {
            this.talkTcpClient = talkTcpClient;
            this.symKey = symKey;
            this.sessionKeyMap = sessionKeyMap;
            this.settings = settings;
            this.fid = settings.getMainFid();
            this.userPriKeyCipher = userPriKeyCipher;
            this.host = host;
            this.port = port;
        }

        public void run() {
            TalkUnit talkUnit;
            byte[] receivedBytes;
            receivedBytes = UdpTools.receive(port);
            talkUnit = TalkUnit.fromBundle(receivedBytes);
            System.out.println(talkUnit.toNiceJson());
            FcReplier replier = talkTcpClient.signInReply();
            System.out.println(replier.toJson());

            while ((receivedBytes = UdpTools.receive(port))!= null) {


                byte[] serverSessionKey;
                serverSessionKey = getSessionKey(replier);
                if (serverSessionKey == null) return;

                receivedBytes = UdpTools.receive(port);
                talkUnit = TalkUnit.fromBundle(receivedBytes);
                System.out.println(talkUnit.toNiceJson());
            }

        }

        @Nullable
        private byte[] getSessionKey(FcReplier replier) {
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

            CryptoDataByte result = new Decryptor().decryptJsonBySymKey(userPriKeyCipher, symKey);
            if(result.getCode()!=0)return null;
            byte[] userPriKey = result.getData();

            result = new Decryptor().decryptJsonByAsyOneWay(sessionKeyCipher,userPriKey);
            if(result.getCode()!=0)return null;
            byte[] sessionKey = result.getData();

            try {
                String serverAccount = talkTcpClient.getApiProvider().getApiParams().getDealer();
                if(serverAccount!=null) {
                    sessionKeyMap.put(serverAccount, sessionKey);
                    settings.getSessionCipherMap().put(serverAccount, Client.encryptBySymKey(sessionKey, symKey));
                    settings.saveSettings(fid);
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
