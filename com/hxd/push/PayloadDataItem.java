package com.hxd.push;

import java.nio.charset.Charset;

public class PayloadDataItem implements PushNotificationDataItem {

	private byte[] itemData;
	private final String payload;
	
	public PayloadDataItem(final String payload) {
		super();
		this.payload = payload;
	}

	@Override
	public byte getItemID() {
		return 2;
	}

	@Override
	public short getItemLength() {
		return (short)this.getItemData().length;
	}

	@Override
	public byte[] getItemData() {
		if (this.itemData == null) {
			this.itemData = this.payload.getBytes(Charset.forName("UTF-8"));
		}
		return this.itemData;
	}

}
