package com.hxd.push;

public interface PushControllerDelegate {
	void pushControllerDidStop();
	void handleRejectedNotification(final String token, final RejectedNotificationReason reason);
}
