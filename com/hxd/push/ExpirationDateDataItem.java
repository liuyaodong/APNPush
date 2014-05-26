package com.hxd.push;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Date;

public class ExpirationDateDataItem implements PushNotificationDataItem {

	private byte[] itemData;
	private final Date expirationData;
	
	public ExpirationDateDataItem(final Date expirationData) {
		super();
		this.expirationData = expirationData;
	}

	@Override
	public byte getItemID() {
		return 4;
	}

	@Override
	public short getItemLength() {
		return 4;
	}

	@Override
	public byte[] getItemData() {
		if (this.itemData == null) {
			this.itemData = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt((int)(this.expirationData.getTime() / 1000)).array();
		}
		return this.itemData;
	}

}
