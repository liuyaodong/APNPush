package com.hxd.push;

public interface NotificationProducerDelegate {
	void producerDidComplete(final BroadcastNotificationProducer producer, Exception e);
}
