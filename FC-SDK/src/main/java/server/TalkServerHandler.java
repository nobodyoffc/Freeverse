package server;

import appTools.Settings;
import ch.qos.logback.classic.Logger;
import handlers.AccountHandler;

import constants.IndicesNames;
import constants.CodeMessage;
import crypto.*;
import fcData.*;
import handlers.SessionHandler;
import handlers.TalkUnitHandler;
import handlers.TalkUnitSender;
import io.netty.channel.*;
import io.netty.handler.timeout.IdleStateEvent;

import org.jetbrains.annotations.Nullable;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import utils.JsonUtils;
import utils.TalkUnitExecutor;
import utils.http.AuthType;
import utils.http.RequestMethod;

import java.io.IOException;
import java.util.*;

import static fcData.TalkUnit.parseTalkUnitData;
import static fcData.TalkUnit.isRequest;
import static handlers.TalkUnitSender.sayGot;

public class TalkServerHandler extends SimpleChannelInboundHandler<ByteBuf> {
    public final static Logger log = (Logger) LoggerFactory.getLogger(TalkServerHandler.class);
    private final TalkServer talkServer;

    public TalkServerHandler(TalkServer talkServer) {
        this.talkServer = talkServer;
    }

    private void sendServiceInfo(ChannelHandlerContext ctx) {
        Signature signature = new Signature();
        byte[] priKey = Decryptor.decryptPriKey(talkServer.getSettings().getMyPriKeyCipher(),talkServer.getSettings().getSymKey());
        signature.sign(talkServer.getService().toJson(), priKey, AlgorithmId.BTC_EcdsaSignMsg_No1_NrC7);
        TalkUnitSender.sendBytesByNettyCtx(signature.toJson().getBytes(), ctx);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        Channel channel = ctx.channel();
        System.out.println("New client connected: " + channel.remoteAddress());
        sendServiceInfo(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        Channel channel = ctx.channel();
        System.out.println("Client disconnected: " + channel.remoteAddress());
        synchronized (talkServer.getFidChannelsMap()) {
            String fid = talkServer.getChannelFidMap().get(ctx.channel());
            if(fid==null)return;
            Set<Channel> channels = talkServer.getFidChannelsMap().get(fid);
            if(channels!=null)channels.remove(ctx.channel());
            else return;
            if (talkServer.getFidChannelsMap().get(fid).isEmpty())
                talkServer.getFidChannelsMap().remove(fid);
        }
        synchronized (talkServer.getChannelFidMap()) {
            talkServer.getChannelFidMap().remove(ctx.channel());
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
        try {
            byte[] bytes = TalkUnitExecutor.readAndCheck(ctx, msg,log);
            if (bytes == null) return;


            CryptoDataByte cryptoDataByte = CryptoDataByte.fromBundle(bytes);
            if (cryptoDataByte == null) {
                log.debug("It is not a cipher.");
                return;
            }

            TalkUnit decryptedTalkUnit;
            SessionHandler fcSessionHandler = talkServer.getFcSessionHandler();


            byte[] dealerPriKey = talkServer.getDealerPriKey();
            decryptedTalkUnit = TalkUnit.decryptUnit(cryptoDataByte, dealerPriKey, null, fcSessionHandler);
            if(decryptedTalkUnit==null)return;
            

            if(decryptedTalkUnit.getTo().equals(talkServer.getDealer())){
                if (handleDealerTalkUnit(ctx, decryptedTalkUnit, fcSessionHandler, dealerPriKey)) return;
            }

            if (talkServer.getAccountHandler().isBadBalance(decryptedTalkUnit.getBy())) {
                TalkUnit talkUnit = new TalkUnitExecutor(talkServer).reply(CodeMessage.Code1004InsufficientBalance,null,null,Op.NOTIFY);
                new TalkUnitSender(talkServer).sendTalkUnit(talkUnit, ctx.channel());
                return;
            }

            saveToEs(decryptedTalkUnit);

            relay(decryptedTalkUnit, decryptedTalkUnit.getBySession(), ctx);

        } catch (Exception e) {
            e.printStackTrace();
            log.debug("Failed to parse msg as CryptoDataByte: {}", e.getMessage());
        }

    }

    private boolean handleDealerTalkUnit(ChannelHandlerContext ctx, TalkUnit decryptedTalkUnit, SessionHandler fcSessionHandler, byte[] dealerPriKey) {
        TalkUnit parsedTalkUnit;
        TalkUnitHandler talkUnitHandler = talkServer.getTalkUnitHandler();
        parsedTalkUnit = parseTalkUnitData(decryptedTalkUnit, dealerPriKey, fcSessionHandler);
        if(parsedTalkUnit==null) return true;

        Boolean gotOrRelay = TalkUnitExecutor.checkGotOrRelay(decryptedTalkUnit, talkUnitHandler);
        if(!Boolean.FALSE.equals(gotOrRelay)) return true;

        sayGot(ctx, dealerPriKey, parsedTalkUnit);

        TalkUnitExecutor talkUnitExecutor = new TalkUnitExecutor(talkServer);
        talkUnitExecutor.executeTalkUnit(parsedTalkUnit);

        TalkUnitSender talkUnitSender = new TalkUnitSender(talkServer);
        talkUnitSender.sendTalkUnitListByNetty(talkUnitExecutor.getSendTalkUnitList(), ctx);

        String result = talkUnitExecutor.getDisplayText();

        if(result!=null) {
            log.info(result);
            System.out.println(result);
        }

        if(talkUnitExecutor.getCode()==0){
            if(parsedTalkUnit.getDataType()!= TalkUnit.DataType.ENCRYPTED_GOT && parsedTalkUnit.getDataType()!= TalkUnit.DataType.ENCRYPTED_RELAYED)
                talkUnitHandler.put(parsedTalkUnit);
        }
        if(isRequest(parsedTalkUnit.getDataType())){
            return true;
        }
        return false;
    }

    private void saveToEs(TalkUnit finalTalkUnit) throws IOException {
        // Create a copy of the TalkUnit with serialized data
        TalkUnit indexTalkUnit = new TalkUnit();
        indexTalkUnit.setId(finalTalkUnit.getId());
        indexTalkUnit.setBy(finalTalkUnit.getBy());
        indexTalkUnit.setTo(finalTalkUnit.getTo());
        indexTalkUnit.setTime(finalTalkUnit.getTime());
        indexTalkUnit.setNonce(finalTalkUnit.getNonce());
        indexTalkUnit.setFrom(finalTalkUnit.getFrom());
        indexTalkUnit.setIdType(finalTalkUnit.getIdType());
        indexTalkUnit.setDataType(finalTalkUnit.getDataType());
        // Convert complex data object to string representation
        if (finalTalkUnit.getData() != null) {
            indexTalkUnit.setData(JsonUtils.toJson(finalTalkUnit.getData()));
        }

        talkServer.getEsClient().index(i -> i
            .index(Settings.addSidBriefToName(talkServer.getService().getId(), IndicesNames.DATA))
            .id(finalTalkUnit.makeId())
            .document(indexTalkUnit));
    }
    private void relay(TalkUnit decryptedTalkUnit, FcSession bySession, ChannelHandlerContext ctx) {
        List<String> receiverList = makeTalkIdList(decryptedTalkUnit);
        if (receiverList == null || receiverList.isEmpty()) return;
        boolean done = sendToAll(decryptedTalkUnit.getFrom(), decryptedTalkUnit, receiverList, talkServer.getFcSessionHandler(),talkServer.getFidChannelsMap(),talkServer.getPrice(),talkServer.getAccountHandler());
        if(done) {
            byte[] relayedBytes = TalkUnit.notifyRelayed(decryptedTalkUnit, talkServer.getDealerPriKey(), bySession);
            if(relayedBytes!=null)
                TalkUnitSender.sendBytesByNettyCtx(relayedBytes, ctx);
        }
    }

    private boolean sendToAll(String fromFid, TalkUnit talkUnit, List<String> toFidList, SessionHandler sessionHandler, Map<String,Set<Channel>> fidChannelsMap, Long price, AccountHandler accountHandler) {
        if(talkUnit==null)return false;
        List<String> sentFidList = new ArrayList<>();
        byte[] bytes=null;
        for (String fid : toFidList) {
            FcSession fcSession = sessionHandler.getSessionById(fid);
            CryptoDataByte cryptoDataByte;
            if(fcSession!=null && fcSession.getKeyBytes()!=null) {
                cryptoDataByte = talkUnit.encryptUnit(fcSession.getKeyBytes(), null, null, EncryptType.SymKey);
            } else {
                String pubKey = KeyTools.getPubKey(fid,talkServer.getFcSessionHandler(),null,null,talkServer.getApipClient());
                cryptoDataByte = talkUnit.encryptUnit(null, talkServer.getDealerPriKey(), pubKey, EncryptType.AsyTwoWay);
                sessionHandler.addNewSession(fid,pubKey);
            }

            if(cryptoDataByte==null || cryptoDataByte.getCode()!=0)continue;
            bytes = cryptoDataByte.toBundle();

            if (TalkUnitSender.sendToFidChannels(fidChannelsMap, bytes, fid)) continue;
            log.debug("Sent to {}.",fid);

            sentFidList.add(fid);
        }
        if(bytes!=null && bytes.length>0)
            accountHandler.addAllBalance(bytes.length,fromFid,sentFidList,price);

        return true;
    }

//    public static void writeToChannel(Channel channel, byte[] data) {
//        if (channel != null && channel.isActive()) {
//            ByteBuf buf = Unpooled.buffer(4 + data.length)
//                .writeInt(data.length)
//                .writeBytes(data);
//            channel.writeAndFlush(buf);
//        }
//    }

    @Nullable
    private List<String> makeTalkIdList(TalkUnit talkUnit) {
        List<String> fidList = new ArrayList<>();
        switch (talkUnit.getIdType()) {
            case FID -> fidList.add(talkUnit.getTo());
            case GROUP -> {
                List<String> groupMemberList = getGroupMemberList(talkUnit.getTo());
                if(groupMemberList==null) return null;
                fidList.addAll(groupMemberList);
            }
            case TEAM -> {
                List<String> teamMemberList = getTeamMemberList(talkUnit.getTo());
                if(teamMemberList==null) return null;
                fidList.addAll(teamMemberList);
            }
            default -> fidList =null;
        }
        return fidList;
    }

    private List<String> getGroupMemberList(String gid) {
        Map<String, String[]> result = talkServer.getApipClient().groupMembers(RequestMethod.POST, AuthType.FC_SIGN_BODY, gid);
        if(result==null || result.isEmpty() || result.get(gid)==null)return null;
        return Arrays.stream(result.get(gid)).toList();
    }

    private List<String> getTeamMemberList(String tid) {
        Map<String, String[]> result = talkServer.getApipClient().teamMembers(RequestMethod.POST, AuthType.FC_SIGN_BODY, tid);
        if(result==null || result.isEmpty() || result.get(tid)==null)return null;
        return Arrays.stream(result.get(tid)).toList();
    }

    private void addNewChannel(String userFid, Channel channel,TalkServer talkServer){
        Set<Channel> channels = talkServer.getFidChannelsMap().get(userFid);
        if(channels!=null){
            channels.add(channel);
        }else{
            talkServer.getFidChannelsMap().put(userFid,new HashSet<>(Arrays.asList(channel)));
        }
        talkServer.getChannelFidMap().put(channel,userFid);
        System.out.println("User " + userFid + " joined at " + channel.remoteAddress());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Channel error for client {}: {}", ctx.channel().remoteAddress(), cause.getMessage());
        // Don't immediately close the connection unless necessary
        if (cause instanceof IOException) {
            log.warn("IO Exception - closing channel");
            ctx.close();
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            // Connection is idle, but don't disconnect - just log it
            // System.out.println("Connection idle from: " + ctx.channel().remoteAddress());
        }
    }
    public boolean sendBytes(byte[] bytes, ChannelHandlerContext ctx){
        return TalkUnitSender.sendBytesByNettyCtx(bytes,ctx);
    }
}
