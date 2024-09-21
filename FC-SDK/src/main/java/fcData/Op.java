package fcData;

public enum Op {

    SIGN((byte) 2),
    VERIFY((byte) 3),
    ENCRYPT((byte) 4),
    DECRYPT((byte) 5),


    SIGN_IN((byte) 1),
    PING((byte) 0),
    //        SIGN_IN((byte)5), //data = {"sid":,"nonce":,"time":}
    CREAT_ROOM((byte)6),
    ASK_ROOM_INFO((byte)7),
    SHARE_ROOM_INFO((byte)8),
    ADD_MEMBER((byte)9),
    REMOVE_MEMBER((byte)10),
    CLOSE_ROOM((byte)11),

    ASK_KEY((byte)12),
    SHARE_KEY((byte)13),

    UPDATE_ITEMS((byte)14),

    ASK_DATA((byte) 15),
    SHARE_DATA((byte) 16),

    ASK_HAT((byte) 17),
    SHARE_HAT((byte) 18),

    EXIT((byte)254),


    SHOW((byte) 20),
    GO((byte) 21);

    public final byte number;
    Op(byte number) {this.number=number;}
}
