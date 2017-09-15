package com.lianjia.keybox;

/**
 * Created by Administrator on 2017/9/13.
 */

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.mqtt.*;

/**
 * netty-test  KeyBoxApp
 *
 * @author Guangshan
 * @summary netty-test  NettyServer
 * @Copyright (c) 2017, Guangshan Group All Rights Reserved
 * @since 2017/8/31 23:37
 */
public class KeyBoxApp {


    private int port;

    public KeyBoxApp(int port) {
        this.port = port;
    }

    public void run() throws Exception {
        EventLoopGroup bossGroup = new NioEventLoopGroup(); // (1)
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap(); // (2)
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class) // (3)
                    .childHandler(new ChannelInitializer<SocketChannel>() { // (4)
                        @Override
                        public void initChannel(SocketChannel ch) throws Exception {
                            ch.pipeline().addLast(
                                    //new TestServerHandler(),
                                    new MqttDecoder(),
                                    // 不知为何，下面两个只有一个的read会被执行？
                                    new ClientInBoundHandler(),
                                    // 因为没有执行fire，所以中断了
                                    // ctx.fireChannelRead(message);
                                    new MyServerHandler(),
                                    MqttEncoder.INSTANCE);
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)          // (5)
                    .childOption(ChannelOption.SO_KEEPALIVE, true); // (6)

            // Bind and start to accept incoming connections.

            // 前面都只是配置和初始化，只有bind只有才开始开启服务器
            // bind过程发生了以下几件事情：在doBind中initAndRegister()，之后
            // (Netty自己的Channel)Channel channel = channelFactory.newChannel()
            // 其实是用上面配置的channel(NioServerSocketChannel.class)来进行实例化
            // 在NioServerSocketChannel的实例化过程中，会创建一个Java自己的ServerSocketChannel
            // 这个是这样来的this(newSocket(DEFAULT_SELECTOR_PROVIDER))，newSocket是这样的：provider.openServerSocketChannel();
            // provider是SelectorProvider。所以其实NioServerSocketChannel的父类AbstractNioChannel其实是java的Channel包装类
            // 同时在上面的构造过程中生成了一个比较重要的对象ChannelPipeline，大致作用就是提供一个Channel的流水线操作，之后再讲。
            // 流水线操作中可以注册各种ChannelHandler，ServerBootstrap的handler和childHandler就是用于注册这个玩意儿的。
            // 在init(channel)的最后，会首先给当前channel的pipeline添加一个ChannelInitializer
            // 作用是在初始化channel的时候为这个channel的pipeline添加上面设置的childHandler
            // 注意这里分两步是因为netty的机制，netty创建了两个eventLoopGroup，第一个eventLoopGroup监听的是连接事件，第二个group监听读写事件
            // 所以第一个channel其实是连接的channel，在这里创建时注册handler没问题，第二个是读写的channel，使用childHandler也没问题。
            // 执行到最后ChannelFuture regFuture = config().group().register(channel)，这里注册channel，其实最终调用的还是SelectableChannel
            // 的register方法，这个方法最终调用的是selector的register方法，最后调用的是AbstractSelector的register方法。
            // 返回的ChannelFuture是经过Netty的Channel包装过的Future接口。其实核心用的还是java的channel和selectionKey。
            // 经过了多层包装最后得到了netty框架。（上面的注册方法其实都是开了线程使用EventLoop来注册的）
            // 经过了层层初始化后，回到最初的bind，调用doBind
            // doBind里有用channel.eventLoop().execute执行了一个异步的bind操作。
            // bind执行完成，调用sync()其实是等待ChannelFuture即ChannelPromise执行完毕。
            ChannelFuture f = b.bind(port).sync(); // (7)

            // Wait until the server socket is closed.
            // In this example, this does not happen, but you can do that to gracefully
            // shut down your server.
            // 最后就比较简单了，调用channel的closeFuture返回一个closeFuture，调用这个future的sync一直等待到服务器关闭
            // 当然因为没有逻辑执行关闭，所以这里就一直wait了，等待接受客户端来连接。
            f.channel().closeFuture().sync();
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }

    public static void main(String[] args) throws Exception {
        int port;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        } else {
            port = 1883;
        }
        new KeyBoxApp(port).run();
    }


    /**
     * Handles a server-side channel.
     */
    public static class MyServerHandler extends ChannelInboundHandlerAdapter { // (1)

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) { // (2)
            // Discard the received data silently.
            System.out.println(1);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) { // (4)
            // Close the connection when an exception is raised.
            cause.printStackTrace();
            ctx.close();
        }
    }

    /**
     * Handles a server-side channel.
     */
    public static class TestServerHandler extends ChannelInboundHandlerAdapter { // (1)

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) { // (2)
            // Discard the received data silently.
//            System.out.println(msg);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) { // (4)
            // Close the connection when an exception is raised.
            cause.printStackTrace();
            ctx.close();
        }
    }

    public static class ClientInBoundHandler extends SimpleChannelInboundHandler<MqttMessage> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, MqttMessage msg) throws Exception {
            if (msg.fixedHeader().messageType() == MqttMessageType.CONNECT) {
                MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.CONNACK, false, MqttQoS.AT_LEAST_ONCE, false, 0);
                MqttConnAckVariableHeader variableHeader = new MqttConnAckVariableHeader(MqttConnectReturnCode.CONNECTION_ACCEPTED, true);
                MqttConnAckMessage connectAckMessage = new MqttConnAckMessage(fixedHeader, variableHeader);
                ctx.channel().writeAndFlush(connectAckMessage);
            }
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            System.out.println("channelActive");


        }

    }


}
