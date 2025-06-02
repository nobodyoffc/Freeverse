package handlers;
    
import clients.ApipClient;
import clients.TalkClient;
import core.crypto.CryptoDataByte;
import core.crypto.EncryptType;
import core.crypto.Encryptor;
import core.crypto.KeyTools;
import data.fcData.AlgorithmId;
import data.fcData.FcSession;
import data.fcData.TalkUnit;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import server.TalkServer;
import utils.BytesUtils;
import utils.TcpUtils;

import java.io.DataOutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static data.fcData.TalkUnit.IdType.FID;

public class TalkUnitSender extends Manager {
    private byte[] myPrikey;

    private AccountManager accountHandler=null;
    private SessionManager sessionHandler;
    private TalkIdManager talkIdHandler;
    private ContactManager contactHandler;
    private ApipClient apipClient;
    private TalkUnitManager talkUnitHandler;


    public TalkUnitSender(TalkServer talkServer) {
        this.myPrikey = talkServer.getDealerPrikey();
        this.sessionHandler = talkServer.getSessionHandler();
        this.apipClient = talkServer.getApipClient();
        this.talkUnitHandler = talkServer.getTalkUnitHandler();

        this.accountHandler = talkServer.getAccountHandler();
    }

    public TalkUnitSender(TalkClient talkClient) {
        this.myPrikey = talkClient.getMyPrikey();
        this.sessionHandler = talkClient.getSessionHandler();
        this.apipClient = talkClient.getApipClient();
        this.talkUnitHandler = talkClient.getTalkUnitHandler();
        
        this.contactHandler = talkClient.getContactHandler();
        this.talkIdHandler = talkClient.getTalkIdHandler();
    }

    public static boolean sendToFidChannels(Map<String, Set<Channel>> fidChannelsMap, byte[] bytes, String fid) {
        Set<Channel> channelSet = fidChannelsMap.get(fid);
        if(channelSet==null|| channelSet.isEmpty()) return true;
        for (Channel channel:channelSet)
            sendBytesByNettyChannel(bytes,channel);
        return false;
    }

    public boolean sendTalkUnitWithNetty(TalkUnit talkUnit, byte[] sessionKey, byte[] myPrikey, String recipientPubkey, Set<Channel> channelSet) {
        if (talkUnit != null) {
            CryptoDataByte cryptoDataByte = talkUnit.encryptUnit(sessionKey, myPrikey, recipientPubkey, talkUnit.getUnitEncryptType());
            if (cryptoDataByte != null) {
                byte[] bytes = cryptoDataByte.toBundle();
                int channelCount = 0;
                for(Channel channel: channelSet){
                    if(sendBytesByNettyChannel(bytes, channel))
                        channelCount++;
                }
                updateSenderBalance(talkUnit.getFrom(), accountHandler, talkUnit.getDataType().name(), bytes.length);
                return channelCount>0;
            }
        }
        return false;
    }

    public static boolean writeTalkUnitByTcp(DataOutputStream outputStream, TalkUnit talkUnitRequest) {
        return writeTalkUnitByTcp(outputStream,talkUnitRequest,null,null,null);
    }

    public static boolean writeTalkUnitByTcp(DataOutputStream outputStream, TalkUnit talkUnitRequest, EncryptType encryptType, byte[] key, byte[] pubkey) {
        byte[] data = talkUnitRequest.toBytes();
        return writeBytesByTcp(outputStream, encryptType, key, pubkey, data);
    }

    public static boolean writeBytesByTcp(DataOutputStream outputStream, EncryptType encryptType, byte[] key, byte[] pubkey, byte[] data) {
        Encryptor encryptor;
        if(encryptType ==null)
            return TcpUtils.writeBytes(outputStream, data);

        CryptoDataByte cryptoDataByte=null;
        switch (encryptType){
            case Symkey-> {
                encryptor = new Encryptor(AlgorithmId.FC_AesCbc256_No1_NrC7);
                cryptoDataByte = encryptor.encryptBySymkey(data, key);
            }
            case Password -> {
                encryptor = new Encryptor(AlgorithmId.FC_AesCbc256_No1_NrC7);
                cryptoDataByte = encryptor.encryptByPassword(data, BytesUtils.bytesToChars(key));
            }
            case AsyTwoWay -> {
                encryptor = new Encryptor(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7);
                cryptoDataByte = encryptor.encryptByAsyTwoWay(data, key, pubkey);
            }
            case AsyOneWay -> {
                encryptor = new Encryptor(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7);
                cryptoDataByte = encryptor.encryptByAsyOneWay(data, key);
            }
        }

        if(cryptoDataByte==null || cryptoDataByte.getCode()!=0)
            return false;

        return TcpUtils.writeBytes(outputStream, cryptoDataByte.toBundle());
    }

