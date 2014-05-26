package com.hxd.push;


public class DeviceTokenDataItem implements PushNotificationDataItem {
	
	private final byte[] itemData;
	private static final String NON_HEX_CHARACTER_PATTERN = "[^0-9a-fA-F]";
	
	public DeviceTokenDataItem(String tokenString) {
		final String strippedTokenString = tokenString.replaceAll(NON_HEX_CHARACTER_PATTERN, "");
		final byte[] tokenBytes = new byte[strippedTokenString.length() / 2];
		for (int i = 0; i < strippedTokenString.length(); i += 2) {
			tokenBytes[i / 2] = (byte)Integer.parseInt(strippedTokenString.substring(i, i + 2), 16);
		}
		
		this.itemData = tokenBytes;
	}

	@Override
	public byte getItemID() {
		return 1;
	}

	@Override
	public short getItemLength() {
		return 32;
	}

	@Override
	public byte[] getItemData() {
		return this.itemData;
	}
}
