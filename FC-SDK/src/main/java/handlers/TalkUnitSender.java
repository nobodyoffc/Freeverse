package handlers;
    
import clients.ApipClient;
import clients.TalkClient;
import crypto.CryptoDataByte;
import crypto.EncryptType;
import crypto.Encryptor;
import crypto.KeyTools;
import fcData.AlgorithmId;
import fcData.FcSession;
import fcData.TalkUnit;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import server.TalkServer;
import tools.BytesTools;
import tools.TcpTools;

import java.io.DataOutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static fcData.TalkUnit.IdType.FID;

public class TalkUnitSender extends Handler {
    private byte[] myPriKey;

    private AccountHandler accountHandler=null;
    private SessionHandler sessionHandler;
    private TalkIdHandler talkIdHandler;
    private ContactHandler contactHandler;
    private ApipClient apipClient;
    private TalkUnitHandler talkUnitHandler;


    public TalkUnitSender(TalkServer talkServer) {
        this.myPriKey = talkServer.getDealerPriKey();
        this.sessionHandler = talkServer.getSessionHandler();
        this.apipClient = talkServer.getApipClient();
        this.talkUnitHandler = talkServer.getTalkUnitHandler();

        this.accountHandler = talkServer.getAccountHandler();
    }

    public TalkUnitSender(TalkClient talkClient) {
        this.myPriKey = talkClient.getMyPriKey();
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

    public boolean sendTalkUnitWithNetty(TalkUnit talkUnit, byte[] sessionKey, byte[] myPriKey, String recipientPubKey, Set<Channel> channelSet) {
        if (talkUnit != null) {
            CryptoDataByte cryptoDataByte = talkUnit.encryptUnit(sessionKey, myPriKey, recipientPubKey, talkUnit.getUnitEncryptType());
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

    public static boolean writeTalkUnitByTcp(DataOutputStream outputStream, TalkUnit talkUnitRequest, EncryptType encryptType, byte[] key, byte[] pubKey) {
        byte[] data = talkUnitRequest.toBytes();
        return writeBytesByTcp(outputStream, encryptType, key, pubKey, data);
    }

    public static boolean writeBytesByTcp(DataOutputStream outputStream, EncryptType encryptType, byte[] key, byte[] pubKey, byte[] data) {
        Encryptor encryptor;
        if(encryptType ==null)
            return TcpTools.writeBytes(outputStream, data);

        CryptoDataByte cryptoDataByte=null;
        switch (encryptType){
            case SymKey-> {
                encryptor = new Encryptor(AlgorithmId.FC_AesCbc256_No1_NrC7);
                cryptoDataByte = encryptor.encryptBySymKey(data, key);
            }
            case Password -> {
                encryptor = new Encryptor(AlgorithmId.FC_AesCbc256_No1_NrC7);
                cryptoDataByte = encryptor.encryptByPassword(data, BytesTools.bytesToChars(key));
            }
            case AsyTwoWay -> {
                encryptor = new Encryptor(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7);
                cryptoDataByte = encryptor.encryptByAsyTwoWay(data, key, pubKey);
            }
            case AsyOneWay -> {
                encryptor = new Encryptor(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7);
                cryptoDataByte = encryptor.encryptByAsyOneWay(data, key);
            }
        }

        if(cryptoDataByte==null || cryptoDataByte.getCode()!=0)
            return false;

        return TcpTools.writeBytes(outputStream, cryptoDataByte.toBundle());
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

    public static void sayGot(ChannelHandlerContext ctx, byte[] myPriKey, TalkUnit parsedTalkUnit) {
        if(parsedTalkUnit.getToType().equals(TalkUnit.IdType.FID)) { //Don't say got to team or group.
            byte[] gotBytes = TalkUnit.notifyGot(parsedTalkUnit, myPriKey, parsedTalkUnit.getFromSession(), parsedTalkUnit.getBySession());
            sendBytesByNettyCtx(gotBytes,ctx);
        }
    }

    public static Long updateSenderBalance(String userFid,
                                           AccountHandler accountHandler, String chargeType, int length) {
        Long newBalance = null;
        long cost;
        Long price = accountHandler.getPriceBase();
        Long nPrice = accountHandler.getnPriceMap().get(chargeType);
        if(price!=null && nPrice!=null)
            cost = length * nPrice * price;
        else if(price!=null)
            cost = length * price;
        else cost =0;
        newBalance = accountHandler.addUserBalance(userFid, -cost);
        if(newBalance!= null && newBalance >= 0) accountHandler.addViaBalance(userFid, cost, null);
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
        String pubKey = null;
        byte[] priKey = myPriKey;

        FcSession session = sessionHandler.getSessionById(toId);
        if(session!=null){
            sessionKey = session.getKeyBytes();
            pubKey = session.getPubKey();
        }else if(!talkUnit.getToType().equals(FID))
            return;

        if(sessionKey==null && pubKey==null){
            pubKey = KeyTools.getPubKey(toId, sessionHandler, talkIdHandler, contactHandler, apipClient);
            if(pubKey==null) return;
        }
        boolean done = talkUnit.makeTalkUnit(sessionKey, priKey, pubKey);
        if(!done)return;
        if(talkUnit.getUnitEncryptType()!=null) {
            switch (talkUnit.getUnitEncryptType()){
                case SymKey -> {
                    if(sessionKey==null)return;
                    pubKey=null;
                    priKey=null;
                }
                case AsyOneWay -> {
                    if(pubKey==null)return;
                    sessionKey=null;
                }
                case AsyTwoWay -> {
                    if(pubKey==null||priKey==null)return;
                    sessionKey=null;
                }
                default -> {
                    return;
                }
            }
        }
        done = sendTalkUnitWithNetty(talkUnit, sessionKey, priKey, pubKey, channelSet);
        if(done){
            talkUnit.setStata(TalkUnit.State.SENT);
        }
        talkUnitHandler.put(talkUnit);
    }
}
