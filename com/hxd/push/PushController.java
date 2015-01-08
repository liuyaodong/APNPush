package com.hxd.push;

import java.security.KeyStore;
import java.util.Collection;

import io.netty.channel.EventLoopGroup;

public interface PushController {
	KeyStore getKeyStore();
	APNSEnviroment getApnsEnviroment();
	char[] getKeystorePassword();
	EventLoopGroup getNioEventLoopGroup();
	
	public void setPushControllerDelegate(final PushControllerDelegate delegate);
	public PushControllerDelegate getPushControllerDelegate();
	public Collection<SendablePushNotification> getRemainNotifications();
	
	/*
	 * Push Actions
	 */
	public void start();
	public void stop();
	public void doPush(String token, String payload) throws InterruptedException;
	
	/*
	 * Callback for PushRunnable
	 */
	void pushRunnableWillTerminate();
	void reportRejectedNotification(final String token, final RejectedNotificationReason reason);
}
