package server;

import constants.ApipApiNames;

import java.util.ArrayList;
import java.util.List;

public enum ApipApi {
    // APIP0 - OpenAPI
    SIGN_IN("", ApipApiNames.SIGN_IN),
    PING("", ApipApiNames.PING),
    SIGN_IN_ECC("", ApipApiNames.SIGN_IN_ECC),
    TOTALS("", ApipApiNames.TOTALS),
    GET_SERVICE("", ApipApiNames.GET_SERVICE),

    // APIP1 - FCDSL
    GENERAL("", ApipApiNames.GENERAL),
    ENTITY_BY_IDS("", ApipApiNames.ENTITY_BY_IDS),
    ENTITY_SEARCH("", ApipApiNames.ENTITY_SEARCH),

    // APIP2 - Blockchain
    BLOCK_BY_IDS(ApipApiNames.SN_2, ApipApiNames.BLOCK_BY_IDS),
    BLOCK_SEARCH(ApipApiNames.SN_2, ApipApiNames.BLOCK_SEARCH),
    BEST_BLOCK(ApipApiNames.SN_2, ApipApiNames.BEST_BLOCK),
    BLOCK_BY_HEIGHTS(ApipApiNames.SN_2, ApipApiNames.BLOCK_BY_HEIGHTS),
    CASH_BY_IDS(ApipApiNames.SN_2, ApipApiNames.CASH_BY_IDS),
    GET_CASHES(ApipApiNames.SN_2, ApipApiNames.GET_CASHES),
    CASH_SEARCH(ApipApiNames.SN_2, ApipApiNames.CASH_SEARCH),
    TX_BY_IDS(ApipApiNames.SN_2, ApipApiNames.TX_BY_IDS),
    TX_BY_FID(ApipApiNames.SN_2, ApipApiNames.TX_BY_FID),
    TX_SEARCH(ApipApiNames.SN_2, ApipApiNames.TX_SEARCH),
    OP_RETURN_BY_IDS(ApipApiNames.SN_2, ApipApiNames.OP_RETURN_BY_IDS),
    OP_RETURN_SEARCH(ApipApiNames.SN_2, ApipApiNames.OP_RETURN_SEARCH),
    MULTISIG_BY_IDS(ApipApiNames.SN_2, ApipApiNames.MULTISIG_BY_IDS),
    MULTISIG_SEARCH(ApipApiNames.SN_2, ApipApiNames.MULTISIG_SEARCH),
    P2SH_BY_IDS(ApipApiNames.SN_2, ApipApiNames.P2SH_BY_IDS),
    P2SH_SEARCH(ApipApiNames.SN_2, ApipApiNames.P2SH_SEARCH),
    CHAIN_INFO(ApipApiNames.SN_2, ApipApiNames.CHAIN_INFO),
    DIFFICULTY_HISTORY(ApipApiNames.SN_2, ApipApiNames.DIFFICULTY_HISTORY),
    HASH_RATE_HISTORY(ApipApiNames.SN_2, ApipApiNames.HASH_RATE_HISTORY),
    BLOCK_TIME_HISTORY(ApipApiNames.SN_2, ApipApiNames.BLOCK_TIME_HISTORY),
    RICH_LIST(ApipApiNames.SN_2, ApipApiNames.RICHLIST),

