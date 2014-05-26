package com.hxd.push;

import java.nio.ByteBuffer;

public class NotificationIdentifierDataItem implements PushNotificationDataItem {

	private byte[] itemData;
	private final int identifier;
	
	public NotificationIdentifierDataItem(final int identifier) {
		super();
		this.identifier = identifier;
	}

	@Override
	public byte getItemID() {
		return 3;
	}

	@Override
	public short getItemLength() {
		return 4;
	}

	@Override
	public byte[] getItemData() {
		if (this.itemData == null) {
			this.itemData = ByteBuffer.allocate(4).putInt(this.identifier).array();
		}
		return this.itemData;
	}
	
	@Override
	public String toString() {
		String byteString = new String();
		for (byte b : this.getItemData()) {
			byteString += String.format("0x%02X", b);
		}
		return String.format("%s; ItemID: %d; ItemLength: %d; ItemData: %s", super.toString(), this.getItemID(), this.getItemLength(), byteString);
	}

}
