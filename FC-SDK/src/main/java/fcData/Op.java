package fcData;

public enum Op {
    PING((byte) 0),
    SIGN_IN((byte) 1),

    SIGN((byte) 2),
    VERIFY((byte) 3),
    ENCRYPT((byte) 4),
    DECRYPT((byte) 5),


    CREAT_ROOM((byte)6),
    ASK_ROOM_INFO((byte)7),
    SHARE_ROOM_INFO((byte)8),
    ADD_MEMBER((byte)9),
    REMOVE_MEMBER((byte)10),
    CLOSE_ROOM((byte)11),

    ASK_KEY((byte)12),
    SHARE_KEY((byte)13),

    UPDATE_DATA((byte)14),

    ASK_DATA((byte) 15),
    SHARE_DATA((byte) 16),

    ASK_HAT((byte) 17),
    SHARE_HAT((byte) 18),



    SHOW((byte) 19),
    GO((byte) 20),
    PAY((byte)21),

    SEND((byte)22),
    DELETE((byte)23),
    RECOVER((byte)24),

    ADD((byte)25),
    UPDATE((byte)26),

    EXIT((byte)254);


    public String toLowerCase() {
        return this.name().toLowerCase();
    }

    public final byte number;
    Op(byte number) {this.number=number;}
}
