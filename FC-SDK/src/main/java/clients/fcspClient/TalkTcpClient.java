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
import crypto.KeyTools;
import fcData.*;
import fcData.TalkUnit;
import javaTools.Hex;
import javaTools.JsonTools;
import javaTools.http.AuthType;
import javaTools.http.TcpTools;
import org.jetbrains.annotations.Nullable;
import settings.TalkClientSettings;

import java.io.*;
import java.net.Socket;
import java.net.URL;
import java.security.Key;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class TalkTcpClient extends Client {
    public Socket socket;
    public String ip;
    public Integer port;
    public Map<Integer, TalkUnit> pendingRequestMap;
    public Map<Integer, TalkUnit> pendingReplierMap;
    public static BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

    public TalkTcpClient() {
        pendingRequestMap=new HashMap<>();
        pendingReplierMap=new HashMap<>();
    }

    public TalkTcpClient(ApiProvider apiProvider, ApiAccount apiAccount, byte[] symKey, ApipClient apipClient) {
        super(apiProvider, apiAccount, symKey, apipClient);
        pendingRequestMap=new HashMap<>();
        pendingReplierMap=new HashMap<>();
    }


    public Session ping(String version, AuthType authType, ServiceType serviceType) {
        System.out.println("Sign in...");
        return null;
    }

    public void start(TalkClientSettings settings, String userPriKeyCipher) {
        System.out.println("Starting client...");

        Map<String,byte[]> sessionKeyMap = decryptSessionKeys(settings.getSessionCipherMap(),symKey);

        try {
            URL url = new URL(apiProvider.getApiUrl());
            ip = url.getHost();
            port = url.getPort();
            socket = new Socket(ip, port);
        } catch (IOException e) {
            System.out.println("Failed to create Talk socket.");
            return;
        }

        try  {
            Thread sendThread = new Send(socket,this, settings, sessionKeyMap, symKey);
            Thread getThread = new Get(socket,this, settings, sessionKeyMap, symKey);

            getThread.start();
            sendThread.start();


            // Wait for threads to finish
            sendThread.join();
            getThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public FcReplier signInReply(DataInputStream dis){
        FcReplier replier = null;
        byte[] receivedBytes;

        TalkUnit talkUnitReply;
        while(!socket.isClosed()) {
            try {
                receivedBytes = TcpTools.readBytes(dis);
                if(receivedBytes==null)continue;
            } catch (IOException e) {
                System.out.println("Failed to read from server.");
                return null;
            }

            try{
                talkUnitReply = TalkUnit.fromBundle(receivedBytes);
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

            }catch (Exception ignore){
                continue;
            }

            return replier;
        }
        return replier;
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
        private final Socket socket;
        private final TalkTcpClient talkTcpClient;
        private Session session;
        private byte[] sessionKey;
        private final String mainFid;
        private final String mainFidPriKeyCipher;
        private final String sid;
        private final String dealer;
        private final String dealerPubKey;
        private final ApipClient apipClient;
        private String talkId;
        private byte[] symKey;
        private TalkClientSettings settings;

        private final Map<String, byte[]> sessionKeyMap;

        public Send(Socket socket, TalkTcpClient talkTcpClient, TalkClientSettings settings, Map<String, byte[]> sessionKeyMap, byte[] symKey) {
            this.mainFid=settings.getMainFid();
            this.mainFidPriKeyCipher = talkTcpClient.getApiAccount().getUserPriKeyCipher();
            this.dealer= talkTcpClient.getApiProvider().getApiParams().getDealer();
            this.socket = socket;
            this.talkTcpClient = talkTcpClient;
            this.symKey = symKey;
            this.sessionKeyMap=sessionKeyMap;
            this.sid = talkTcpClient.getApiProvider().getId();
            this.settings = settings;
            this.dealerPubKey = talkTcpClient.getApiProvider().getDealerPubKey();
            this.apipClient = (ApipClient) settings.getApipAccount().getClient();
        }

        public void run() {
            try(DataOutputStream dataOutputStream = new DataOutputStream(socket.getOutputStream())) {
                talkId=null;
                if (!signInRequest(dataOutputStream)) return;

                Menu menu = new Menu();
                menu.setName("Talk Client");
                menu.add("Contacts");
                menu.add("Teams");
                menu.add("Groups");
                menu.add("Rooms");
                menu.add("Sign in");

                int choice = menu.choose(br);
                switch (choice){
                    case 1 -> contacts(br, talkTcpClient.getApipClient());
                    case 2 -> teams(br, talkTcpClient.getApipClient());
                    case 3 -> groups(br, talkTcpClient.getApipClient());
                    case 4 -> rooms(br);
                    case 5 -> signInRequest(dataOutputStream);
                }

                while (!socket.isClosed()) {

                    String content = br.readLine();

                    if (content == null || "exit".equalsIgnoreCase(content)) {
                        break;
                    }
                    dataOutputStream.write(content.getBytes());
                    dataOutputStream.flush();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private boolean signInRequest(DataOutputStream outputStream) {
            System.out.println("Sign in...");

            TalkUnit talkUnitRequest = new TalkUnit(mainFid, TalkUnit.ToType.SERVER,null, null, TalkUnit.DataType.REQUEST);

            CryptoDataByte cryptoDataByte;
            RequestBody requestBody = new RequestBody();

            requestBody.setSid(sid);
            requestBody.setOp(Op.SIGN_IN);

            talkUnitRequest.setData(requestBody);
            talkUnitRequest.setDataType(TalkUnit.DataType.REQUEST);

            byte[] priKey  = decryptPriKey(mainFidPriKeyCipher, symKey);
            if(priKey== null){
                System.out.println("Failed to decrypt priKey.");
                return false;
            }

            Encryptor encryptor = new Encryptor(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7);

            if(dealerPubKey==null){
                log.error("Failed to get the dealer's pubKey.");
                return false;
            }
            byte[] data = talkUnitRequest.toBundle();
            cryptoDataByte = encryptor.encryptByAsyTwoWay(data,priKey, Hex.fromHex(dealerPubKey));
            if(cryptoDataByte.getPubKeyA()==null)cryptoDataByte.setPubKeyA(KeyTools.priKeyToPubKey(priKey));

            byte[] cipher = cryptoDataByte.toBundle();

            try {
                outputStream.writeInt(cipher.length);
                outputStream.write(cipher);
                outputStream.flush();
            } catch (IOException e) {
                System.out.println("Failed to sign in.");
                e.printStackTrace();
            }
            synchronized (talkTcpClient.getPendingRequestMap()) {
                talkTcpClient.getPendingRequestMap().put(talkUnitRequest.getNonce(), talkUnitRequest);
            }

            while (true) {
                try {
                    TimeUnit.SECONDS.sleep(1000*3);
                } catch (InterruptedException e) {
                    System.out.println(e.getMessage());
                }
                if(talkTcpClient.getPendingRequestMap().get(talkUnitRequest.getNonce())==null && sessionKeyMap.get(dealer)!=null){
                    sessionKey = sessionKeyMap.get(dealer);
                    return true;
                }
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
        private final Socket socket;
        private final TalkTcpClient talkTcpClient;
        private final byte[] symKey;
        private final Map<String, byte[]> sessionKeyMap;
        private final TalkClientSettings settings;
        private final String userPriKeyCipher;

        public Get(Socket socket, TalkTcpClient talkTcpClient, TalkClientSettings settings, Map<String, byte[]> sessionKeyMap, byte[] symKey) {
            this.socket = socket;
            this.talkTcpClient = talkTcpClient;
            this.symKey = symKey;
            this.sessionKeyMap = sessionKeyMap;
            this.settings = settings;
            this.fid = settings.getMainFid();
            this.userPriKeyCipher = talkTcpClient.getApiAccount().getUserPriKeyCipher();
        }

        public void run() {
            try(DataInputStream dataInputStream = new DataInputStream(socket.getInputStream())) {
                if (failedToGetServiceInfo(dataInputStream)) return;

                FcReplier replier = talkTcpClient.signInReply(dataInputStream);
                byte[] serverSessionKey;
                serverSessionKey = getSessionKey(replier);
                if (serverSessionKey == null) return;

                byte[] receivedBytes;

                receivedBytes = TcpTools.readBytes(dataInputStream);
                while (receivedBytes != null && !socket.isClosed()) {
                    System.out.println(new String(receivedBytes));
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private boolean failedToGetServiceInfo(DataInputStream dataInputStream) throws IOException {
            TalkUnit talkUnitReceived = TalkUnit.readTalkUnitByTcp(dataInputStream);
            if (talkUnitReceived == null) return true;

            Signature signature = (Signature) talkUnitReceived.getData();
            if(signature==null) return true;

            System.out.println("Received signed information from service.");

            if(signature.verify()) System.out.println("Verified.");
            else {
                System.out.println("Failed to verify the signature from the service.");
                return true;
            }

            if(signature.getFid().equals(talkTcpClient.getApiProvider().getApiParams().getDealer()))
                System.out.println("It is from the dealer of the service.");
            else {
                System.out.println("The signer " + signature.getFid() + " is not the dealer of the service.");
                return true;
            }

            System.out.println("Server: \n" + JsonTools.jsonToNiceJson(signature.getMsg()));
            return false;
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
        new TalkTcpClient().start("127.0.0.1", 3333);
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

    public Socket getSocket() {
        return socket;
    }

    public void setSocket(Socket socket) {
        this.socket = socket;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public Map<Integer, TalkUnit> getPendingRequestMap() {
        return pendingRequestMap;
    }

    public void setPendingRequestMap(Map<Integer, TalkUnit> pendingRequestMap) {
        this.pendingRequestMap = pendingRequestMap;
    }

    public Map<Integer, TalkUnit> getPendingReplierMap() {
        return pendingReplierMap;
    }

    public void setPendingReplierMap(Map<Integer, TalkUnit> pendingReplierMap) {
        this.pendingReplierMap = pendingReplierMap;
    }
}
