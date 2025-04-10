package clients;

import java.util.HashMap;
import java.util.Map;

import apip.apipData.RequestBody;
import fcData.*;
import fcData.TalkUnit.IdType;
import handlers.TalkUnitSender;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;
import utils.FchUtils;
import utils.JsonUtils;
import utils.TalkUnitExecutor;

import static fcData.TalkUnit.*;

public class TalkClientHandler extends SimpleChannelInboundHandler<ByteBuf> {
    private TalkClient talkClient;
    public TalkClientHandler(TalkClient talkClient) {
        this.talkClient = talkClient;
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        System.out.println("DEBUG: Channel registered");
        super.channelRegistered(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
        System.out.println("DEBUG: Entering channelRead0");
//
//        // Add null check for msg
//        if (msg == null) return;
//
//        // Check if there are enough bytes to read the length (4 bytes)
//        if (msg.readableBytes() < 4) {
//            System.out.println("DEBUG: readableBytes is less than 4: "+msg.readableBytes());
//            return;
//        }
//        // Read the length prefix
//        int length = msg.readInt();
//        // Check if we have enough bytes for the complete message
//        if (msg.readableBytes() < length) {
//            System.out.println("DEBUG: readableBytes is " + msg.readableBytes() + " less than length " + length);
//            return;
//        }
//        // Create a byte array of the exact size we need
//        byte[] bytes = new byte[length];
//        // Read the message bytes
//        msg.readBytes(bytes);
//        // Add null check before processing
//        if (bytes.length == 0) return;
        byte[] bytes = TalkUnitExecutor.readAndCheck(ctx, msg,log);
        if (bytes == null) return;

        //TODO
        System.out.println(new String(bytes));
        System.out.println(talkClient.isRunning());

        if (!checkServerFirstMsg(ctx, bytes)) return;

        // Process regular messages
        byte[] myPriKey = talkClient.getMyPriKey();
        TalkUnit decryptedTalkUnit = TalkUnit.decryptUnit(bytes, myPriKey,talkClient.serverSession, null );

        if(decryptedTalkUnit==null){
            talkClient.disply("Failed to decrypt talkUnit:"+new String(bytes));
            return;
        }

        TalkUnit parsedTalkUnit=null;
        try {
            parsedTalkUnit = parseTalkUnitData(decryptedTalkUnit, myPriKey, talkClient.getSessionHandler());
        }catch (Exception e){
            e.printStackTrace();
        }

        if(parsedTalkUnit==null){
            talkClient.disply("Failed to parse talkUnit:"+ JsonUtils.toNiceJson(decryptedTalkUnit));
            return;
        }

        Boolean gotOrRelay = TalkUnitExecutor.checkGotOrRelay(parsedTalkUnit, talkClient.getTalkUnitHandler());
        if(!Boolean.FALSE.equals(gotOrRelay))return;

//        byte[] serverSessionKey =null;
//        if(talkClient.getServerSession()!=null && talkClient.getServerSession().getKeyBytes()!=null)
//                serverSessionKey= talkClient.getServerSession().getKeyBytes();
//        FcSession serverSession = talkClient.getServerSession();
//        FcSession fromSession = prepareSession(parsedTalkUnit.getFrom(),talkClient.getSessionHandler(),talkClient.getTalkIdHandler(),talkClient.getContactHandler(),talkClient.getApipClient());
//
        TalkUnitSender.sayGot(ctx, myPriKey, parsedTalkUnit);

        TalkUnitExecutor talkUnitExecutor = new TalkUnitExecutor(talkClient);

        talkUnitExecutor.executeTalkUnit(parsedTalkUnit);
        TalkUnitSender talkUnitSender = new TalkUnitSender(talkClient);
        talkUnitSender.sendTalkUnitListByNetty(talkUnitExecutor.getSendTalkUnitList(), ctx);

        talkClient.getTalkUnitHandler().put(parsedTalkUnit);

        if(talkUnitExecutor.getPayToForBytes()!=null)
            pay(talkUnitExecutor.getPayToForBytes());

        if(talkUnitExecutor.getDisplayText()!=null)
            talkClient.getDisplayer().displayMessage(talkUnitExecutor.getDisplayText());

        if(parsedTalkUnit.getDataType()!= TalkUnit.DataType.ENCRYPTED_GOT && parsedTalkUnit.getDataType()!= TalkUnit.DataType.ENCRYPTED_RELAYED)
            talkClient.getTalkUnitHandler().put(parsedTalkUnit);
    }

    private boolean checkServerFirstMsg(ChannelHandlerContext ctx, byte[] bytes) {
        if(!talkClient.isRunning()){
            try{
                Signature signature = Signature.fromJson(new String(bytes));
                if(!signature.verify()){
                    talkClient.disply("Failed to verify service information:\n"+signature.toNiceJson());
                    return false;
                }
                talkClient.getDisplayer().displayAppNotice("Got TALK service info from "+ ctx.channel().remoteAddress().toString()+":\n" + JsonUtils.strToNiceJson(signature.getMsg()));
            }catch(Exception e){
                talkClient.disply("Failed to verify service information:\n"+new String(bytes));
                return false;
            }
            talkClient.setRunning(true);
            return true;
        }
        return true;
    }

    private void pay(Map<String,Integer> payToForBytes) {
        try{
            String priceStr = talkClient.getApiProvider().getApiParams().getPricePerKBytes();
            long price = FchUtils.coinStrToSatoshi(priceStr);
            Map<String,Long> payList = new HashMap<>();
            for(Map.Entry<String,Integer> entry:payToForBytes.entrySet()){
                String fid = entry.getKey();
                int bytesLength = entry.getValue();
                bytesLength = bytesLength/1024;
                long amount = price * bytesLength;
                payList.put(fid,amount);
            }
            RequestBody requestBody = new RequestBody();
            requestBody.setOp(Op.PAY);
            requestBody.setData(payList);
            TalkUnit rawTalkUnit = new TalkUnit(talkClient.getMyFid(), requestBody, TalkUnit.DataType.ENCRYPTED_REQUEST, talkClient.getDealer(), IdType.FID);
            TalkUnit talkUnit = TalkUnit.makeTalkUnit(rawTalkUnit,talkClient.getServerSession().getKeyBytes(),talkClient.getMyPriKey(), talkClient.getDealerPubKey());
            if (talkUnit == null) return;
            boolean done = talkClient.sendTalkUnit(rawTalkUnit, talkClient.getSessionKey(), talkClient.getMyPriKey(),talkClient.getDealerPubKey());
            talkClient.getTalkUnitHandler().saveTalkUnit(rawTalkUnit, done);
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        System.err.println("Error: " + cause.getMessage());
        cause.printStackTrace();
        ctx.close();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("DEBUG: Channel active");
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        System.out.println("DEBUG: Disconnected from server");
        
        // Attempt to reconnect if the client should still be running
        if (talkClient.isRunning()) {
            ctx.channel().eventLoop().execute(() -> {
                System.out.println("Attempting to reconnect...");
                talkClient.tryReconnect();
            });
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            // Send heartbeat
            ctx.writeAndFlush(Unpooled.EMPTY_BUFFER);
        }
    }


    public TalkClient getTalkClient() {
        return talkClient;
    }

    public void setTalkClient(TalkClient talkClient) {
        this.talkClient = talkClient;
    }
}
