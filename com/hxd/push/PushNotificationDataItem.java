package com.hxd.push;

public interface PushNotificationDataItem {
	public byte getItemID();
	public short getItemLength();
	public byte[] getItemData();
}
