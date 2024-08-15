package fch;

import fch.fchData.RawOpReturnForCs;
import fch.fchData.RawTxForCs;
import fch.fchData.SendTo;
import constants.Constants;
import fch.fchData.Cash;
import com.google.gson.Gson;
import javaTools.JsonTools;

import java.util.List;

public class CryptoSign {
    public static String makeRawTxForCs(DataForOffLineTx sendRequestForCs, List<Cash> meetList) {
        Gson gson = new Gson();
        StringBuilder RawTx = new StringBuilder("[");
        int i = 0;
        for (Cash cash : meetList) {
            if (i > 0) RawTx.append(",");
            RawTxForCs rawTxForCs = new RawTxForCs();
            rawTxForCs.setAddress(cash.getOwner());
            rawTxForCs.setAmount((double) cash.getValue() / Constants.COIN_TO_SATOSHI);
            rawTxForCs.setTxid(cash.getBirthTxId());
            rawTxForCs.setIndex(cash.getBirthIndex());
            rawTxForCs.setSeq(i);
            rawTxForCs.setDealType(1);
            RawTx.append(gson.toJson(rawTxForCs));
            i++;
        }
        int j = 0;
        if (sendRequestForCs.getSendToList() != null) {
            for (SendTo sendTo : sendRequestForCs.getSendToList()) {
                RawTxForCs rawTxForCs = new RawTxForCs();
                rawTxForCs.setAddress(sendTo.getFid());
                rawTxForCs.setAmount(sendTo.getAmount());
                rawTxForCs.setSeq(j);
                rawTxForCs.setDealType(2);
                RawTx.append(",");
                RawTx.append(gson.toJson(rawTxForCs));
                j++;
            }
        }

        if (sendRequestForCs.getMsg() != null) {
            RawOpReturnForCs rawOpReturnForCs = new RawOpReturnForCs();
            rawOpReturnForCs.setMsg(sendRequestForCs.getMsg());
            rawOpReturnForCs.setMsgType(2);
            rawOpReturnForCs.setSeq(j);
            rawOpReturnForCs.setDealType(3);
            RawTx.append(",");
            RawTx.append(gson.toJson(rawOpReturnForCs));
        }
        RawTx.append("]");
        return RawTx.toString();
    }

    public static String makeRawTxForCs(List<Cash> cashList, List<SendTo> sendToList, String msg) {
        Gson gson = new Gson();
        StringBuilder RawTx = new StringBuilder("[");
        int i = 0;
        for (Cash cash : cashList) {
            if (i > 0) RawTx.append(",");
            RawTxForCs rawTxForCs = new RawTxForCs();
            rawTxForCs.setAddress(cash.getOwner());
            rawTxForCs.setAmount((double) cash.getValue() / Constants.COIN_TO_SATOSHI);
            rawTxForCs.setTxid(cash.getBirthTxId());
            rawTxForCs.setIndex(cash.getBirthIndex());
            rawTxForCs.setSeq(i);
            rawTxForCs.setDealType(1);
            RawTx.append(gson.toJson(rawTxForCs));
            i++;
        }
        int j = 0;
        if (sendToList != null) {
            for (SendTo sendTo : sendToList) {
                RawTxForCs rawTxForCs = new RawTxForCs();
                rawTxForCs.setAddress(sendTo.getFid());
                rawTxForCs.setAmount(sendTo.getAmount());
                rawTxForCs.setSeq(j);
                rawTxForCs.setDealType(2);
                RawTx.append(",");
                RawTx.append(gson.toJson(rawTxForCs));
                j++;
            }
        }

        if (msg != null) {
            RawOpReturnForCs rawOpReturnForCs = new RawOpReturnForCs();
            rawOpReturnForCs.setMsg(msg);
            rawOpReturnForCs.setMsgType(2);
            rawOpReturnForCs.setSeq(j);
            rawOpReturnForCs.setDealType(3);
            RawTx.append(",");
            RawTx.append(gson.toJson(rawOpReturnForCs));
        }
        RawTx.append("]");
        return RawTx.toString();
    }

    public static DataForOffLineTx parseDataForOffLineTxFromOther(String json) {
        Gson gson = new Gson();
        return gson.fromJson(json, DataForOffLineTx.class);
    }
}