    // APIP3 - Identity
    FREER_BY_IDS(ApipApiNames.SN_3, ApipApiNames.Freer_BY_IDS),
    CID_BY_IDS(ApipApiNames.SN_3, ApipApiNames.CID_BY_IDS),
    FREER_SEARCH(ApipApiNames.SN_3, ApipApiNames.FREER_SEARCH),
    FREER_HISTORY(ApipApiNames.SN_3, ApipApiNames.FREER_HISTORY),
    CID_AVATAR_BY_IDS(ApipApiNames.SN_3, ApipApiNames.CID_AVATAR_BY_IDS),
    HOME_HISTORY(ApipApiNames.SN_3, ApipApiNames.HOME_HISTORY),
    NOTICE_FEE_HISTORY(ApipApiNames.SN_3, ApipApiNames.NOTICE_FEE_HISTORY),
    REPUTATION_HISTORY(ApipApiNames.SN_3, ApipApiNames.REPUTATION_HISTORY),
    NOBODY_SEARCH(ApipApiNames.SN_3, ApipApiNames.NOBODY_SEARCH),
    CHECK_NOBODIES(ApipApiNames.SN_3, ApipApiNames.CHECK_NOBODIES),
    NOBODY_BY_IDS(ApipApiNames.SN_3, ApipApiNames.NOBODY_BY_IDS),
    GET_FID_CID(ApipApiNames.SN_3, ApipApiNames.GET_FID_CID),
    FID_CID_SEEK(ApipApiNames.SN_3, ApipApiNames.FID_CID_SEEK),
    NEW_OP_RETURN_HOOK_BY_FIDS(ApipApiNames.SN_3, ApipApiNames.HOOK_NEW_OP_RETURN_BY_FIDS),
    AVATARS(ApipApiNames.SN_3, ApipApiNames.AVATARS),
    GET_AVATAR(ApipApiNames.SN_3, ApipApiNames.GET_AVATAR),

    // APIP4 - Protocol
    PROTOCOL_BY_IDS(ApipApiNames.SN_4, ApipApiNames.PROTOCOL_BY_IDS),
    PROTOCOL_SEARCH(ApipApiNames.SN_4, ApipApiNames.PROTOCOL_SEARCH),
    PROTOCOL_OP_HISTORY(ApipApiNames.SN_4, ApipApiNames.PROTOCOL_OP_HISTORY),
    PROTOCOL_RATE_HISTORY(ApipApiNames.SN_4, ApipApiNames.PROTOCOL_RATE_HISTORY),

    // APIP5 - Code
    CODE_BY_IDS(ApipApiNames.SN_5, ApipApiNames.CODE_BY_IDS),
    CODE_SEARCH(ApipApiNames.SN_5, ApipApiNames.CODE_SEARCH),
    CODE_OP_HISTORY(ApipApiNames.SN_5, ApipApiNames.CODE_OP_HISTORY),
    CODE_RATE_HISTORY(ApipApiNames.SN_5, ApipApiNames.CODE_RATE_HISTORY),

    // APIP6 - Service
    SERVICE_BY_IDS(ApipApiNames.SN_6, ApipApiNames.SERVICE_BY_IDS),
    GET_BEST_BLOCK(ApipApiNames.SN_6, ApipApiNames.GET_BEST_BLOCK),
    GET_SERVICES(ApipApiNames.SN_6, ApipApiNames.GET_SERVICES),
    GET_FREE_SERVICE(ApipApiNames.SN_6, ApipApiNames.GET_FREE_SERVICE),
    SERVICE_SEARCH(ApipApiNames.SN_6, ApipApiNames.SERVICE_SEARCH),
    SERVICE_OP_HISTORY(ApipApiNames.SN_6, ApipApiNames.SERVICE_OP_HISTORY),
    SERVICE_RATE_HISTORY(ApipApiNames.SN_6, ApipApiNames.SERVICE_RATE_HISTORY),

    // APIP7 - App
    APP_BY_IDS(ApipApiNames.SN_7, ApipApiNames.APP_BY_IDS),
    GET_APPS(ApipApiNames.SN_7, ApipApiNames.GET_APPS),
    APP_SEARCH(ApipApiNames.SN_7, ApipApiNames.APP_SEARCH),
    APP_OP_HISTORY(ApipApiNames.SN_7, ApipApiNames.APP_OP_HISTORY),
    APP_RATE_HISTORY(ApipApiNames.SN_7, ApipApiNames.APP_RATE_HISTORY),

