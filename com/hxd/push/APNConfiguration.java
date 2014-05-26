package com.hxd.push;

import java.util.Map;

public class APNConfiguration {
	private String pkcs12;
	private String password;
	private String alertBody;
	private int badge;
	private boolean isDebug;
	private String logPath;
	private String tokenFile;
	private Map<String, String> customField;
	
	public String getPkcs12() {
		return pkcs12;
	}
	public void setPkcs12(String pkcs12) {
		this.pkcs12 = pkcs12;
	}
	public String getPassword() {
		return password;
	}
	public void setPassword(String password) {
		this.password = password;
	}
	public String getAlertBody() {
		return alertBody;
	}
	public void setAlertBody(String alertBody) {
		this.alertBody = alertBody;
	}
	public int getBadge() {
		return badge;
	}
	public void setBadge(int badge) {
		this.badge = badge;
	}
	
	public boolean isDebug() {
		return isDebug;
	}
	public void setDebug(boolean isDebug) {
		this.isDebug = isDebug;
	}
	public String getTokenFile() {
		return tokenFile;
	}
	public void setTokenFile(String tokenFile) {
		this.tokenFile = tokenFile;
	}
	public String getLogPath() {
		String splash = "";
		if (!logPath.endsWith("/")) {
			splash = "/";
		}
		return logPath + splash;
	}
	public void setLogPath(String logPath) {
		this.logPath = logPath;
	}
	public Map<String, String> getCustomField() {
		return customField;
	}
	public void setCustomField(Map<String, String> customField) {
		this.customField = customField;
	}
	
	@Override
	public String toString() {
		return String.format("%s: pkcs12:%s, pwd:%s, alertBody:%s, badge:%d, isDebug:%s, logFile:%s, tokenFile:%s",
				super.toString(), this.getPkcs12(), this.getPassword(), this.getAlertBody(), this.getBadge(), 
				this.isDebug(), this.getLogPath(), this.getTokenFile()) ;
	}
}
