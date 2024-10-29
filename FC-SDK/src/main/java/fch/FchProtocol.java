package fch;

import com.google.gson.Gson;

import java.nio.charset.Charset;

@SuppressWarnings("unused")
public class FchProtocol {

    private static Charset UTF_8 = Charset.forName("utf-8");
    private static Gson gson = new Gson();

    ;

    /**
     * @return
     */
    public static String createCidProtocol(String cid, CidOpType type) {

        Protocol protocol = new Protocol();
        protocol.setHash("");
        protocol.setName("CID");
        protocol.setType("feip");
        protocol.setSn(3);
        protocol.setVer(4);
        CidData cidData = new CidData();
        protocol.setData(cidData);

        if (type == CidOpType.Create || type == CidOpType.Update) {

            if (cid == null || cid.getBytes(UTF_8).length > 32) {
                throw new IllegalArgumentException("cid is null or too long");
            }
            cidData.setName(cid);
            cidData.setOp("register");
        }
        if (type == CidOpType.Delete) {

            if (cid == null || cid.getBytes(UTF_8).length > 32) {
                throw new IllegalArgumentException("cid is null or too long");
            }
            cidData.setOp("unregister");
        }
        return gson.toJson(protocol);
    }

    public static String createDigitEnvelopeProtocol(String msg) {
        Protocol protocol = new Protocol();
        protocol.setHash("");
        protocol.setName("Safe");
        protocol.setType("feip");
        protocol.setSn(17);
        protocol.setVer(3);
        DigitEnvelope digitEnvelope = new DigitEnvelope();
        protocol.setData(digitEnvelope);
        digitEnvelope.setAlg("ECC256k1-AES256CBC");
        digitEnvelope.setOp("add");
        digitEnvelope.setMsg(msg);
        return gson.toJson(protocol);
    }

    public static String delDigitEnvelopeProtocol(String txid) {
        Protocol protocol = new Protocol();
        protocol.setHash("");
        protocol.setName("Safe");
        protocol.setType("feip");
        protocol.setSn(17);
        protocol.setVer(3);
        DigitEnvelopeDel digitEnvelope = new DigitEnvelopeDel();
        protocol.setData(digitEnvelope);
        digitEnvelope.setOp("del");
        digitEnvelope.setTxid(txid);
        return gson.toJson(protocol);
    }

    public static String createSignMsg(String msg, String address, String signature) {
        SignMsg signMsg = new SignMsg();
        signMsg.setAddress(address);
        signMsg.setMessage(msg);
        signMsg.setSignature(signature);
        return gson.toJson(signMsg);
    }

    public static SignMsg parseSignMsg(String msg) {
        return gson.fromJson(msg, SignMsg.class);
    }

    public static enum CidOpType {

        Create, Update, Delete
    }

    public static enum DigitEnvelopeOpType {

        Create, Update, Delete
    }

    public static class Protocol {

        private String type;

        private int sn;

        private int ver;

        private String name;

        private String hash;

        private Object data;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }


        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getHash() {
            return hash;
        }

        public void setHash(String hash) {
            this.hash = hash;
        }

        public Object getData() {
            return data;
        }

        public void setData(Object data) {
            this.data = data;
        }

        public int getSn() {
            return sn;
        }

        public void setSn(int sn) {
            this.sn = sn;
        }

        public int getVer() {
            return ver;
        }

        public void setVer(int ver) {
            this.ver = ver;
        }
    }
    
    private static class CidData {

        private String name;

        private String op;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getOp() {
            return op;
        }

        public void setOp(String op) {
            this.op = op;
        }
    }

    private static class DigitEnvelope {

        private String op;

        private String alg;

        private String msg;

        public String getOp() {
            return op;
        }

        public void setOp(String op) {
            this.op = op;
        }

        public String getAlg() {
            return alg;
        }

        public void setAlg(String alg) {
            this.alg = alg;
        }

        public String getMsg() {
            return msg;
        }

        public void setMsg(String msg) {
            this.msg = msg;
        }
    }

    private static class DigitEnvelopeDel {

        private String op;

        private String txid;


        public String getOp() {
            return op;
        }

        public void setOp(String op) {
            this.op = op;
        }

        public String getTxid() {
            return txid;
        }

        public void setTxid(String txid) {
            this.txid = txid;
        }
    }

    public static class SignMsg {
        private String message;

        private String address;

        private String signature;


        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public String getSignature() {
            return signature;
        }

        public void setSignature(String signature) {
            this.signature = signature;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
