package clients;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;

public class ClientTcp {
    private Socket socket;
    private String ip;
    private Integer port;

    public ClientTcp(String url1) {
        try {
            URL url = new URL(url1);
            ip = url.getHost();
            port = url.getPort();
            socket = new Socket(ip, port);
        } catch (IOException e) {
            System.out.println("Failed to create TCP client:"+e.getMessage());
        }
    }

    public void close(){
        try {
            socket.close();
        } catch (IOException e) {
            System.out.println("Failed to close socket:"+e.getMessage());
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
}
