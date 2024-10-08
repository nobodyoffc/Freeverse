package constants;

public class ReplyCodeMessage {
    //Header name
    public static final String CodeInHeader = "Code";
    public static final String SignInHeader = "Sign";
    public static final String FidInHeader = "FID";
    public static final String SessionNameInHeader = "SessionName";
    //Code and messages
    public static final int Code0Success = 0;
    public static final String Msg0Success = "Success.";
    public static final int Code1000SignMissed = 1000;
    public static final String Msg1000SignMissed = "Sign missed in request header.";
    public static final int Code1001PubKeyMissed = 1001;
    public static final String Msg1001PubKeyMissed = "PubKey missed in request header.";
    public static final int Code1002SessionNameMissed = 1002;
    public static final String Msg1002SessionNameMissed = "SessionName missed in request header.";
    public static final int Code1003BodyMissed = 1003;
    public static final String Msg1003BodyMissed = "Request body missed.";
    public static final int Code1004InsufficientBalance = 1004;
    public static final String Msg1004InsufficientBalance = "Insufficient balance. Buy the service please.";
    public static final int Code1005UrlUnequal = 1005;
    public static final String Msg1005UrlUnequal = "The request URL is not the same as the one you signed.";
    public static final int Code1006RequestTimeExpired = 1006;
    public static final String Msg1006RequestTimeExpired = "Request expired.";
    public static final int Code1007UsedNonce = 1007;
    public static final String Msg1007UsedNonce = "Nonce had been used.";
    public static final int Code1008BadSign = 1008;
    public static final String Msg1008BadSign = "Failed to verify signature.";
    public static final int Code1009SessionTimeExpired = 1009;
    public static final String Msg1009SessionTimeExpired = "NO such sessionName or it was expired. Please sign in again.";
    public static final int Code1010TooMuchData = 1010;
    public static final String Msg1010TooMuchData = "Too much data to be requested.";
    public static final int Code1011DataNotFound = 1011;
    public static final String Msg1011DataNotFound = "No data meeting the conditions.";
    public static final int Code1012BadQuery = 1012;
    public static final String Msg1012BadQuery = "Bad query. Check your request body referring the documents.";
    public static final int Code1013BadRequest = 1013;
    public static final String Msg1013BadRequest = "Bad request. Please check request body.";
    public static final int Code1014ApiSuspended = 1014;
    public static final String Msg1014ApiSuspended  = "The API is suspended";
    public static final int Code1015FidMissed = 1015;
    public static final String Msg1015FidMissed  = "FID missed in request header.";
    public static final int Code1016IllegalUrl = 1016;
    public static final String Msg1016IllegalUrl  = "Illegal URL.";
    public static final int Code1017MethodNotAvailable = 1017;
    public static final String Msg1017MethodNotAvailable  = "The http method is not available for this API.";
    public static final int Code1018NonceMissed = 1018;
    public static final String Msg1018NonceMissed  = "Nonce missed.";
    public static final int Code1019TimeMissed = 1019;
    public static final String Msg1019TimeMissed  = "Time missed.";
    public static final int Code1020OtherError = 1020;
    public static final String Msg1020OtherError = "Other error.";
    public static final int Code1021FidIsRequired = 1021;
    public static final String Msg1021FidIsRequired = "FID is Required.";
    public static final int Code1022NoSuchMethod = 1022;
    public static final String Msg1022NoSuchMethod = "No such method.";
    public static final int Code1023MissSessionKey = 1023;
    public static final String Msg1023MissSessionKey = "Miss sessionKey";
    public static final int Code1024UrlMissed = 1024;
    public static final String Msg1024UrlMissed = "URL missed in the request body";
    public static final int Code1025WrongSid = 1025;
    public static final String Msg1025WrongSid = "Wrong SID.";
    public static final int Code1026InsufficientFchOnChain = 1026;
    public static final String Msg1026InsufficientFchOnChain = "Insufficient FCH on chain.";
    public static final int Code2001FreeGetIsForbidden = 2001;
    public static final String Msg2001FreeGetIsForbidden = "Free API is not active now.";
    public static final int Code2002CidNoFound = 2002;
    public static final String Msg2002CidNoFound = "Cid no found.";

    public static final int Code2003IllegalFid = 2003;
    public static final String Msg2003IllegalFid = "Illegal FID.";

    public static final int Code2004RawTxNoHex = 2004;
    public static final String Msg2004RawTxNoHex = "Raw TX must be in HEX.";

    public static final int Code2005SendTxFailed = 2005;
    public static final String Msg2005SendTxFailed = "Send TX failed.";

    public static final int Code2006AppNoFound = 2006;
    public static final String Msg2006AppNoFound = "App no found.";

    public static final int Code2007CashNoFound = 2007;
    public static final String Msg2007CashNoFound = "Cash no found.";

