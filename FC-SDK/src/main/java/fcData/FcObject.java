package fcData;

import crypto.Hash;
import utils.Hex;

public class FcObject extends FcEntity {
    // The 'id' of this class is called 'DID' in FC.
    protected transient byte[] bytes;
    protected String objName;

//    public String makeId() {
//        if(this.bytes==null)this.bytes = this.toBytes();
//        this.id = Hex.toHex(Hash.sha256(bytes));
//        return id;
//    }

    public void setBytes(byte[] bytes) {
        this.bytes = bytes;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public String getObjName() {
        return objName;
    }

    public void setObjName(String objName) {
        this.objName = objName;
    }
}
