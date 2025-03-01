package fcData;

import crypto.Hash;
import tools.Hex;

public class FcObject extends FcEntity {
    // The 'id' of this class is called 'DID' in FC.
    protected transient byte[] bytes;

    public String makeDid() {
        this.id = Hex.toHex(Hash.sha256(bytes));
        return id;
    }

    public void setBytes(byte[] bytes) {
        this.bytes = bytes;
    }

    public byte[] getBytes() {
        return bytes;
    }
}
