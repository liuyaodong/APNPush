package com.hxd.push;



public class RejectedNotification {
	private final int sequenceNumber;
	private final RejectedNotificationReason rejectionReason;
	public RejectedNotification(int sequenceNumber,
			RejectedNotificationReason rejectionReason) {
		super();
		this.sequenceNumber = sequenceNumber;
		this.rejectionReason = rejectionReason;
	}
	public int getIdentifier() {
		return sequenceNumber;
	}
	public RejectedNotificationReason getRejectionReason() {
		return rejectionReason;
	}
	
}
