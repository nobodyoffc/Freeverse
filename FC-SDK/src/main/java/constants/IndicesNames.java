package constants;

public class IndicesNames {
    //Indices name
    public static final String BLOCK = "block";
    public static final String BLOCK_HAS = "block_has";
    public static final String TX = "tx";
    public static final String TX_HAS = "tx_has";
    public static final String CASH = "cash";
    public static final String OPRETURN = "opreturn";
    public static final String ADDRESS = "address";
    public static final String P2SH = "p2sh";
    public static final String BLOCK_MARK = "block_mark";
    public static final String CID = "cid";
    public static final String CID_HISTORY = "cid_history";
    public static final String REPUTATION_HISTORY = "reputation_history";
    public static final String PROTOCOL = "protocol";
    public static final String CODE = "code";
    public static final String SERVICE = "service";
    public static final String APP = "app";
    public static final String PROTOCOL_HISTORY = "protocol_history";
    public static final String CODE_HISTORY = "code_history";
    public static final String SERVICE_HISTORY = "service_history";
    public static final String APP_HISTORY = "app_history";
    public static final String CONTACT = "contact";
    public static final String MAIL = "mail";
    public static final String SECRET = "secret";
    public static final String BOX = "box";
    public static final String BOX_HISTORY = "box_history";
    public static final String GROUP = "group";
    public static final String TEAM = "team";
    public static final String GROUP_HISTORY = "group_history";
    public static final String TEAM_HISTORY = "team_history";
    public static final String STATEMENT = "statement";
    public static final String PROOF = "proof";
    public static final String PROOF_HISTORY = "proof_history";
    public static final String FEIP_MARK = "feip_mark";
    public static final String NID = "nid";
    public static final String ORDER = "order";
    public static final String WEBHOOK = "webhook";
    public static final String NOBODY = "nobody";

    public static final String SWAP_STATE = "swap_state";
    public static final String SWAP_LP = "swap_lp";
    public static final String SWAP_FINISHED = "swap_finished";
    public static final String SWAP_PENDING = "swap_pending";
    public static final String SWAP_PRICE = "swap_price";
    public static final String TOKEN_HISTORY = "token_history";
    public static final String TOKEN = "token";
    public static final String TOKEN_HOLDER = "token_holder";

    public static void printIndices() {
        Indices indices = Indices.CID;
        System.out.println(indices.name()+indices.ordinal());

        for(Indices in : Indices.values()){
            System.out.println(in.sn()+". "+in.name());
        }
    }

    public enum Indices{

        BLOCK(IndicesNames.BLOCK,1),
        BLOCK_HAS(IndicesNames.BLOCK_HAS,2),
        TX(IndicesNames.TX,3),
        TX_HAS(IndicesNames.TX_HAS,4),
        CASH(IndicesNames.CASH,5),
        OPRETURN(IndicesNames.OPRETURN,6),
        ADDRESS(IndicesNames.ADDRESS,7),
        P2SH(IndicesNames.P2SH,8),
        BLOCK_MARK(IndicesNames.BLOCK_MARK,9),

        CID(IndicesNames.CID,10),
        CID_HISTORY(IndicesNames.CID_HISTORY,11),
        REPUTATION_HISTORY(IndicesNames.REPUTATION_HISTORY,12),

        PROTOCOL(IndicesNames.PROTOCOL,13),
        CODE(IndicesNames.CODE,14),
        SERVICE(IndicesNames.SERVICE,15),
        APP(IndicesNames.APP,16),
        PROTOCOL_HISTORY(IndicesNames.PROTOCOL_HISTORY,17),
        CODE_HISTORY(IndicesNames.CODE_HISTORY,18),
        SERVICE_HISTORY(IndicesNames.SERVICE_HISTORY,19),
        APP_HISTORY(IndicesNames.APP_HISTORY,20),

        CONTACT(IndicesNames.CONTACT,21),
        MAIL(IndicesNames.MAIL,22),
        SECRET(IndicesNames.SECRET,23),
        BOX(IndicesNames.BOX,24),
        BOX_HISTORY(IndicesNames.BOX_HISTORY,25),

        GROUP(IndicesNames.GROUP,26),
        TEAM(IndicesNames.TEAM,27),
        GROUP_HISTORY(IndicesNames.GROUP_HISTORY,28),
        TEAM_HISTORY(IndicesNames.TEAM_HISTORY,29),

        STATEMENT(IndicesNames.STATEMENT,30),
        PROOF(IndicesNames.PROOF,31),
        PROOF_HISTORY(IndicesNames.PROOF_HISTORY,32),

        FEIP_MARK(IndicesNames.FEIP_MARK,33),
        NID(IndicesNames.NID,34);

        private int sn;

        Indices(String name, int sn) {
            this.sn=sn;
        }
        public final int sn(){
            return this.sn;
        }
    }
}
