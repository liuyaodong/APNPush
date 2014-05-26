package com.hxd.push;

public interface APNBasicNotification {
	String getPayload();
	String getToken();
	int getIdentifier();
}
