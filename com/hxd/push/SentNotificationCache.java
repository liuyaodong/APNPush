package com.hxd.push;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

// SentNotificationCache is implemented as a burn-after-querying cache whose size is defined by the capacity ivar.
// When new notification added, if the max size is reached, the eldest notification will be discarded.
public class SentNotificationCache {
	private final int capacity;
	private final LinkedHashMap<Integer, SendablePushNotification> bufferedNotifications;

	public SentNotificationCache(final int capacity) {
		this.bufferedNotifications = new LinkedHashMap<Integer, SendablePushNotification>();
		this.capacity = capacity;
	}

	public synchronized void addNotification(final SendablePushNotification sentNotification) {
		this.bufferedNotifications.put(sentNotification.getIdentifier(), sentNotification);
		if (this.bufferedNotifications.size() == this.capacity) {
			int theEldestKey = ((Integer)this.bufferedNotifications.keySet().toArray()[0]).intValue();
			this.bufferedNotifications.remove(theEldestKey);
		}
	}

	public synchronized SendablePushNotification getAndRemoveNotificationWithIdentifier(final int identifier) {
		SendablePushNotification notification = this.bufferedNotifications.get(identifier);
		if (notification != null) {
			this.bufferedNotifications.remove(identifier);
		}
		return notification;
	}

	// Include notification with this identifier
	public synchronized ArrayList<SendablePushNotification> getAllNotificationsAfterIdentifierAndPurgeCache(final int identifier) {
		Set<Map.Entry<Integer, SendablePushNotification>> entrySet = this.bufferedNotifications.entrySet();
		boolean findIt = false;
		ArrayList<SendablePushNotification> notificationsFollowingTheIdentifier = new ArrayList<SendablePushNotification>();
		for (Map.Entry<Integer, SendablePushNotification> entry : entrySet) {
			if ( (entry.getKey()).intValue() == identifier ) {
				findIt = true;
			}
			
			if (findIt) {
				notificationsFollowingTheIdentifier.add(entry.getValue());
			}
			
		}
		this.bufferedNotifications.clear();
		return notificationsFollowingTheIdentifier;
	}
}
