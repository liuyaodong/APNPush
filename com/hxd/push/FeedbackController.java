package com.hxd.push;


public interface FeedbackController {
	void feedbackServiceDidRead(ExpiredToken expiredToken);
	void feedbackServiceDidClose();
	void feedbackServiceDidStart();
}
