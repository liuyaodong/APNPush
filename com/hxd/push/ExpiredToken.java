package com.hxd.push;


import java.util.Date;

public class ExpiredToken {
	private final Date expirationDate;
	private final String tokenString;
	
	public ExpiredToken(final Date expirationDate, final String tokenString) {
		this.expirationDate = expirationDate;
		this.tokenString = tokenString;
	}
	
	public ExpiredToken(final Date expirationDate, final byte[] tokenBytes) {
		this(expirationDate, ExpiredToken.tokenBytesToString(tokenBytes));
	}

	public Date getExpirationDate() {
		return expirationDate;
	}

	public String getTokenString() {
		return tokenString;
	}
	
	public static String tokenBytesToString(final byte[] tokenBytes) {
		final StringBuilder builder = new StringBuilder();

        for (final byte b : tokenBytes) {
        	final String hexString = Integer.toHexString(b & 0xff);
        	
        	if (hexString.length() == 1) {
        		// We need a leading zero
        		builder.append("0");
        	}
        	
            builder.append(hexString);
        }

        return builder.toString();
	}
}
