package com.hxd.push;

import java.security.KeyStore;

import io.netty.channel.EventLoopGroup;

public interface PushContext {
	KeyStore getKeyStore();
	APNSEnviroment getApnsEnviroment();
	char[] getKeystorePassword();
	EventLoopGroup getNioEventLoopGroup();
	void pushRunnableWillTerminate();
}
