package javaTools.http;

import java.io.DataInputStream;
import java.io.IOException;

public class TcpTools {
    public static byte[] readBytes(DataInputStream dis) throws IOException {
        byte[] receivedBytes;
        int length = dis.readInt();
        if(length==-1){
            return null;
        }
        receivedBytes = new byte[length];
        int read = dis.read(receivedBytes);
        if(read==0)return null;
        return receivedBytes;
    }
}
