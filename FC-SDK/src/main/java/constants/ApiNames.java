package constants;

import java.util.ArrayList;

public class ApiNames {
    public static final String Version1 ="v1";
    public static final String Version2 ="v2";
    public static final String SN_0 = "sn0";
    public static final String SN_1 = "sn1";
    public static final String SN_2 = "sn2";
    public static final String SN_3 = "sn3";
    public static final String SN_4 = "sn4";
    public static final String SN_5 = "sn5";
    public static final String SN_6 = "sn6";
    public static final String SN_7 = "sn7";
    public static final String SN_8 = "sn8";
    public static final String SN_9 = "sn9";
    public static final String SN_10 = "sn10";

    public static final String SN_11 = "sn11";
    public static final String SN_12 = "sn12";
    public static final String SN_13 = "sn13";
    public static final String SN_14 = "sn14";
    public static final String SN_15 = "sn15";
    public static final String SN_16 = "sn16";
    public static final String SN_17 = "sn17";
    public static final String SN_18 = "sn18";
    public static final String SN_19 = "sn19";
    public static final String SN_20 = "sn20";
    public static final String SN_21 = "sn21";
    public static final String Carve = "carve";

    public static String makeUrlTailPath(String sn,String ver){
        return "/"+sn+"/"+ver+"/";
    }

    public static String[] OpenAPIs;
    public static String[] FcdslAPIs;
    public static String[] FreeAPIs;
    public static String[] SwapHallAPIs;
    public static String[] BlockchainAPIs;
    public static String[] IdentityAPIs;
    public static String[] OrganizeAPIs;
    public static String[] ConstructAPIs;
    public static String[] PersonalAPIs;
    public static String[] PublishAPIs;
    public static String[] WalletAPIs;
    public static String[] CryptoAPIs;
    public static String[] DiskAPIs;
    public static String[] ApipAPIs;
    public static String[] EndpointAPIs;
    //APIP path
    public static final String APIP0V1Path = "/apip0/v1/";
    public static final String APIP1V1Path = "/apip1/v1/";
    public static final String APIP2V1Path = "/apip2/v1/";
    public static final String APIP3V1Path = "/apip3/v1/";
    public static final String APIP4V1Path = "/apip4/v1/";
    public static final String APIP5V1Path = "/apip5/v1/";
    public static final String APIP6V1Path = "/apip6/v1/";
    public static final String APIP7V1Path = "/apip7/v1/";
    public static final String APIP8V1Path = "/apip8/v1/";
    public static final String APIP9V1Path = "/apip9/v1/";
    public static final String APIP10V1Path = "/apip10/v1/";
    public static final String APIP11V1Path = "/apip11/v1/";
    public static final String APIP12V1Path = "/apip12/v1/";
    public static final String APIP13V1Path = "/apip13/v1/";
    public static final String APIP14V1Path = "/apip14/v1/";
    public static final String APIP15V1Path = "/apip15/v1/";
    public static final String APIP16V1Path = "/apip16/v1/";
    public static final String APIP17V1Path = "/apip17/v1/";
    public static final String APIP18V1Path = "/apip18/v1/";
    public static final String APIP19V1Path = "/apip19/v1/";
    public static final String APIP20V1Path = "/apip20/v1/";
    public static final String APIP21V1Path = "/apip21/v1/";
    public static final String FreeGetPath = "/freeGet/v1/";
    public static final String SwapHallPath = "/swapHall/v1/";
    public static final String ToolsPath = "/tools/";
    public static final String SignIn = "signIn";
    public static final String Ping = "ping";
    public static final String SignInEcc = "signInEcc";
    public static final String General = "general";
    public static final String Totals = "totals";
    public static final String BlockByIds = "blockByIds";
    public static final String BlockSearch = "blockSearch";
    public static final String BlockByHeights = "blockByHeights";
    public static final String CashByIds = "cashByIds";
    public static final String GetCashes = "getCashes";
    public static final String CashSearch = "cashSearch";
    public static final String TxByIds = "txByIds";
    public static final String TxByFid = "txByFid";

