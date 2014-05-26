package com.hxd.push;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.ReplayingDecoder;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.concurrent.GenericFutureListener;

public class FeedbackService {
	
	private enum FeedbackDecodeState {
		TIMESTAMP,
		TOKEN_LENGTH,
		DEVICE_TOKEN
	}
	
	private final EventLoopGroup nioEventLoopGroup;
	private final Bootstrap bootstrap;
	private final APNSEnviroment apnsEnviroment;
	private final FeedbackController feedbackController;
	private final Logger logger = LoggerFactory.getLogger(FeedbackService.class);
	
	private Channel channel;
	private Date expirationDate;
	private byte[] token;
	public FeedbackService(final FeedbackController feedbackController, final APNSEnviroment apnsEnviroment, final String apsPKCS12FilePath, final String password) throws KeyStoreException {
		this.apnsEnviroment = apnsEnviroment;
		this.feedbackController = feedbackController;
		this.nioEventLoopGroup = new NioEventLoopGroup();
		final KeyStore keyStore = KeyStore.getInstance("PKCS12");
		final char[] keystorePassword = password.toCharArray();
		
		try {
			FileInputStream keystoreInputStream;
			keystoreInputStream = new FileInputStream(apsPKCS12FilePath);
			keyStore.load(keystoreInputStream, keystorePassword);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		final FeedbackService connection = this;
		this.bootstrap = new Bootstrap()
							.group(this.nioEventLoopGroup)
							.channel(NioSocketChannel.class)
							.handler(new ChannelInitializer<SocketChannel>() {

								@Override
								protected void initChannel(SocketChannel socketChannel) throws Exception {
									final ChannelPipeline channelPipeline = socketChannel.pipeline();
									channelPipeline.addLast("ssl", SslHandlerUtil.createSslHandler(keyStore, keystorePassword));
									channelPipeline.addLast("readTimeoutHandler", new ReadTimeoutHandler(30, TimeUnit.SECONDS));
									channelPipeline.addLast("decoder", new FeedbackTupleDecoder(connection));
									channelPipeline.addLast("handler", new FeedbackHandler());
								}
							});   
	}
	
	private class FeedbackTupleDecoder extends ReplayingDecoder<FeedbackDecodeState> {
		
		private final FeedbackService connection;
		public FeedbackTupleDecoder(final FeedbackService connection) {
			super(FeedbackDecodeState.TIMESTAMP);
			this.connection = connection;
		}

		@Override
		protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
			switch (state()) {
			case TIMESTAMP:
				final long timestamp = (in.readInt() & 0xFFFFFFFFL) * 1000L;
				this.connection.expirationDate = new Date(timestamp);
				checkpoint(FeedbackDecodeState.TOKEN_LENGTH);
				break;
			case TOKEN_LENGTH:
				this.connection.token = new byte[in.readShort() & 0x0000FFFF];
				checkpoint(FeedbackDecodeState.DEVICE_TOKEN);
				break;
			case DEVICE_TOKEN:
				in.readBytes(this.connection.token);
				out.add(new ExpiredToken(this.connection.expirationDate, this.connection.token));
				checkpoint(FeedbackDecodeState.TIMESTAMP);
				break;
			default:
				break;
			}
		}
		
	}
	
	private class FeedbackHandler extends SimpleChannelInboundHandler<ExpiredToken> {
		
		@Override
		protected void channelRead0(ChannelHandlerContext ctx, ExpiredToken expiredToken) throws Exception {
			feedbackController.feedbackServiceDidRead(expiredToken);
		}
		
		@Override
		public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
				throws Exception {
			logger.error(String.format("FeedbackHandler caught an exception: %s", cause.getMessage()));
			if (cause instanceof ReadTimeoutException) {
				logger.info(String.format("Feedback service read timeout"));
				System.out.println("Feedback service read timeout");
			} else {
				logger.warn("Caught an unexpected exception while waiting for feedback.", cause);
			}
			ctx.close();
		}
	}
	
	public synchronized boolean start() throws InterruptedException {
		ChannelFuture channelFuture = this.bootstrap.connect(this.apnsEnviroment.getFeedbackHost(),
															this.apnsEnviroment.getFeedbackPort());
		channelFuture.await();
		if (channelFuture.isSuccess()) {
			logger.info("Feedback service connected!");
			this.feedbackController.feedbackServiceDidStart();
			this.channel = channelFuture.channel();
			this.channel.closeFuture().addListener(new GenericFutureListener<ChannelFuture>() {
				@Override
				public void operationComplete(ChannelFuture channelFuture) throws Exception {
					logger.info("Feedback service channel is closed");
					feedbackController.feedbackServiceDidClose();
				}
			});
			return true;
		} else {
			logger.error("Feedback service connection failed!");
			return false;
		}
	}
	
	public synchronized void shutdown() {
		this.nioEventLoopGroup.shutdownGracefully();
	}
}
