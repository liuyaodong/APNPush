package com.hxd.push;

import java.util.ArrayList;
import java.util.Date;

public class SendablePushNotification implements APNBasicNotification {
	
	private final int identifier;
	private final String tokenString;
	private final String payload;
	private ArrayList<PushNotificationDataItem> items;
	
	private static final int ITEM_ID_FIELD_LENGTH = 1;
	private static final int ITEM_DATA_LENGTH_FIELD_LENGTH = 2;
	
	public SendablePushNotification(final String token,
			final String payload, final Date deliveryInvalidationTime) {
		super();
		this.identifier = APNIdentifierGenerator.generateIdentifer();
		this.payload = payload;
		this.tokenString = token;
		this.items = new ArrayList<PushNotificationDataItem>();
		this.items.add(new DeviceTokenDataItem(token));
		this.items.add(new PayloadDataItem(payload));
		this.items.add(new NotificationIdentifierDataItem(identifier));
		
		if (deliveryInvalidationTime != null) {
			this.items.add(new ExpirationDateDataItem(deliveryInvalidationTime));
		} else {
			this.items.add(new ExpirationDateDataItem(new Date(0)));
		}
		this.items.add(new PriorityDataItem());
	}

	public int getFrameLength() {
		int frameLength = 0;
		for (PushNotificationDataItem dataItem : this.getItems()) {
			frameLength += dataItem.getItemLength() + ITEM_ID_FIELD_LENGTH + ITEM_DATA_LENGTH_FIELD_LENGTH;
		}
		return frameLength;
	}

	public ArrayList<PushNotificationDataItem> getItems() {
		return items;
	}
	
	@Override
	public String getPayload() {
		return this.payload;
	}
	@Override
	public String getToken() {
		return this.tokenString;
	}
	@Override
	public int getIdentifier() {
		return identifier;
	}
	
	@Override
	public String toString() {
		return String.format("<%s, identifier: %d>", super.toString(), this.getIdentifier());
	}
	
}
