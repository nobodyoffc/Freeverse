//package nettyClient;
//
//import clients.TalkClientHandler;
//import io.netty.bootstrap.Bootstrap;
//import io.netty.buffer.ByteBuf;
//import io.netty.channel.Channel;
//import io.netty.channel.ChannelInitializer;
//import io.netty.channel.ChannelPipeline;
//import io.netty.channel.EventLoopGroup;
//import io.netty.channel.nio.NioEventLoopGroup;
//import io.netty.channel.socket.SocketChannel;
//import io.netty.channel.socket.nio.NioSocketChannel;
//
//import java.io.BufferedReader;
//import java.io.InputStreamReader;
//import java.net.URL;
//
//public class ClientNetty {
//    private final String host;
//    private final int port;
//    private Channel channel;
//
//    public ClientNetty(String host, int port) {
//        this.host = host;
//        this.port = port;
//    }
//
//    public void start() throws Exception {
//        EventLoopGroup group = new NioEventLoopGroup();
//
//        try {
//            Bootstrap bootstrap = new Bootstrap()
//                .group(group)
//                .channel(NioSocketChannel.class)
//                .handler(new ChannelInitializer<SocketChannel>() {
//                    @Override
//                    protected void initChannel(SocketChannel ch) {
//                        ChannelPipeline pipeline = ch.pipeline();
//                        pipeline.addLast(new TalkClientHandler());
//                    }
//                });
//
//            this.channel = bootstrap.connect(host, port).sync().channel();
//
//            BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
//            while (true) {
//                System.out.println("Input message to send to server:");
//                String message = in.readLine();
//                ByteBuf buf = channel.alloc().buffer();
//                buf.writeBytes(message.getBytes());
//                channel.writeAndFlush(buf);
//            }
//        } finally {
//            group.shutdownGracefully();
//        }
//    }
//
//    public static ClientNetty fromUrl(String url){
//        ClientNetty clientNetty;
//        try{
//            URL url1 = new URL(url);
//            clientNetty = new ClientNetty(url1.getHost(), url1.getPort());
//        }catch (Exception e) {
//            clientNetty = null;
//        }
//        return clientNetty;
//    }
//
//    public void sendMessage(String message) {
//        if (channel != null && channel.isActive()) {
//            ByteBuf buf = channel.alloc().buffer();
//            buf.writeBytes(message.getBytes());
//            channel.writeAndFlush(buf);
//        } else {
//            System.err.println("Cannot send message - channel is not active");
//        }
//    }
//}
