package javaTools;

import java.io.DataInputStream;
import java.io.DataOutputStream;
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

    public static boolean writeBytes(DataOutputStream dos, byte[]bytes) {
        try {
            dos.writeInt(bytes.length);
            dos.write(bytes);
            dos.flush();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

}
