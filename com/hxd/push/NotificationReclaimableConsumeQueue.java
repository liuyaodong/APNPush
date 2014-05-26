package com.hxd.push;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

public interface NotificationReclaimableConsumeQueue {
	void reclaimFailedNotifications(Collection<SendablePushNotification> notifications);
	void reclaimFailedNotification(SendablePushNotification notification);
	SendablePushNotification pollNotification(long timeout, TimeUnit unit) throws InterruptedException;
	void reportRejectedNotification(SendablePushNotification rejectedNotification);
}
