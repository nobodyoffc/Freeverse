package data.fcData;

public enum Op {
    PING((byte) 0),
    PONG((byte) 1),

    SIGN((byte) 2),
    VERIFY((byte) 3),
    ENCRYPT((byte) 4),
    DECRYPT((byte) 5),
    NOTIFY((byte)6),

    SIGN_IN((byte) 10),
    ASK_KEY((byte)11),
    SHARE_KEY((byte)12),

    UPDATE_DATA((byte)13),
    ASK_DATA((byte) 14),
    SHARE_DATA((byte) 15),

    ASK_HAT((byte) 16),
    SHARE_HAT((byte) 17),

    SHOW((byte) 18),
    GO((byte) 19),
    PAY((byte)20),

    SEND((byte)21),
    DELETE((byte)22),
    RECOVER((byte)23),

    ADD((byte)24),
    UPDATE((byte)25),


    EXIT((byte)99);


    public String toLowerCase() {
        return this.name().toLowerCase();
    }

    public final byte number;
    Op(byte number) {this.number=number;}
}