    public static final String TxSearch = "txSearch";
    public static final String OpReturnByIds = "opReturnByIds";
    public static final String OpReturnSearch = "opReturnSearch";
    public static final String Unconfirmed = "unconfirmed";
    public static final String BlockHasByIds = "blockHasByIds";
    public static final String TxHasByIds = "TxHasByIds";
    public static final String CashValid = "cashValid";
    public static final String GetUtxo = "getUtxo";
    public static final String FidByIds = "fidByIds";
    public static final String FidSearch = "fidSearch";
    public static final String FidCidSeek = "fidCidSeek";
    public static final String NewOpReturnByFids ="newOpReturnByFids";

    public static final String GetFidCid = "getFidCid";
    public static final String CidInfoByIds = "cidInfoByIds";
    public static final String CidInfoSearch = "cidInfoSearch";
    public static final String CidHistory = "cidHistory";
    public static final String HomepageHistory = "homepageHistory";
    public static final String NoticeFeeHistory = "noticeFeeHistory";
    public static final String ReputationHistory = "reputationHistory";
    public static final String NobodySearch = "nobodySearch";
    public static final String P2shByIds = "p2shByIds";
    public static final String P2shSearch = "p2shSearch";
    public static final String ProtocolByIds = "protocolByIds";
    public static final String ProtocolSearch = "protocolSearch";
    public static final String ProtocolOpHistory = "protocolOpHistory";
    public static final String ProtocolRateHistory = "protocolRateHistory";
    public static final String CodeByIds = "codeByIds";
    public static final String CodeSearch = "codeSearch";
    public static final String CodeOpHistory = "codeOpHistory";
    public static final String CodeRateHistory = "codeRateHistory";
    public static final String ServiceByIds = "serviceByIds";
    public static final String GetBestBlock = "getBestBlock";
    public static final String GetServices = "getServices";
    public static final String GetService = "getService";
    public static final String GetFreeService = "getFreeService";
    public static final String ServiceSearch = "serviceSearch";
    public static final String ServiceOpHistory = "serviceOpHistory";
    public static final String ServiceRateHistory = "serviceRateHistory";
    public static final String AppByIds = "appByIds";
    public static final String GetApps = "getApps";
    public static final String AppSearch = "appSearch";
    public static final String AppOpHistory = "appOpHistory";
    public static final String AppRateHistory = "appRateHistory";
    public static final String GroupByIds = "groupByIds";
    public static final String GroupSearch = "groupSearch";
    public static final String GroupMembers = "groupMembers";
    public static final String GroupExMembers = "groupExMembers";
    public static final String MyGroups = "myGroups";
    public static final String GroupOpHistory = "groupOpHistory";
    public static final String TeamByIds = "teamByIds";
    public static final String TeamSearch = "teamSearch";
    public static final String TeamMembers = "teamMembers";
    public static final String TeamExMembers = "teamExMembers";
    public static final String TeamOtherPersons = "teamOtherPersons";
    public static final String MyTeams = "myTeams";
    public static final String TeamOpHistory = "teamOpHistory";
    public static final String TeamRateHistory = "teamRateHistory";
    public static final String ContactByIds = "contactByIds";
    public static final String ContactSearch = "contactSearch";
    public static final String ContactsDeleted = "contactsDeleted";
    public static final String SecretByIds = "secretByIds";
    public static final String SecretSearch = "secretSearch";
    public static final String SecretsDeleted = "secretsDeleted";
    public static final String MailByIds = "mailByIds";
    public static final String MailSearch = "mailSearch";
    public static final String MailsDeleted = "mailsDeleted";
    public static final String MailThread = "mailThread";
    public static final String StatementByIds = "statementByIds";
    public static final String StatementSearch = "statementSearch";
    public static final String ProofByIds = "proofByIds";
    public static final String ProofSearch = "proofSearch";
    public static final String ProofHistory = "proofHistory";
    public static final String BoxByIds = "boxByIds";
    public static final String BoxSearch = "boxSearch";
    public static final String BoxHistory = "boxHistory";
    public static final String Avatars = "avatars";
    public static final String GetAvatar = "getAvatar";
    public static final String DecodeTx = "decodeTx";
    public static final String BroadcastTx = "broadcastTx";
    public static final String FeeRate = "feeRate";
    public static final String Broadcast = "broadcast";
//    public static final String CashValid = "cashValidLive";
//    public static final String CashValid = "cashValidForCd";
//    public static final String CashValid = "cashValidForPay";
    public static final String GetTotals = "getTotals";
    public static final String GetPrices = "getPrices";
    public static final String NidSearch = "nidSearch";
    public static final String Encrypt = "encrypt";
    public static final String Sha256 = "sha256";
    public static final String HexToBase58 = "hexToBase58";
    public static final String CheckSum4Hex = "checkSum4Hex";
    public static final String Sha256x2 = "sha256x2";
    public static final String Sha256Hex = "sha256Hex";
    public static final String Ripemd160Hex = "ripemd160Hex";
    public static final String KeccakSha3Hex = "keccakSha3Hex";
    public static final String Sha256x2Hex = "sha256x2Hex";
    public static final String Verify = "verify";
    public static final String OffLineTx = "offLineTx";
    public static final String OffLineTxByCd = "offLineTxByCd";
    public static final String Addresses = "addresses";
    public static final String NewCashByFids = "newCashByFids";
    public static final String OpReturnByFids = "opReturnByFids";
    public static final String NobodyByIds ="nobodyByIds";