    public static boolean sendBytesByNettyCtx(byte[] bytes, ChannelHandlerContext ctx) {
        return sendBytesByNettyChannel(bytes,ctx.channel());
    }

    public static boolean sendBytesByNettyChannel(byte[] messageBytes, Channel channel) {
        if (channel != null && channel.isActive()) {
            ByteBuf buf = channel.alloc().buffer(4 + messageBytes.length)
                .writeInt(messageBytes.length)
                .writeBytes(messageBytes);
            channel.writeAndFlush(buf);
            return true;
        } else {
            System.err.println("Cannot send message - channel is not active");
        }
        return false;
    }

    public void sendTalkUnitListByNetty(List<TalkUnit> sendTalkUnitList, ChannelHandlerContext ctx) {
        try {
            if(ctx!=null && sendTalkUnitList!=null){
                for(TalkUnit talkUnit : sendTalkUnitList){
                    sendTalkUnit(talkUnit, ctx.channel());
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public static void sayGot(ChannelHandlerContext ctx, byte[] myPrikey, TalkUnit parsedTalkUnit) {
        if(parsedTalkUnit.getToType().equals(TalkUnit.IdType.FID)) { //Don't say got to team or group.
            byte[] gotBytes = TalkUnit.notifyGot(parsedTalkUnit, myPrikey, parsedTalkUnit.getFromSession(), parsedTalkUnit.getBySession());
            sendBytesByNettyCtx(gotBytes,ctx);
        }
    }

    public static Long updateSenderBalance(String userFid,
                                           AccountManager accountHandler, String chargeType, int length) {
        Long newBalance = null;
        long cost;
        Long price = accountHandler.getPriceBase();
        Long nPrice = accountHandler.getnPriceMap().get(chargeType);
        if(price!=null && nPrice!=null)
            cost = length * nPrice * price;
        else if(price!=null)
            cost = length * price;
        else cost =0;
        newBalance = accountHandler.updateUserBalance(userFid, -cost);
        if(newBalance!= null && newBalance >= 0) accountHandler.updateViaBalance(userFid, cost, null);
        return newBalance;
    }

    public void sendTalkUnit(TalkUnit talkUnit, Channel channel) {
        Set<Channel> channelSet = new HashSet<>();
        channelSet.add(channel);
        sendTalkUnit(talkUnit,channelSet);
    }

    public void sendTalkUnit(TalkUnit talkUnit, Set<Channel> channelSet) {
        String toId = talkUnit.getTo();
        byte[] sessionKey = null;
        String pubkey = null;
        byte[] prikey = myPrikey;

        FcSession session = sessionHandler.getSessionByUserId(toId);
        if(session!=null){
            sessionKey = session.getKeyBytes();
            pubkey = session.getPubkey();
        }else if(!talkUnit.getToType().equals(FID))
            return;

        if(sessionKey==null && pubkey==null){
            pubkey = KeyTools.getPubkey(toId, sessionHandler, talkIdHandler, contactHandler, apipClient);
            if(pubkey==null) return;
        }
        boolean done = talkUnit.makeTalkUnit(sessionKey, prikey, pubkey);
        if(!done)return;
        if(talkUnit.getUnitEncryptType()!=null) {
            switch (talkUnit.getUnitEncryptType()){
                case Symkey -> {
                    if(sessionKey==null)return;
                    pubkey=null;
                    prikey=null;
                }
                case AsyOneWay -> {
                    if(pubkey==null)return;
                    sessionKey=null;
                }
                case AsyTwoWay -> {
                    if(pubkey==null||prikey==null)return;
                    sessionKey=null;
                }
                default -> {
                    return;
                }
            }
        }
        done = sendTalkUnitWithNetty(talkUnit, sessionKey, prikey, pubkey, channelSet);
        if(done){
            talkUnit.setStata(TalkUnit.State.SENT);
        }
        talkUnitHandler.put(talkUnit);
    }
}
