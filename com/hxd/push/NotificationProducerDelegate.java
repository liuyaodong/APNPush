package com.hxd.push;

public interface NotificationProducerDelegate {
	void producerDidComplete(final NotificationProducer producer, Exception e);
}
