package com.hxd.push;


public class APNSEnviroment {
	private final String apnsGatewayHost;
	private final int apnsGatewayPort;
	
	private final String feedbackHost;
	private final int feedbackPort;
	
	public APNSEnviroment(final String apnsGatewayHost, final int apnsGatewayPort, final String feedbackHost, final int feedbackPort) {
		this.apnsGatewayHost = apnsGatewayHost;
		this.apnsGatewayPort = apnsGatewayPort;
		
		this.feedbackHost = feedbackHost;
		this.feedbackPort = feedbackPort;
	}

	public String getApnsGatewayHost() {
		return this.apnsGatewayHost;
	}

	public int getApnsGatewayPort() {
		return this.apnsGatewayPort;
	}

	public String getFeedbackHost() {
		return this.feedbackHost;
	}

	public int getFeedbackPort() {
		return this.feedbackPort;
	}

	public static APNSEnviroment getProductionEnvironment() {
		return new APNSEnviroment("gateway.push.apple.com", 2195, "feedback.push.apple.com", 2196);
	}

	public static APNSEnviroment getSandboxEnvironment() {
		return new APNSEnviroment("gateway.sandbox.push.apple.com", 2195, "feedback.sandbox.push.apple.com", 2196);
	}
}
