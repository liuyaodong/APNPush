package com.hxd.push;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class APNManager implements PushContext {
	
	public static final int CONCURRENT_CONNECTIONS = 14; //max 15
	public static final int SENT_BUFFER_CAPACITY_PER_TASK = 300;
	public static final int MAX_PRODUCER_QUEUE_SIZE = SENT_BUFFER_CAPACITY_PER_TASK * (CONCURRENT_CONNECTIONS + 1);
	public static final int BATCH_SIZE = 32;
	
	private final char[] keystorePassword;
	private final APNSEnviroment apnsEnviroment;
	private final EventLoopGroup nioEventLoopGroup;
	private final Logger logger = LoggerFactory.getLogger(APNManager.class);
	private final ExecutorService pushConnectionPool;
	private final NotificationQueue notificationQueue;
	private final KeyStore keyStore;
	private final APNLogEnvironment logEnvironment;
	
	public APNManager(APNSEnviroment apnsEnviroment, final String apsPKCS12FilePath, final String password, final APNLogEnvironment logEnvironment) throws KeyStoreException {
		this.apnsEnviroment = apnsEnviroment;
		this.keystorePassword = password.toCharArray();
		this.keyStore = KeyStore.getInstance("PKCS12");
		this.nioEventLoopGroup = new NioEventLoopGroup();
		this.pushConnectionPool = Executors.newFixedThreadPool(APNManager.CONCURRENT_CONNECTIONS);
		//Queue size should bigger than the sum of all task buffered notifications.
		this.notificationQueue = new NotificationQueue(APNManager.MAX_PRODUCER_QUEUE_SIZE);
		this.logEnvironment = logEnvironment;
		
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

	public synchronized void start() {
		for (int i = 0; i < APNManager.CONCURRENT_CONNECTIONS; i++) {
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
	    		this.logEnvironment.logUnsentTokens(this.notificationQueue.remainNotifications());
	    }
	}

	@Override
	public synchronized void pushRunnableWillTerminate() {
		this.pushConnectionPool.submit(new PushRunnable(this.notificationQueue, this));
	}
}