    public static final int Code2008ServiceNoFound = 2008;
    public static final String Msg2008ServiceNoFound= "Cash no found.";
    public static final int Code2009NoFreeSessionKey= 2009;
    public static final String Msg2009NoFreeSessionKey= "Can not get free sessionKey.";
    public static final int Code2010ErrorFromFchRpc= 2010;
    public static final String Msg2010ErrorFromFchRpc= "Error from freecash RPC.";
    public static final int Code2020FailedToWriteData= 2020;
    public static final String Msg2020FailedToWriteData= "Failed to write data";
    public static final int Code3001ResponseIsNull = 3001;
    public static final String Msg3001ResponseIsNull = "Http response is null.";
    public static final int Code3002GetRequestFailed = 3002;
    public static final String Msg3002GetRequestFailed = "The request of GET is failed.";
    public static final int Code3003CloseHttpClientFailed = 3003;
    public static final String Msg3003CloseHttpClientFailed = "Failed to close the http client.";
    public static final int Code3004RequestUrlIsAbsent = 3004;
    public static final String Msg3004RequestUrlIsAbsent = "The URL of requesting is absent.";
    public static final int Code3006ResponseStatusWrong = 3006;
    public static final String Msg3006ResponseStatusWrong = "The status of response is wrong.";
    public static final int Code3007ErrorWhenRequestingPost = 3007;
    public static final String Msg3007ErrorWhenRequestingPost = "Do POST request wrong.";
    public static final int Code3008ErrorWhenRequestingGet = 3008;
    public static final String Msg3008ErrorWhenRequestingGet = "Do GET request wrong.";
    public static final int Code3005ResponseDataIsNull = 3005;
    public static final String Msg3005ResponseDataIsNull = "The data object in response body is null.";
    public static final int Code3009DidMissed = 3009;
    public static final String Msg3009DidMissed  = "DID missed.";
    public static String getMsg(int code) {
        return switch (code) {
            case Code0Success -> Msg0Success;
            case Code1000SignMissed -> Msg1000SignMissed;
            case Code1001PubKeyMissed -> Msg1001PubKeyMissed;
            case Code1002SessionNameMissed -> Msg1002SessionNameMissed;
            case Code1003BodyMissed -> Msg1003BodyMissed;
            case Code1004InsufficientBalance -> Msg1004InsufficientBalance;
            case Code1005UrlUnequal -> Msg1005UrlUnequal;
            case Code1006RequestTimeExpired -> Msg1006RequestTimeExpired;
            case Code1007UsedNonce -> Msg1007UsedNonce;
            case Code1008BadSign -> Msg1008BadSign;
            case Code1009SessionTimeExpired -> Msg1009SessionTimeExpired;
            case Code1010TooMuchData -> Msg1010TooMuchData;
            case Code1011DataNotFound -> Msg1011DataNotFound;
            case Code1012BadQuery -> Msg1012BadQuery;
            case Code1013BadRequest -> Msg1013BadRequest;
            case Code1014ApiSuspended -> Msg1014ApiSuspended;
            case Code1015FidMissed -> Msg1015FidMissed;
            case Code1016IllegalUrl -> Msg1016IllegalUrl;
            case Code1017MethodNotAvailable -> Msg1017MethodNotAvailable;
            case Code1018NonceMissed -> Msg1018NonceMissed;
            case Code1019TimeMissed -> Msg1019TimeMissed;
            case Code1020OtherError -> Msg1020OtherError;
            case Code1021FidIsRequired -> Msg1021FidIsRequired;
            case Code1022NoSuchMethod -> Msg1022NoSuchMethod;
            case Code1023MissSessionKey -> Msg1023MissSessionKey;
            case Code1024UrlMissed -> Msg1024UrlMissed;
            case Code1025WrongSid -> Msg1025WrongSid;
            case Code1026InsufficientFchOnChain -> Msg1026InsufficientFchOnChain;
            case Code2001FreeGetIsForbidden -> Msg2001FreeGetIsForbidden;
            case Code2002CidNoFound -> Msg2002CidNoFound;
            case Code2003IllegalFid -> Msg2003IllegalFid;
            case Code2004RawTxNoHex -> Msg2004RawTxNoHex;
            case Code2005SendTxFailed -> Msg2005SendTxFailed;
            case Code2006AppNoFound -> Msg2006AppNoFound;
            case Code2007CashNoFound -> Msg2007CashNoFound;
            case Code2008ServiceNoFound -> Msg2008ServiceNoFound;
            case Code2009NoFreeSessionKey -> Msg2009NoFreeSessionKey;
            case Code2010ErrorFromFchRpc -> Msg2010ErrorFromFchRpc;
            case Code2020FailedToWriteData -> Msg2020FailedToWriteData;
            case Code3001ResponseIsNull -> Msg3001ResponseIsNull;
            case Code3002GetRequestFailed -> Msg3002GetRequestFailed;
            case Code3003CloseHttpClientFailed -> Msg3003CloseHttpClientFailed;
            case Code3004RequestUrlIsAbsent -> Msg3004RequestUrlIsAbsent;
            case Code3005ResponseDataIsNull -> Msg3005ResponseDataIsNull;
            case Code3006ResponseStatusWrong -> Msg3006ResponseStatusWrong;
            case Code3007ErrorWhenRequestingPost -> Msg3007ErrorWhenRequestingPost;
            case Code3008ErrorWhenRequestingGet -> Msg3008ErrorWhenRequestingGet;
            case Code3009DidMissed -> Msg3009DidMissed;
            default -> "Unknown error code.";
        };
    }
}
