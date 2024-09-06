package clients.fcspClient;

import apip.apipData.RequestBody;
import apip.apipData.Session;
import clients.Client;
import clients.apipClient.ApipClient;
import configure.ApiAccount;
import configure.ApiProvider;
import configure.ServiceType;
import fcData.FcReplier;
import feip.feipData.serviceParams.Params;
import javaTools.http.AuthType;
import javaTools.http.HttpRequestMethod;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.URL;

public class TalkClient extends Client {

    public static Socket socket;
    public static OutputStreamWriter outputStreamWriter;
    public static BufferedReader brFromKeyboard;
    public static BufferedReader brFromServer;

    public TalkClient() {
    }

    public TalkClient(ApiProvider apiProvider, ApiAccount apiAccount, byte[] symKey, ApipClient apipClient) {
        super(apiProvider, apiAccount, symKey, apipClient);
    }
    public Session ping(String version, AuthType authType, ServiceType serviceType) {
        System.out.println("Sign in...");
        return null;
    }

    @Override
    public Session signInEcc(ApiAccount apiAccount, RequestBody.SignInMode mode, byte[] symKey) {
        System.out.println("Sign in...");
        return null;
    }

    public void start() {
        System.out.println("Starting client...");
        try{
            URL url = new URL(apiProvider.getApiUrl());
            String ip = url.getHost();
            int port = url.getPort();
//            int ipEndIndex = apiProvider.getApiUrl().lastIndexOf(":");
//            String ip = apiProvider.getApiUrl().substring(0, ipEndIndex);
//            int port = Integer.parseInt(apiProvider.getApiUrl().substring(ipEndIndex+1));
            socket = new Socket(ip, port);
            outputStreamWriter = new OutputStreamWriter(socket.getOutputStream());
            outputStreamWriter.write(apiAccount.getId()+" connect.");
            brFromKeyboard = new BufferedReader(new InputStreamReader(System.in));
            brFromServer = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            System.out.println(brFromServer.readLine());
        } catch (IOException e) {
            try {
                e.printStackTrace();
                socket.close();
                outputStreamWriter.close();
                brFromServer.close();
                brFromKeyboard.close();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }

//        signInEcc();

        try {
            Thread send = new Send(socket);
            Thread get = new Get(socket);

            send.start();
            get.start();

            // Wait for threads to finish
            send.join();
            get.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static class Send extends Thread {
        private final Socket socket;

        public Send(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                while (!socket.isClosed()) {

                        String content = brFromKeyboard.readLine();
                        if (content == null || "exit".equalsIgnoreCase(content)) {
                            break;
                        }
                        outputStreamWriter.write(content + "\n");
                        outputStreamWriter.flush();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }finally {
                try {
                    brFromKeyboard.close();
                    outputStreamWriter.close();
                } catch (IOException ex) {
                    System.out.println("Failed to close:"+ex.getMessage());
                }
            }
        }
    }

    public static class Get extends Thread {
        private final Socket socket;

        public Get(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            String content;
            try {
                while ((content = brFromServer.readLine()) != null && !socket.isClosed()) {
                    System.out.println(content);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }finally {
                try {
                    brFromServer.close();
                } catch (IOException ex) {
                    System.out.println("Failed to close:"+ex.getMessage());
                }
            }
        }
    }


//TEST

    public static void main(String[] args) {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        new TalkClient().start("127.0.0.1", 3333);
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