    // APIP8 - Square
    SQUARE_BY_IDS(ApipApiNames.SN_8, ApipApiNames.SQUARE_BY_IDS),
    SQUARE_SEARCH(ApipApiNames.SN_8, ApipApiNames.SQUARE_SEARCH),
    SQUARE_MEMBERS(ApipApiNames.SN_8, ApipApiNames.SQUARE_MEMBERS),
    SQUARE_EX_MEMBERS(ApipApiNames.SN_8, ApipApiNames.SQUARE_EX_MEMBERS),
    MY_SQUARES(ApipApiNames.SN_8, ApipApiNames.MY_SQUARES),
    SQUARE_OP_HISTORY(ApipApiNames.SN_8, ApipApiNames.SQUARE_OP_HISTORY),

    // APIP9 - Team
    TEAM_BY_IDS(ApipApiNames.SN_9, ApipApiNames.TEAM_BY_IDS),
    TEAM_SEARCH(ApipApiNames.SN_9, ApipApiNames.TEAM_SEARCH),
    TEAM_MEMBERS(ApipApiNames.SN_9, ApipApiNames.TEAM_MEMBERS),
    TEAM_EX_MEMBERS(ApipApiNames.SN_9, ApipApiNames.TEAM_EX_MEMBERS),
    TEAM_OTHER_PERSONS(ApipApiNames.SN_9, ApipApiNames.TEAM_OTHER_PERSONS),
    MY_TEAMS(ApipApiNames.SN_9, ApipApiNames.MY_TEAMS),
    TEAM_OP_HISTORY(ApipApiNames.SN_9, ApipApiNames.TEAM_OP_HISTORY),
    TEAM_RATE_HISTORY(ApipApiNames.SN_9, ApipApiNames.TEAM_RATE_HISTORY),

    // APIP10 - Box
    BOX_BY_IDS(ApipApiNames.SN_10, ApipApiNames.BOX_BY_IDS),
    BOX_SEARCH(ApipApiNames.SN_10, ApipApiNames.BOX_SEARCH),
    BOX_HISTORY(ApipApiNames.SN_10, ApipApiNames.BOX_HISTORY),

    // APIP11 - Contact
    CONTACT_BY_IDS(ApipApiNames.SN_11, ApipApiNames.CONTACT_BY_IDS),
    CONTACT_SEARCH(ApipApiNames.SN_11, ApipApiNames.CONTACT_SEARCH),
    CONTACTS_DELETED(ApipApiNames.SN_11, ApipApiNames.CONTACTS_DELETED),

    // APIP12 - Secret
    SECRET_BY_IDS(ApipApiNames.SN_12, ApipApiNames.SECRET_BY_IDS),
    SECRET_SEARCH(ApipApiNames.SN_12, ApipApiNames.SECRET_SEARCH),
    SECRETS_DELETED(ApipApiNames.SN_12, ApipApiNames.SECRETS_DELETED),

    // APIP13 - Mail
    MAIL_BY_IDS(ApipApiNames.SN_13, ApipApiNames.MAIL_BY_IDS),
    MAIL_SEARCH(ApipApiNames.SN_13, ApipApiNames.MAIL_SEARCH),
    MAILS_DELETED(ApipApiNames.SN_13, ApipApiNames.MAILS_DELETED),
    MAIL_THREAD(ApipApiNames.SN_13, ApipApiNames.MAIL_THREAD),

    // APIP14 - Proof
    PROOF_BY_IDS(ApipApiNames.SN_14, ApipApiNames.PROOF_BY_IDS),
    PROOF_SEARCH(ApipApiNames.SN_14, ApipApiNames.PROOF_SEARCH),
    PROOF_HISTORY(ApipApiNames.SN_14, ApipApiNames.PROOF_HISTORY),

    // APIP15 - Statement
    STATEMENT_BY_IDS(ApipApiNames.SN_15, ApipApiNames.STATEMENT_BY_IDS),
    STATEMENT_SEARCH(ApipApiNames.SN_15, ApipApiNames.STATEMENT_SEARCH),

