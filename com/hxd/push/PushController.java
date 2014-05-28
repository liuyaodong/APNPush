package com.hxd.push;

public interface PushController {
	void apnConnectionManagerDidStop();
	void handleRejectedNotification(final String token, final RejectedNotificationReason reason);
}
