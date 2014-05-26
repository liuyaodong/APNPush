package com.hxd.push;

public class APNIdentifierGenerator {
	private static int identifier = 0;
	public synchronized static int generateIdentifer() {
		APNIdentifierGenerator.identifier++;
		return APNIdentifierGenerator.identifier;
	}
}