    // APIP16 - Token
    MY_TOKENS(ApipApiNames.SN_16, ApipApiNames.MY_TOKENS),
    TOKEN_BY_IDS(ApipApiNames.SN_16, ApipApiNames.TOKEN_BY_IDS),
    TOKEN_HISTORY(ApipApiNames.SN_16, ApipApiNames.TOKEN_HISTORY),
    TOKEN_HOLDERS_BY_IDS(ApipApiNames.SN_16, ApipApiNames.TOKEN_HOLDERS_BY_IDS),
    TOKEN_HOLDER_SEARCH(ApipApiNames.SN_16, ApipApiNames.TOKEN_HOLDER_SEARCH),
    TOKEN_SEARCH(ApipApiNames.SN_16, ApipApiNames.TOKEN_SEARCH),

    // APIP17 - Crypto
    ENCRYPT(ApipApiNames.SN_17, ApipApiNames.ENCRYPT),
    SHA_256(ApipApiNames.SN_17, ApipApiNames.SHA_256),
    HEX_TO_BASE_58(ApipApiNames.SN_17, ApipApiNames.HEX_TO_BASE_58),
    CHECK_SUM_4_HEX(ApipApiNames.SN_17, ApipApiNames.CHECK_SUM_4_HEX),
    SHA_256_X_2(ApipApiNames.SN_17, ApipApiNames.SHA_256_X_2),
    SHA_256_HEX(ApipApiNames.SN_17, ApipApiNames.SHA_256_HEX),
    RIPEMD_160_HEX(ApipApiNames.SN_17, ApipApiNames.RIPEMD_160_HEX),
    KECCAK_SHA_3_HEX(ApipApiNames.SN_17, ApipApiNames.KECCAK_SHA_3_HEX),
    SHA_256_X_2_HEX(ApipApiNames.SN_17, ApipApiNames.SHA_256_X_2_HEX),
    VERIFY(ApipApiNames.SN_17, ApipApiNames.VERIFY),
    ADDRESSES(ApipApiNames.SN_17, ApipApiNames.ADDRESSES),

    // APIP18 - Wallet
    DECODE_TX(ApipApiNames.SN_18, ApipApiNames.DECODE_TX),
    BROADCAST_TX(ApipApiNames.SN_18, ApipApiNames.BROADCAST_TX),
    FEE_RATE(ApipApiNames.SN_18, ApipApiNames.FEE_RATE),
    BALANCE_BY_IDS(ApipApiNames.SN_18, ApipApiNames.BALANCE_BY_IDS),
    CASH_VALID(ApipApiNames.SN_18, ApipApiNames.CASH_VALID),
    GET_UTXO(ApipApiNames.SN_18, ApipApiNames.GET_UTXO),
    UNCONFIRMED(ApipApiNames.SN_18, ApipApiNames.UNCONFIRMED),
    UNCONFIRMED_CASHES(ApipApiNames.SN_18, ApipApiNames.UNCONFIRMED_CASHES),
    OFF_LINE_TX(ApipApiNames.SN_18, ApipApiNames.OFF_LINE_TX),

    // APIP19 - Nid
    NID_SEARCH(ApipApiNames.SN_19, ApipApiNames.NID_SEARCH),
    OID_BY_NIDS(ApipApiNames.SN_19, ApipApiNames.OID_BY_NIDS),

    // APIP20 - Webhook
    HOOK_NEW_CASH_BY_FIDS(ApipApiNames.SN_20, ApipApiNames.HOOK_NEW_CASH_BY_FIDS),
    HOOK_NEW_OP_RETURN_BY_FIDS(ApipApiNames.SN_20, ApipApiNames.HOOK_NEW_OP_RETURN_BY_FIDS),

