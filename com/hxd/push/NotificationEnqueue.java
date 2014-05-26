package com.hxd.push;

public interface NotificationEnqueue {
	void put(SendablePushNotification notification) throws InterruptedException;
}
