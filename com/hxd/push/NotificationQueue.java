package com.hxd.push;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// ThreadSafe
// Defined the capacity for memory consideration.
// Reclaimed notifications should not excess the capacity of the reclaimQueue, otherwise runtime exceptions will be raised.
public class NotificationQueue implements NotificationEnqueue, NotificationReclaimableConsumeQueue {
	private final BlockingQueue<SendablePushNotification> workingQueue;
	private final BlockingQueue<SendablePushNotification> reclaimQueue;
	private final Logger logger = LoggerFactory.getLogger(NotificationQueue.class);
	
	public NotificationQueue(final int capacity) {
		this.workingQueue = new LinkedBlockingQueue<SendablePushNotification>(capacity);
		this.reclaimQueue = new LinkedBlockingQueue<SendablePushNotification>(capacity);
	}
	
	// Delegate thread safety to workingQueue
	public void put(SendablePushNotification notification) throws InterruptedException {
		this.workingQueue.put(notification);
	}
	
	// Delegate thread safety to workingQueue and reclaimQueue, and the independence between them
	public SendablePushNotification pollNotification(long timeout, TimeUnit unit) throws InterruptedException {
		SendablePushNotification notification = null;
		notification = this.reclaimQueue.poll(); // No need to block on reclaimQueue
		if (notification == null) {
			notification = this.workingQueue.poll(timeout, unit);
		}
		return notification;
	}
	
	// Delegate thread safety to reclaimQueue
	public void reclaimFailedNotifications(Collection<SendablePushNotification> notifications) {
		if (notifications != null) {
			this.reclaimQueue.addAll(notifications);
		}
	}
	
	// Delegate thread safety to reclaimQueue
	public void reclaimFailedNotification(SendablePushNotification notification) {
		if (notification != null) {
			this.reclaimQueue.add(notification);
		}
	}
	
	public void reportRejectedNotification(SendablePushNotification rejectedNotification) {
		synchronized (this) {
			//TODO write it to file
			this.logger.warn("Rejected notification detected. Discard it");
		}
	}
	
	public Collection<SendablePushNotification> remainNotifications() {
		synchronized (this) {
			SendablePushNotification[] workingQueueNotifications = this.workingQueue.toArray(new SendablePushNotification[0]);
			SendablePushNotification[] reclaimQueueNotifications = this.workingQueue.toArray(new SendablePushNotification[0]);
			Collection<SendablePushNotification> unsentNotifications = new ArrayList<SendablePushNotification>(workingQueueNotifications.length + reclaimQueueNotifications.length);
			unsentNotifications.addAll(Arrays.asList(workingQueueNotifications));
			unsentNotifications.addAll(Arrays.asList(reclaimQueueNotifications));
			return unsentNotifications;
		}
	}
}