    //News
    NEWS_BY_IDS(ApipApiNames.SN_21, ApipApiNames.NEWS_BY_IDS),
    NEWS_SEARCH(ApipApiNames.SN_21, ApipApiNames.NEWS_SEARCH),
    // APIP21 - Text
    TEXT_BY_IDS(ApipApiNames.SN_22, ApipApiNames.TEXT_BY_IDS),
    TEXT_SEARCH(ApipApiNames.SN_22, ApipApiNames.TEXT_SEARCH),
    TEXT_OP_HISTORY(ApipApiNames.SN_22, ApipApiNames.TEXT_OP_HISTORY),
    TEXT_RATE_HISTORY(ApipApiNames.SN_22, ApipApiNames.TEXT_RATE_HISTORY),

    // Remark
    REMARK_BY_IDS(ApipApiNames.SN_23, ApipApiNames.REMARK_BY_IDS),
    REMARK_SEARCH(ApipApiNames.SN_23, ApipApiNames.REMARK_SEARCH),
    REMARK_OP_HISTORY(ApipApiNames.SN_23, ApipApiNames.REMARK_OP_HISTORY),
    REMARK_RATE_HISTORY(ApipApiNames.SN_23, ApipApiNames.REMARK_RATE_HISTORY),


    // Sound
    SOUND_BY_IDS(ApipApiNames.SN_24, ApipApiNames.SOUND_BY_IDS),
    SOUND_SEARCH(ApipApiNames.SN_24, ApipApiNames.SOUND_SEARCH),
    SOUND_OP_HISTORY(ApipApiNames.SN_24, ApipApiNames.SOUND_OP_HISTORY),
    SOUND_RATE_HISTORY(ApipApiNames.SN_24, ApipApiNames.SOUND_RATE_HISTORY),

    // Image
    IMAGE_BY_IDS(ApipApiNames.SN_25, ApipApiNames.IMAGE_BY_IDS),
    IMAGE_SEARCH(ApipApiNames.SN_25, ApipApiNames.IMAGE_SEARCH),
    IMAGE_OP_HISTORY(ApipApiNames.SN_25, ApipApiNames.IMAGE_OP_HISTORY),
    IMAGE_RATE_HISTORY(ApipApiNames.SN_25, ApipApiNames.IMAGE_RATE_HISTORY),

    // Video
    VIDEO_BY_IDS(ApipApiNames.SN_26, ApipApiNames.VIDEO_BY_IDS),
    VIDEO_SEARCH(ApipApiNames.SN_26, ApipApiNames.VIDEO_SEARCH),
    VIDEO_OP_HISTORY(ApipApiNames.SN_26, ApipApiNames.VIDEO_OP_HISTORY),
    VIDEO_RATE_HISTORY(ApipApiNames.SN_26, ApipApiNames.VIDEO_RATE_HISTORY),

    // APIP30 - News


    // Endpoints (not APIP APIs)
    CIRCULATING("endpoint", ApipApiNames.CIRCULATING),
    RICHLIST("endpoint", ApipApiNames.RICHLIST),
    FREECASH_INFO("endpoint", ApipApiNames.FREECASH_INFO),
    TOTAL_SUPPLY("endpoint", ApipApiNames.TOTAL_SUPPLY),

    // DISK APIs
    NEW_ORDER("disk", ApipApiNames.NEW_ORDER),

    // Other
    BROADCAST("other", ApipApiNames.BROADCAST),
    GET_TOTALS("other", ApipApiNames.GET_TOTALS),
    GET_PRICES("other", ApipApiNames.GET_PRICES),
    BLOCK_HAS_BY_IDS("other", ApipApiNames.BLOCK_HAS_BY_IDS),
    TX_HAS_BY_IDS("other", ApipApiNames.TX_HAS_BY_IDS);

    private final String sn;
    private final String name;

    ApipApi(String sn, String name) {
        this.sn = sn;
        this.name = name;
    }

    public String getSn() {
        return sn;
    }

