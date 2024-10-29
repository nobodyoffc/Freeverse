package javaTools;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class UdpTools {

    public static boolean send(String host, int port,byte[] bytes) throws UnknownHostException {
        InetAddress ip= InetAddress.getByName(host);
        DatagramPacket datagramPacket = new DatagramPacket(bytes,bytes.length,ip,port);
        try(DatagramSocket datagramSocket = new DatagramSocket()){
            datagramSocket.send(datagramPacket);
        } catch (IOException e) {
            System.out.println("Error:"+e.getMessage());
            return false;
        }
        return true;
    }

    public static byte[] receive(int port){
        try(DatagramSocket datagramSocket = new DatagramSocket(port)){
            byte[] buf = new byte[4096];
            DatagramPacket datagramPacket = new DatagramPacket(buf,4096);
            datagramSocket.receive(datagramPacket);
            System.out.println("Got data from "+datagramPacket.getAddress().getHostName());
            int length = datagramPacket.getLength();
            byte[] data = new byte[length];
            System.arraycopy(datagramPacket.getData(),0,data,0,length);
            return data;
        } catch (IOException e) {
            System.out.println("Failed to receive data.");
        }
        return null;
    }
}
