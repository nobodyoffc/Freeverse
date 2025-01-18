//package nettyServer;
//
//import io.netty.bootstrap.ServerBootstrap;
//import io.netty.channel.*;
//import io.netty.channel.nio.NioEventLoopGroup;
//import io.netty.channel.socket.SocketChannel;
//import io.netty.channel.socket.nio.NioServerSocketChannel;
//import server.HandlerServerTalk;
//import server.TalkServer;
//
//
//public class ServerNetty {
//    private final TalkServer talkServer;
//
//    public ServerNetty(TalkServer talkServer) {
//        this.talkServer = talkServer;
//    }
//
//    public void start() throws Exception {
//        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
//        EventLoopGroup workerGroup = new NioEventLoopGroup();
//
//        try {
//            ServerBootstrap bootstrap = new ServerBootstrap()
//                .group(bossGroup, workerGroup)
//                .channel(NioServerSocketChannel.class)
//                .childHandler(new ChannelInitializer<SocketChannel>() {
//                    @Override
//                    protected void initChannel(SocketChannel ch) {
//                        ChannelPipeline pipeline = ch.pipeline();
//                        pipeline.addLast("handler", new HandlerServerTalk(talkServer));
//                    }
//                });
//
//            ChannelFuture f = bootstrap.bind().sync();
//            System.out.println("Chat server started on port " + talkServer.getPort());
//            f.channel().closeFuture().sync();
//        } finally {
//            bossGroup.shutdownGracefully();
//            workerGroup.shutdownGracefully();
//        }
//    }
//}
