package apip.apipData;

import crypto.Hash;
import javaTools.BytesTools;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HexFormat;

public class WebhookInfo {  //for webhook users to mark different webhook services.
    private String webhookId;
    private String owner;
    private String method;
    private String account;
    private String type;
    private String sid;
    private String url;
    private String receiveUrl;
    private byte[] sessionKey;
    private Object request;
    private Object response;

    public static final String  MAPPINGS = "{\"mappings\":{\"properties\":{\"hookId\":{\"type\":\"keyword\"},\"owner\":{\"type\":\"wildcard\"},\"endpoint\":{\"type\":\"text\"},\"data\":{\"type\":\"object\"},\"method\":{\"type\":\"wildcard\"},\"op\":{\"type\":\"keyword\"}}}}";


    private static String makeWebhookId(String url, String method) {
        return HexFormat.of().formatHex(Hash.sha256x2(BytesTools.bytesMerger(url.getBytes(), method.getBytes())));
    }

    public static void updateFromUserInput(BufferedReader br, WebhookInfo info) {
        try {
            System.out.println("Current method: " + info.method);
            if (askAndConfirm(br, "Change it? (y/n)")) {
                System.out.print("Enter new method: ");
                info.method = br.readLine();
            }

            System.out.println("Current account: " + info.account);
            if (askAndConfirm(br, "Change it? (y/n)")) {
                System.out.print("Enter new account: ");
                info.account = br.readLine();
            }

            System.out.println("Current type: " + info.type);
            if (askAndConfirm(br, "Change it? (y/n)")) {
                System.out.print("Enter new type: ");
                info.type = br.readLine();
            }

            System.out.println("Current sid: " + info.sid);
            if (askAndConfirm(br, "Change it? (y/n)")) {
                System.out.print("Enter new sid: ");
                info.sid = br.readLine();
            }


            System.out.println("Current owner: " + info.owner);
            if (askAndConfirm(br, "Change it? (y/n)")) {
                System.out.print("Enter new owner: ");
                info.owner = br.readLine();
            }

            // Repeat this for all the other fields...

            System.out.println("Current url: " + info.url);
            if (askAndConfirm(br, "Change it? (y/n)")) {
                System.out.print("Enter new url: ");
                info.url = br.readLine();
            }

            System.out.println("Current receiveUrl: " + info.receiveUrl);
            if (askAndConfirm(br, "Change it? (y/n)")) {
                System.out.print("Enter new receiveUrl: ");
                info.receiveUrl = br.readLine();
            }

            info.webhookId = makeWebhookId(info.url, info.method);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static boolean askAndConfirm(BufferedReader reader, String message) throws IOException {
        System.out.print(message);
        String response = reader.readLine();
        return "y".equalsIgnoreCase(response);
    }

    public void inputAll(BufferedReader br) {
        try {
            System.out.print("Enter owner: ");
            this.owner = br.readLine();

            System.out.print("Enter method: ");
            this.method = br.readLine();

            System.out.print("Enter account: ");
            this.account = br.readLine();

            System.out.print("Enter type: ");
            this.type = br.readLine();

            System.out.print("Enter sid: ");
            this.sid = br.readLine();

            System.out.print("Enter url: ");
            this.url = br.readLine();

            System.out.print("Enter receiveUrl: ");
            this.receiveUrl = br.readLine();

            this.webhookId = makeWebhookId(url, method);

        } catch (IOException e) {
            System.out.println("BufferReader wrong.");
        }
    }

    public String getWebhookId() {
        return webhookId;
    }

    public void setWebhookId(String webhookId) {
        this.webhookId = webhookId;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSid() {
        return sid;
    }

    public void setSid(String sid) {
        this.sid = sid;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getReceiveUrl() {
        return receiveUrl;
    }

    public void setReceiveUrl(String receiveUrl) {
        this.receiveUrl = receiveUrl;
    }

    public byte[] getSessionKey() {
        return sessionKey;
    }

    public void setSessionKey(byte[] sessionKey) {
        this.sessionKey = sessionKey;
    }

    public Object getRequest() {
        return request;
    }

    public void setRequest(Object request) {
        this.request = request;
    }

    public Object getResponse() {
        return response;
    }

    public void setResponse(Object response) {
        this.response = response;
    }
}