    public static final String SwapRegister ="swapRegister";
    public static final String SwapUpdate ="swapUpdate";
    public static final String SwapState ="swapState";
    public static final String SwapLp ="swapLp";
    public static final String SwapPending ="swapPending";
    public static final String SwapFinished ="swapFinished";
    public static final String SwapPrice ="swapPrice";
    public static final String SwapInfo ="swapInfo";

    public static final String MyTokens = "myTokens";
    public static final String TokenByIds = "tokenByIds";
    public static final String TokenHistory = "tokenHistory";
    public static final String TokenHoldersByIds = "tokenHoldersByIds";
    public static final String TokenHolderSearch = "tokenHolderSearch";
    public static final String TokenSearch = "tokenSearch";

    public static final String Circulating = "circulating";
    public static final String Richlist = "richlist";
    public static final String FreecashInfo = "freecashInfo";
    public static final String TotalSupply = "totalSupply";
    public static final String Put = "put";
    public static final String Get = "get";
    public static final String Check = "check";
    public static final String LIST = "list";

    public static final String ChainInfo ="chainInfo";
    public static final String DifficultyHistory ="difficultyHistory";
    public static final String HashRateHistory ="hashRateHistory";
    public static final String BlockTimeHistory ="blockTimeHistory";
    public static final String Endpoint = "endpoint";



    public static ArrayList<String> freeApiList = new ArrayList<>();
    public static ArrayList<String> DiskApiList = new ArrayList<>();
    public static ArrayList<String> ApipApiList = new ArrayList<>();

