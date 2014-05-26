package com.hxd.push;

public class PriorityDataItem implements PushNotificationDataItem {

	private static final byte[] itemData = new byte[] {10};
	@Override
	public byte getItemID() {
		return 5;
	}

	@Override
	public short getItemLength() {
		return 1;
	}

	@Override
	public byte[] getItemData() {
		return PriorityDataItem.itemData;
	}

}
