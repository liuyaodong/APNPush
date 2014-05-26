package com.hxd.push;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToByteEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hxd.push.PushNotificationDataItem;
import com.hxd.push.RejectedNotification;
import com.hxd.push.RejectedNotificationReason;
import com.hxd.push.SendablePushNotification;
import com.hxd.push.SslHandlerUtil;


public class PushRunnable implements Runnable {

	private static final long POLL_TIMEOUT = 50;
	private static final TimeUnit MILLISECONDS_TIME_UNIT = TimeUnit.MILLISECONDS;
	private static final long CLOSE_TIMEOUT = 1000;
	
	private final NotificationReclaimableConsumeQueue notificationQueue;
	private final PushContext context;
	private final Bootstrap bootstrap;
	private final Logger logger = LoggerFactory.getLogger(PushRunnable.class);
	private final SentNotificationCache notificationCache;
	
	private Boolean requestTermination = false;
	private Channel channel;
	private AtomicInteger notificationsWriten = new AtomicInteger(0);
	
	public PushRunnable(final NotificationReclaimableConsumeQueue notificationQueue, final PushContext context) {
		this.notificationQueue = notificationQueue;
		this.context = context;
		this.notificationCache = new SentNotificationCache(APNManager.SENT_BUFFER_CAPACITY_PER_TASK);
		final PushRunnable pushRunnable = this;
		this.bootstrap = new Bootstrap()
							.group(context.getNioEventLoopGroup())
							.channel(NioSocketChannel.class)
							.option(ChannelOption.SO_KEEPALIVE, true)
							.handler(new ChannelInitializer<SocketChannel>() {
			@Override
			protected void initChannel(final SocketChannel channel) throws Exception {
				final ChannelPipeline pipeline = channel.pipeline();
				pipeline.addLast("ssl", SslHandlerUtil.createSslHandler(context.getKeyStore(), context.getKeystorePassword()));
				pipeline.addLast("decoder", new RejectedNotificationDecoder());
				pipeline.addLast("encoder", new ApnsPushNotificationEncoder());
				pipeline.addLast("handler", new ApnsErrorHandler(pushRunnable));
			}
		});
	}
	
	
	private class RejectedNotificationDecoder extends ByteToMessageDecoder {

		// Per Apple's docs, APNS errors will have a one-byte "command", a one-byte status, and a 4-byte notification ID
		private static final int EXPECTED_BYTE_SIZE = 6;
		private static final byte EXPECTED_COMMAND = 8;
		
		@Override
		protected void decode(final ChannelHandlerContext context, final ByteBuf in, final List<Object> out) {
			if (in.readableBytes() >= EXPECTED_BYTE_SIZE) {
				final byte command = in.readByte();
				final byte code = in.readByte();
				
				final int notificationId = in.readInt();
				
				if (command != EXPECTED_COMMAND) {
					logger.error(String.format("Unexpected command: %d", command));
				}
				
				final RejectedNotificationReason errorCode = RejectedNotificationReason.getByErrorCode(code);
				out.add(new RejectedNotification(notificationId, errorCode));
			}
		}
	}
	
	private class ApnsPushNotificationEncoder extends MessageToByteEncoder<SendablePushNotification> {

		private static final byte MODERN_FOMAT_NOTIFICATION_COMMAND = 2;

		@Override
		protected void encode(final ChannelHandlerContext context, final SendablePushNotification sendablePushNotification, final ByteBuf out) throws Exception {
		
			out.writeByte(MODERN_FOMAT_NOTIFICATION_COMMAND);
			out.writeInt(sendablePushNotification.getFrameLength());
			
			for (PushNotificationDataItem dataItem : sendablePushNotification.getItems()) {
				out.writeByte(dataItem.getItemID());
				out.writeShort(dataItem.getItemLength());
				out.writeBytes(dataItem.getItemData());
			}
		}
	}

	private class ApnsErrorHandler extends SimpleChannelInboundHandler<RejectedNotification> {

		private final PushRunnable pushRunnable;
		
		public ApnsErrorHandler(final PushRunnable pushRunnable) {
			this.pushRunnable = pushRunnable;
		}
		
		@Override
		protected void channelRead0(final ChannelHandlerContext context, final RejectedNotification rejectedNotification) throws Exception {
			logger.warn(String.format("APNs gateway rejected notification with sequence number %d, reason: %s",
					rejectedNotification.getIdentifier(), rejectedNotification.getRejectionReason()));
			this.pushRunnable.handlerNotificationRejectedError(rejectedNotification);	
		}
		
