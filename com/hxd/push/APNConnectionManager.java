package com.hxd.push;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class APNConnectionManager implements PushContext {
	
	public static final int CONCURRENT_CONNECTIONS = 10; //max 15
	public static final int SENT_BUFFER_CAPACITY_PER_TASK = 5000; // 5000
	public static final int MAX_PRODUCER_QUEUE_SIZE = SENT_BUFFER_CAPACITY_PER_TASK * (CONCURRENT_CONNECTIONS + 1);
	public static final int BATCH_SIZE = 32; //32
	
	private final char[] keystorePassword;
	private final APNSEnviroment apnsEnviroment;
	private final EventLoopGroup nioEventLoopGroup;
	private final Logger logger = LoggerFactory.getLogger(APNConnectionManager.class);
	private final ExecutorService pushConnectionPool;
	private final NotificationQueue notificationQueue;
	private final KeyStore keyStore;
	private final PushController pushController;
	
	public APNConnectionManager(APNSEnviroment apnsEnviroment, final String apsPKCS12FilePath, final String password, final PushController pushController) throws KeyStoreException {
		this.apnsEnviroment = apnsEnviroment;
		this.keystorePassword = password.toCharArray();
		this.keyStore = KeyStore.getInstance("PKCS12");
		this.nioEventLoopGroup = new NioEventLoopGroup();
		this.pushConnectionPool = Executors.newFixedThreadPool(APNConnectionManager.CONCURRENT_CONNECTIONS);
		//Queue size should bigger than the sum of all task buffered notifications.
		this.notificationQueue = new NotificationQueue(APNConnectionManager.MAX_PRODUCER_QUEUE_SIZE);
		this.pushController = pushController;
		
		try {
			FileInputStream keystoreInputStream;
			keystoreInputStream = new FileInputStream(apsPKCS12FilePath);
			this.keyStore.load(keystoreInputStream, keystorePassword);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public APNSEnviroment getApnsEnviroment() {
		return apnsEnviroment;
	}

	public KeyStore getKeyStore() {
		return keyStore;
	}

	public char[] getKeystorePassword() {
		return keystorePassword;
	}
	
	public EventLoopGroup getNioEventLoopGroup() {
		return nioEventLoopGroup;
	}

	public NotificationEnqueue getNotificationEnqueue() {
		return this.notificationQueue;
	}
	
	public Collection<SendablePushNotification> remainNotifications() {
		return this.notificationQueue.remainNotifications();
	}

	public synchronized void start() {
		for (int i = 0; i < APNConnectionManager.CONCURRENT_CONNECTIONS; i++) {
			this.pushConnectionPool.submit(new PushRunnable(this.notificationQueue, this));
		}
	}
	
	public synchronized void stop() {
		this.pushConnectionPool.shutdownNow();
		this.nioEventLoopGroup.shutdownGracefully();
	    try {
	    		if (!this.pushConnectionPool.awaitTermination(60, TimeUnit.SECONDS)) {
		           this.logger.warn("Pool did not terminate");
		    }
	    } catch (InterruptedException ie) {
	    		this.pushConnectionPool.shutdownNow();
	    		Thread.currentThread().interrupt();
	    } finally {
	    		this.pushController.apnConnectionManagerDidStop();
	    }
	}

	@Override
	public synchronized void pushRunnableWillTerminate() {
		this.pushConnectionPool.submit(new PushRunnable(this.notificationQueue, this));
	}

	@Override
	public synchronized void reportRejectedNotification(final String token, final RejectedNotificationReason reason) {
		this.pushController.handleRejectedNotification(token, reason);
	}
}
