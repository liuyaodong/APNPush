package com.hxd.push;


public enum RejectedNotificationReason {
	NO_ERROR((byte)0),
	PROCESSING_ERROR((byte)1),
	MISSING_DEVICE_TOKEN((byte)2),
	MISSING_TOPIC((byte)3),
	MISSING_PAYLOAD((byte)4),
	INVALID_TOKEN_SIZE((byte)5),
	INVALID_TOPIC_SIZE((byte)6),
	INVALID_PAYLOAD_SIZE((byte)7),
	INVALID_TOKEN((byte)8),
	SHUTDOWN((byte)10),
	UNKNOWN((byte)255);
	
	private final byte errorCode;
	
	private RejectedNotificationReason(final byte errorCode) {
		this.errorCode = errorCode;
	}
	
	public byte getErrorCode() {
		return this.errorCode;
	}
	
	public static RejectedNotificationReason getByErrorCode(final byte errorCode) {
		for (final RejectedNotificationReason error : RejectedNotificationReason.values()) {
			if (error.errorCode == errorCode) {
				return error;
			}
		}
		
		throw new IllegalArgumentException(String.format("Unrecognized error code: %d", errorCode));
	}
}
