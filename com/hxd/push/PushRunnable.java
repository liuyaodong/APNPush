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
import io.netty.channel.nio.AbstractNioChannel.NioUnsafe;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.util.concurrent.GenericFutureListener;

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
	
	private final NotificationReclaimableConsumeQueue notificationQueue;
	private final PushController controller;
	private final Bootstrap bootstrap;
	private final Logger logger = LoggerFactory.getLogger(PushRunnable.class);
	private final SentNotificationCache notificationCache;
	private final Object channelWritabilityNotifier = new Object();
	
	private boolean requestTermination = false;
	private Channel channel;
	private AtomicInteger notificationsWriten = new AtomicInteger(0);
	
	public PushRunnable(final NotificationReclaimableConsumeQueue notificationQueue, final PushController controller) {
		this.notificationQueue = notificationQueue;
		this.controller = controller;
		this.notificationCache = new SentNotificationCache(APNConnectionManager.SENT_BUFFER_CAPACITY_PER_TASK);
		final PushRunnable pushRunnable = this;
		this.bootstrap = new Bootstrap()
							.group(controller.getNioEventLoopGroup())
							.channel(NioSocketChannel.class)
							.option(ChannelOption.SO_KEEPALIVE, true)
							.handler(new ChannelInitializer<SocketChannel>() {
			@Override
			protected void initChannel(final SocketChannel channel) throws Exception {
				final ChannelPipeline pipeline = channel.pipeline();
				pipeline.addLast("ssl", SslHandlerUtil.createSslHandler(controller.getKeyStore(), controller.getKeystorePassword()));
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
		public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
			super.channelWritabilityChanged(ctx);
			synchronized (channelWritabilityNotifier) {
				if (ctx.channel().isWritable()) {
					channelWritabilityNotifier.notify();
				}
			}
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
		ChannelFuture future = this.bootstrap.connect(this.controller.getApnsEnviroment().getApnsGatewayHost(), this.controller.getApnsEnviroment().getApnsGatewayPort());
		future.await();
		if (future.isSuccess()) {
			this.channel = future.channel();
			this.logger.debug(this.toString() + "Connected");
			return true;
		} else {
			this.logger.error("Connect to APS error! Reason: " + future.cause().getMessage());
			return false;
		}
	}
	
	// NOT Thread Safe: Should be private and called only once
	// Return true if writing operation performed, false if not
	private boolean sendNotification(final SendablePushNotification notification) throws InterruptedException {
		if (notification == null) {
			this.notificationsWriten.set(0);
			this.channel.flush();
			return true;
		}

		synchronized (this.channelWritabilityNotifier) {
			while (!this.channel.isWritable()) {
				if (!this.channel.isActive() || this.requestTermination || Thread.currentThread().isInterrupted()) {
					this.requestTermination = true;
					return false;
				}
				this.channelWritabilityNotifier.wait(5 * 1000); // 5 seconds
			}
		}
		
		this.notificationCache.addNotification(notification);
		this.channel.write(notification).addListener(new GenericFutureListener<ChannelFuture>() {

			@Override
			public void operationComplete(ChannelFuture future) throws Exception {
				if (!future.isSuccess()) {
					handlerNotificationIOError(notification.getIdentifier());
				}
			}
		});
		
		if (this.notificationsWriten.incrementAndGet() == APNConnectionManager.BATCH_SIZE) {
			this.notificationsWriten.set(0);
			this.channel.flush();
		}
		
		return true;
	}
	
	private void handlerNotificationIOError(final int failedNotificationIdentifier) {
		SendablePushNotification cachedNotification = this.notificationCache.getAndRemoveNotificationWithIdentifier(failedNotificationIdentifier);
		this.logger.debug("Channel handle failed IO notification id: " + failedNotificationIdentifier + " sendablenotification: " + cachedNotification);
		this.notificationQueue.reclaimFailedNotification(cachedNotification);
		this.requestTermination = true;
	}
	
	private void handlerNotificationRejectedError(final RejectedNotification rejectedNotification) {
		ArrayList<SendablePushNotification> notifications = this.notificationCache.getAllNotificationsAfterIdentifierAndPurgeCache(rejectedNotification.getIdentifier());
		if (notifications.size() > 0) {
			SendablePushNotification rejectedOne = notifications.get(0);
			this.controller.reportRejectedNotification(rejectedOne.getToken(), rejectedNotification.getRejectionReason());
			notifications.remove(0);
			this.notificationQueue.reclaimFailedNotifications(notifications);
			this.logger.debug(String.format("Hanlder rejected notification, reclaimed %d ", notifications.size()));
		} else {
			this.logger.error("Failed to find the rejected notification in SentNotificationCache");
		}
		this.requestTermination = true;
	}
	
	private synchronized void close() throws InterruptedException {
		if (this.channel != null && this.channel.isOpen()) {
			((NioUnsafe)this.channel.unsafe()).read(); // Temporary strategy, reference from https://github.com/relayrides/pushy/issues/6 for details.
			this.logger.debug("channel is about to close");
			this.channel.close();
		} else {
			this.logger.info("Channel is already closed!");
		}
		
		this.channel.closeFuture().await();
		if (this.channel.closeFuture().isSuccess()) {
			this.logger.debug("Successfully closed abandoned channel.");
			
		} else if (this.channel.closeFuture().cause() != null) {
			this.logger.error(String.format("Failed to close abandoned channel. Because: %s", this.channel.closeFuture().cause().getMessage()));
		} else {
			this.logger.error(String.format("Failed to close abandoned channel, with no causes returned"));
		}
	}
	
	@Override
	public void run(){
		boolean interrupted = false;
		try {
			if ( !this.connect() ) {
				return;
			}
			
			this.logger.debug("pushRunnable ready to send" + this);
			while (!this.requestTermination && !Thread.currentThread().isInterrupted()) {
				SendablePushNotification notification = this.notificationQueue.pollNotification(PushRunnable.POLL_TIMEOUT, PushRunnable.MILLISECONDS_TIME_UNIT);
				if (!sendNotification(notification)) {
					this.notificationQueue.reclaimFailedNotification(notification);
				}
			}
			this.logger.debug("pushRunnable finish sending" + this);
			this.close();
		} catch (InterruptedException e) {
			this.logger.debug("pushRunnable aborted due to InterruptedException!");
			interrupted = Thread.interrupted();
		} finally {
			try {
				this.logger.debug("pushRunnable finally close");
				this.close(); // Calling close() more than once is harmless
			} catch (InterruptedException e) {
				// Task is going to the end, so just ignore it.
				this.logger.debug("exception happend in finnally");
			}
			if (interrupted) {
				Thread.currentThread().interrupt();
			}
			this.controller.pushRunnableWillTerminate();
		}
	}

}