    public String getSnNumber() {
        return sn.replace("sn","");
    }

    public String getName() {
        return name;
    }

    public static ApipApi fromName(String name) {
        if (name == null) {
            return null;
        }
        for (ApipApi api : ApipApi.values()) {
            if (api.name.equals(name)) {
                return api;
            }
        }
        return null;
    }

    public static ApipApi fromSn(String sn) {
        if (sn == null) {
            return null;
        }
        for (ApipApi api : ApipApi.values()) {
            if (api.sn.equals(sn)) {
                return api;
            }
        }
        return null;
    }

    public static String nameBySn(String sn) {
        ApipApi apipApi = fromSn(sn);
        return apipApi != null ? apipApi.name : null;
    }

    public static String snByName(String name) {
        ApipApi apipApi = fromName(name);
        return apipApi != null ? apipApi.sn : null;
    }

    public static final String VER_1 = "v1";

    public static final List<ApipApi> apiList = new ArrayList<>(); // All APIs
    public static final List<String> apiNameList = new ArrayList<>(); // All APIs
    public static final ArrayList<ApipApi> initApiList = new ArrayList<>(); //API for new user. They should be free to be requested.

    public static final ApipApi[] openAPIs;
    public static final ApipApi[] fcdslAPIs;
    public static final ApipApi[] blockchainAPIs;
    public static final ApipApi[] identityAPIs;
    public static final ApipApi[] organizeAPIs;
    public static final ApipApi[] constructAPIs;
    public static final ApipApi[] personalAPIs;
    public static final ApipApi[] publishAPIs;
    public static final ApipApi[] financeAPIs;
    public static final ApipApi[] walletAPIs;
    public static final ApipApi[] cryptoAPIs;
    public static final ApipApi[] endpointAPIs;