		@Override
		public void exceptionCaught(final ChannelHandlerContext context, final Throwable cause) {
			// Assume this is a temporary IO problem. Some writes will fail, but will be re-enqueued.
			logger.error(String.format("Caught an exception. Because: %s", cause.getMessage()), cause);
		}
	}
	
	public synchronized void terminate() {
		this.requestTermination = true;
	}
	
	private synchronized boolean connect() throws InterruptedException {
		ChannelFuture future = this.bootstrap.connect(this.context.getApnsEnviroment().getApnsGatewayHost(), this.context.getApnsEnviroment().getApnsGatewayPort());
		future.await();
		if (future.isSuccess()) {
			this.channel = future.channel();
			return true;
		} else {
			this.logger.error("Connect to APS error! Reason: " + future.cause().getMessage());
			return false;
		}
	}
	
	//NOT Thread Safe: Should be private and called only once
	private void sendNotification(final SendablePushNotification notification) throws InterruptedException {
		if (notification == null) {
			this.notificationsWriten.set(0);
			this.channel.flush();
			return;
		}
		
		this.notificationCache.addNotification(notification);
		ChannelFuture future = this.channel.write(notification);
		future.await();
		
		//TODO when interrupted exception during flush data, how to deal with the last added notification
		
		if (!future.isSuccess()) {
			this.logger.error("Write notification to channel error! Reason: " + future.cause().getMessage());
			this.handlerNotificationIOError(notification.getIdentifier());
		}
		
		if (this.notificationsWriten.incrementAndGet() == APNManager.BATCH_SIZE) {
			this.notificationsWriten.set(0);
			this.channel.flush();
		}
	}
	
	private void handlerNotificationIOError(final int failedNotificationIdentifier) {
		SendablePushNotification cachedNotification = this.notificationCache.getAndRemoveNotificationWithIdentifier(failedNotificationIdentifier);
		this.notificationQueue.reclaimFailedNotification(cachedNotification);
		if (!this.channel.isWritable()) {
			this.logger.error("Channel is not writable now, maybe it's broken");
			this.requestTermination = true;
		}
	}
	
	private void handlerNotificationRejectedError(final RejectedNotification rejectedNotification) {
		ArrayList<SendablePushNotification> notifications = this.notificationCache.getAllNotificationsAfterIdentifierAndPurgeCache(rejectedNotification.getIdentifier());
		if (notifications.size() > 0) {
			SendablePushNotification rejectedOne = notifications.get(0);
			this.notificationQueue.reportRejectedNotification(rejectedOne);
			notifications.remove(0);
			this.notificationQueue.reclaimFailedNotifications(notifications);
		} else {
			this.logger.error("Failed to find the rejected notification in SentNotificationCache");
		}
		this.requestTermination = true;
	}
	
	private synchronized void close() throws InterruptedException {
		if (this.channel.isOpen()) {
			this.logger.error("channel is about to close");
			ChannelFuture future = this.channel.close();
			future.await(PushRunnable.CLOSE_TIMEOUT, PushRunnable.MILLISECONDS_TIME_UNIT);
			if (future.isSuccess()) {
				this.logger.debug("Successfully closed abandoned channel.");
				
			} else if (future.cause() != null) {
				this.logger.error(String.format("Failed to close abandoned channel. Because: %s", future.cause().getMessage()));
			} else {
				this.logger.error(String.format("Failed to close abandoned channel, with no causes returned"));
			}
		} else {
			this.logger.warn("Channel is already closed!");
		}
	}
	
	@Override
	public void run(){
		boolean interrupted = false;
		try {
			if ( !this.connect() )
				return;
			
			while (!this.requestTermination && !Thread.currentThread().isInterrupted()) {
				SendablePushNotification notification = this.notificationQueue.pollNotification(PushRunnable.POLL_TIMEOUT, PushRunnable.MILLISECONDS_TIME_UNIT);
				sendNotification(notification);
			}
			this.close();
		} catch (InterruptedException e) {
			this.logger.debug("pushRunnable aborted due to InterruptedException!");
			interrupted = Thread.interrupted();
		} finally {
			try {
				this.close();
			} catch (InterruptedException e) {
				// Task is going to the end, so just ignore it.
				this.logger.debug("exception happend in finnally");
			}
			if (interrupted) {
				Thread.currentThread().interrupt();
			}
			this.context.pushRunnableWillTerminate();
		}
	}

}