    static {

        OpenAPIs = new String[]{
                Ping, GetService, SignIn, SignInEcc, Totals
        };
        FcdslAPIs = new String[]{General};

        BlockchainAPIs = new String[]{
                BlockSearch,BlockByIds,  BlockByHeights,
                CashSearch,CashByIds,
                FidSearch,FidByIds,
                OpReturnSearch,OpReturnByIds,
                P2shSearch,P2shByIds,
                TxSearch,TxByIds, TxByFid,
                ChainInfo, BlockTimeHistory, DifficultyHistory, HashRateHistory
        };

        IdentityAPIs = new String[]{
                CidInfoSearch,CidInfoByIds,  CidHistory,
                FidCidSeek, GetFidCid,
                NobodySearch,NobodyByIds,
                HomepageHistory, NoticeFeeHistory, ReputationHistory,
                GetAvatar,Avatars
        };

        OrganizeAPIs = new String[]{
                GroupSearch,GroupByIds,  GroupMembers, GroupOpHistory, MyGroups,
                TeamSearch,TeamByIds,  TeamMembers, TeamExMembers,
                TeamOpHistory, TeamRateHistory, TeamOtherPersons, MyTeams
        };

        ConstructAPIs = new String[]{
                ProtocolSearch,ProtocolByIds,  ProtocolOpHistory, ProtocolRateHistory,
                CodeSearch, CodeByIds, CodeOpHistory, CodeRateHistory,
                ServiceSearch,ServiceByIds,  ServiceOpHistory, ServiceRateHistory,
                AppSearch,AppByIds,  AppOpHistory, AppRateHistory
        };

        PersonalAPIs = new String[]{
                BoxSearch, BoxByIds, BoxHistory,
                ContactSearch,ContactByIds,  ContactsDeleted,
                SecretSearch,SecretByIds,  SecretsDeleted,
                MailSearch,MailByIds,  MailsDeleted, MailThread
        };

        PublishAPIs = new String[]{
                ProofSearch,ProofByIds,  ProofHistory,
                StatementSearch,StatementByIds,  NidSearch,
                TokenSearch, TokenByIds, TokenHistory,
                TokenHolderSearch,TokenHoldersByIds, MyTokens
        };

        WalletAPIs = new String[]{
                BroadcastTx, DecodeTx,
                CashValid,
                Unconfirmed, FeeRate,
                OffLineTx
        };

        CryptoAPIs = new String[]{
                Addresses,
                Encrypt, Verify,
                Sha256, Sha256x2, Sha256Hex, Sha256x2Hex,
                Ripemd160Hex, KeccakSha3Hex,
                CheckSum4Hex,HexToBase58
        };

        ApipApiList.addAll(java.util.List.of(OpenAPIs));
        ApipApiList.addAll(java.util.List.of(BlockchainAPIs));
        ApipApiList.addAll(java.util.List.of(IdentityAPIs));
        ApipApiList.addAll(java.util.List.of(OrganizeAPIs));
        ApipApiList.addAll(java.util.List.of(ConstructAPIs));
        ApipApiList.addAll(java.util.List.of(PersonalAPIs));
        ApipApiList.addAll(java.util.List.of(PublishAPIs));
        ApipApiList.addAll(java.util.List.of(WalletAPIs));
        ApipApiList.addAll(java.util.List.of(CryptoAPIs));

        ApipAPIs = ApipApiList.toArray(new String[0]);
        DiskAPIs = new String[]{
                Put, Get, Check, LIST, Ping, SignIn, SignInEcc
        };
        EndpointAPIs = new String[]{
                TotalSupply,Circulating,Richlist,FreecashInfo
        };

        FreeAPIs = new String[]{
                Ping, ChainInfo, GetService, FidCidSeek, GetFidCid,GetAvatar, CashValid, Broadcast
        };

        freeApiList.add(ApiNames.GetBestBlock);
        freeApiList.add(ApiNames.GetFreeService);
        freeApiList.add(ApiNames.GetAvatar);
        freeApiList.add(ApiNames.GetTotals);
        freeApiList.add(ApiNames.GetPrices);
        freeApiList.add(ApiNames.GetApps);
        freeApiList.add(ApiNames.GetCashes);
        freeApiList.add(ApiNames.GetFidCid);
        freeApiList.add(ApiNames.GetServices);

        DiskApiList.add(SignIn);
        DiskApiList.add(SignInEcc);
        DiskApiList.add(Put);
        DiskApiList.add(Get);
        DiskApiList.add(Check);
        DiskApiList.add(LIST);
        DiskApiList.add(Ping);

        SwapHallAPIs = new String[]{
                SwapRegister, SwapUpdate, SwapState,
                SwapLp, SwapPending, SwapFinished,
                SwapPrice, SwapInfo, SwapInfo
        };

    }