    static {
        openAPIs = new ApipApi[]{
                PING, GET_SERVICE, SIGN_IN, SIGN_IN_ECC, TOTALS
        };
        fcdslAPIs = new ApipApi[]{GENERAL};

        blockchainAPIs = new ApipApi[]{
                BLOCK_SEARCH, BEST_BLOCK, BLOCK_BY_IDS, BLOCK_BY_HEIGHTS,
                CASH_SEARCH, CASH_BY_IDS,
                OP_RETURN_SEARCH, OP_RETURN_BY_IDS,
                MULTISIG_SEARCH, MULTISIG_BY_IDS,
                TX_SEARCH, TX_BY_IDS, TX_BY_FID,
                CHAIN_INFO, BLOCK_TIME_HISTORY, DIFFICULTY_HISTORY, HASH_RATE_HISTORY
        };

        identityAPIs = new ApipApi[]{
                FREER_SEARCH, FREER_BY_IDS,CID_BY_IDS, FREER_HISTORY,
                FID_CID_SEEK, GET_FID_CID,
                NOBODY_SEARCH, NOBODY_BY_IDS,
                HOME_HISTORY, NOTICE_FEE_HISTORY, REPUTATION_HISTORY,
                GET_AVATAR, AVATARS
        };

        organizeAPIs = new ApipApi[]{
                SQUARE_SEARCH, SQUARE_BY_IDS, SQUARE_MEMBERS, SQUARE_OP_HISTORY, MY_SQUARES,
                TEAM_SEARCH, TEAM_BY_IDS, TEAM_MEMBERS, TEAM_EX_MEMBERS,
                TEAM_OP_HISTORY, TEAM_RATE_HISTORY, TEAM_OTHER_PERSONS, MY_TEAMS
        };

        constructAPIs = new ApipApi[]{
                PROTOCOL_SEARCH, PROTOCOL_BY_IDS, PROTOCOL_OP_HISTORY, PROTOCOL_RATE_HISTORY,
                CODE_SEARCH, CODE_BY_IDS, CODE_OP_HISTORY, CODE_RATE_HISTORY,
                SERVICE_SEARCH, SERVICE_BY_IDS, SERVICE_OP_HISTORY, SERVICE_RATE_HISTORY,
                APP_SEARCH, APP_BY_IDS, APP_OP_HISTORY, APP_RATE_HISTORY
        };

        personalAPIs = new ApipApi[]{
                BOX_SEARCH, BOX_BY_IDS, BOX_HISTORY,
                CONTACT_SEARCH, CONTACT_BY_IDS, CONTACTS_DELETED,
                SECRET_SEARCH, SECRET_BY_IDS, SECRETS_DELETED,
                MAIL_SEARCH, MAIL_BY_IDS, MAILS_DELETED, MAIL_THREAD
        };

        financeAPIs = new ApipApi[]{
                PROOF_SEARCH, PROOF_BY_IDS, PROOF_HISTORY,
                TOKEN_SEARCH, TOKEN_BY_IDS, TOKEN_HISTORY,
                TOKEN_HOLDER_SEARCH, TOKEN_HOLDERS_BY_IDS, MY_TOKENS
        };

        publishAPIs = new ApipApi[]{
                STATEMENT_SEARCH, STATEMENT_BY_IDS, NID_SEARCH,
                TEXT_BY_IDS,TEXT_SEARCH,TEXT_OP_HISTORY,TEXT_RATE_HISTORY,
                REMARK_BY_IDS,REMARK_SEARCH,REMARK_OP_HISTORY,REMARK_RATE_HISTORY,
                SOUND_BY_IDS,SOUND_SEARCH,SOUND_OP_HISTORY,SOUND_RATE_HISTORY,
                IMAGE_BY_IDS,IMAGE_SEARCH,IMAGE_OP_HISTORY,IMAGE_RATE_HISTORY,
                VIDEO_BY_IDS,VIDEO_SEARCH,VIDEO_OP_HISTORY,VIDEO_RATE_HISTORY
        };

        walletAPIs = new ApipApi[]{
                BROADCAST_TX, DECODE_TX,
                CASH_VALID, BALANCE_BY_IDS,
                UNCONFIRMED, UNCONFIRMED_CASHES, FEE_RATE,
                OFF_LINE_TX
        };

        cryptoAPIs = new ApipApi[]{
                ADDRESSES,
                ENCRYPT, VERIFY,
                SHA_256, SHA_256_X_2, SHA_256_HEX, SHA_256_X_2_HEX,
                RIPEMD_160_HEX, KECCAK_SHA_3_HEX,
                CHECK_SUM_4_HEX, HEX_TO_BASE_58
        };
        endpointAPIs = new ApipApi[]{
                HOOK_NEW_CASH_BY_FIDS,
                HOOK_NEW_OP_RETURN_BY_FIDS,
                TOTAL_SUPPLY, CIRCULATING, RICH_LIST, FREECASH_INFO
        };

        apiList.addAll(List.of(openAPIs));
        apiList.addAll(List.of(blockchainAPIs));
        apiList.addAll(List.of(identityAPIs));
        apiList.addAll(List.of(organizeAPIs));
        apiList.addAll(List.of(constructAPIs));
        apiList.addAll(List.of(personalAPIs));
        apiList.addAll(List.of(publishAPIs));
        apiList.addAll(List.of(walletAPIs));
        apiList.addAll(List.of(cryptoAPIs));
        apiList.addAll(List.of(endpointAPIs));
        apiList.addAll(List.of(financeAPIs));

        for(ApipApi apipApi :apiList){
            apiNameList.add(apipApi.name);
        }



        initApiList.add(PING);
        initApiList.add(GET_SERVICE);
        initApiList.add(SIGN_IN);
        initApiList.add(CHAIN_INFO);

        initApiList.add(BROADCAST_TX);
        initApiList.add(BEST_BLOCK);
        initApiList.add(CASH_VALID);
        initApiList.add(TX_BY_FID);
        initApiList.add(TX_BY_IDS);

    }
}
