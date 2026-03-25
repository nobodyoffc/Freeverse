package data.fcData;

public class FcObject extends FcEntity {
    // The 'id' of this class is called 'DID' in FC.
    protected transient byte[] bytes;
    protected String objName;

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