    public static ArrayList<String> apiList = new ArrayList<String>();

    static {

        ApiNames.apiList.add(ApiNames.SignIn);
        ApiNames.apiList.add(ApiNames.SignInEcc);
        ApiNames.apiList.add(ApiNames.General);
        ApiNames.apiList.add(ApiNames.Totals);

        ApiNames.apiList.add(ApiNames.BlockByIds);
        ApiNames.apiList.add(ApiNames.BlockSearch);
        ApiNames.apiList.add(ApiNames.BlockByHeights);
        ApiNames.apiList.add(ApiNames.CashByIds);
        ApiNames.apiList.add(ApiNames.CashSearch);
        ApiNames.apiList.add(ApiNames.TxHasByIds);
        ApiNames.apiList.add(ApiNames.CashValid);
        ApiNames.apiList.add(ApiNames.TxByIds);
        ApiNames.apiList.add(ApiNames.TxSearch);
        ApiNames.apiList.add(ApiNames.BlockHasByIds);
        ApiNames.apiList.add(ApiNames.OpReturnByIds);
        ApiNames.apiList.add(ApiNames.OpReturnSearch);
        ApiNames.apiList.add(ApiNames.FidByIds);
        ApiNames.apiList.add(ApiNames.FidSearch);
        ApiNames.apiList.add(ApiNames.P2shByIds);
        ApiNames.apiList.add(ApiNames.P2shSearch);

        ApiNames.apiList.add(ApiNames.CidInfoByIds);
        ApiNames.apiList.add(ApiNames.FidCidSeek);
        ApiNames.apiList.add(ApiNames.CidInfoSearch);
        ApiNames.apiList.add(ApiNames.CidHistory);
        ApiNames.apiList.add(ApiNames.HomepageHistory);
        ApiNames.apiList.add(ApiNames.NoticeFeeHistory);
        ApiNames.apiList.add(ApiNames.ReputationHistory);
        ApiNames.apiList.add(ApiNames.NobodySearch);
        ApiNames.apiList.add(ApiNames.NobodyByIds);

        ApiNames.apiList.add(ApiNames.ProtocolByIds);
        ApiNames.apiList.add(ApiNames.ProtocolSearch);
        ApiNames.apiList.add(ApiNames.ProtocolOpHistory);
        ApiNames.apiList.add(ApiNames.ProtocolRateHistory);

        ApiNames.apiList.add(ApiNames.CodeByIds);
        ApiNames.apiList.add(ApiNames.CodeSearch);
        ApiNames.apiList.add(ApiNames.CodeOpHistory);
        ApiNames.apiList.add(ApiNames.CodeRateHistory);

        ApiNames.apiList.add(ApiNames.ServiceByIds);
        ApiNames.apiList.add(ApiNames.ServiceSearch);
        ApiNames.apiList.add(ApiNames.ServiceOpHistory);
        ApiNames.apiList.add(ApiNames.ServiceRateHistory);

        ApiNames.apiList.add(ApiNames.AppByIds);
        ApiNames.apiList.add(ApiNames.AppSearch);
        ApiNames.apiList.add(ApiNames.AppOpHistory);
        ApiNames.apiList.add(ApiNames.AppRateHistory);

        ApiNames.apiList.add(ApiNames.GroupByIds);
        ApiNames.apiList.add(ApiNames.GroupSearch);
        ApiNames.apiList.add(ApiNames.GroupOpHistory);
        ApiNames.apiList.add(ApiNames.GroupMembers);
        ApiNames.apiList.add(ApiNames.GroupExMembers);
        ApiNames.apiList.add(ApiNames.MyGroups);

        ApiNames.apiList.add(ApiNames.TeamByIds);
        ApiNames.apiList.add(ApiNames.TeamSearch);
        ApiNames.apiList.add(ApiNames.TeamOpHistory);
        ApiNames.apiList.add(ApiNames.TeamMembers);
        ApiNames.apiList.add(ApiNames.TeamExMembers);
        ApiNames.apiList.add(ApiNames.TeamOtherPersons);
        ApiNames.apiList.add(ApiNames.MyTeams);
        ApiNames.apiList.add(ApiNames.TeamRateHistory);

        ApiNames.apiList.add(ApiNames.BoxByIds);
        ApiNames.apiList.add(ApiNames.BoxSearch);
        ApiNames.apiList.add(ApiNames.BoxHistory);

        ApiNames.apiList.add(ApiNames.ContactByIds);
        ApiNames.apiList.add(ApiNames.ContactSearch);
        ApiNames.apiList.add(ApiNames.ContactsDeleted);

        ApiNames.apiList.add(ApiNames.SecretByIds);
        ApiNames.apiList.add(ApiNames.SecretSearch);
        ApiNames.apiList.add(ApiNames.SecretsDeleted);

        ApiNames.apiList.add(ApiNames.MailByIds);
        ApiNames.apiList.add(ApiNames.MailSearch);
        ApiNames.apiList.add(ApiNames.MailsDeleted);
        ApiNames.apiList.add(ApiNames.MailThread);

        ApiNames.apiList.add(ApiNames.ProofByIds);
        ApiNames.apiList.add(ApiNames.ProofSearch);
        ApiNames.apiList.add(ApiNames.ProofHistory);

        ApiNames.apiList.add(ApiNames.StatementByIds);
        ApiNames.apiList.add(ApiNames.StatementSearch);
        ApiNames.apiList.add(ApiNames.NidSearch);

        ApiNames.apiList.add(ApiNames.Avatars);

        ApiNames.apiList.add(ApiNames.Unconfirmed);
//        ApiNames.apiList.add(ApiNames.CashValid);
//        ApiNames.apiList.add(ApiNames.CashValid);
//        ApiNames.apiList.add(ApiNames.CashValid);
        ApiNames.apiList.add(ApiNames.DecodeTx);
        ApiNames.apiList.add(ApiNames.BroadcastTx);
        ApiNames.apiList.add(ApiNames.FeeRate);

        ApiNames.apiList.add(ApiNames.OffLineTx);
//        ApiNames.apiList.add(ApiNames.OffLineTxByCd);
        ApiNames.apiList.add(ApiNames.Encrypt);
        ApiNames.apiList.add(ApiNames.Verify);
        ApiNames.apiList.add(ApiNames.Addresses);
        ApiNames.apiList.add(ApiNames.Sha256);
        ApiNames.apiList.add(ApiNames.Sha256x2);
        ApiNames.apiList.add(ApiNames.Sha256Hex);
        ApiNames.apiList.add(ApiNames.Sha256x2Hex);

        ApiNames.apiList.add(ApiNames.NewCashByFids);

        ApiNames.apiList.add(ApiNames.SwapRegister);
        ApiNames.apiList.add(ApiNames.SwapUpdate);
        ApiNames.apiList.add(ApiNames.SwapState);
        ApiNames.apiList.add(ApiNames.SwapLp);
        ApiNames.apiList.add(ApiNames.SwapPending);
        ApiNames.apiList.add(ApiNames.SwapFinished);
        ApiNames.apiList.add(ApiNames.SwapPrice);
        ApiNames.apiList.add(ApiNames.SwapInfo);
        ApiNames.apiList.add(ApiNames.SwapInfo);


        ApiNames.apiList.add(TokenByIds);
        ApiNames.apiList.add(TokenSearch);
        ApiNames.apiList.add(TokenHistory);
        ApiNames.apiList.add(TokenHoldersByIds);
        ApiNames.apiList.add(MyTokens);
        ApiNames.apiList.add(TokenHolderSearch);

    }
}
