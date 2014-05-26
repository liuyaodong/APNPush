package com.hxd.push;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;


public class APNLogEnvironment {
	
	public enum APNLogLevel {
		LOG_LEVEL_DEBUG("DEBUG"),
		LOG_LEVEL_INFO("INFO"),
		LOG_LEVEL_WARN("WARN"),
		LOG_LEVEL_ERROR("ERROR");
		
	    private APNLogLevel(final String text) {
	        this.text = text;
	    }

	    private final String text;

	    @Override
	    public String toString() {
	        return text;
	    }
	}
	
	
	private final String logFilePath;
	private BufferedWriter unsentTokenBufferWriter = null;
	
	public APNLogEnvironment() {
		this("./");
	}
	
	public APNLogEnvironment(String logFilePath) {
		if (logFilePath == null) {
			logFilePath = "./";
		}
		
		if (logFilePath.endsWith("/")) {
			this.logFilePath = logFilePath;
		} else {
			this.logFilePath = logFilePath + "/";
		}
	}

	public String getLogFilePath() {
		return logFilePath;
	}
	
	public boolean configure(APNLogLevel logLevel) {
		Date thisDate = new Date();
		SimpleDateFormat dateFormat = new SimpleDateFormat("_yyyyMMdd_HHmmss");
		
		String folderName = this.logFilePath + "apn" + dateFormat.format(thisDate);
		File folder = new File(folderName);
		if (!folder.mkdir()) {
			System.err.println(String.format("Folder at path(%s) create failed!", folderName));
			return false;
		}
		
		System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, logLevel.toString());
		System.setProperty(org.slf4j.impl.SimpleLogger.LOG_FILE_KEY, folderName + "/apn.log");
		return true;
	}
	
	public synchronized void logUnsentTokens(Collection<? extends APNBasicNotification> unsentNotifications) {
		try {
			File unsentTokenFile = new File(this.getLogFilePath() + "unsent_token.txt");
			
			FileWriter fw = new FileWriter(unsentTokenFile, false);
			this.unsentTokenBufferWriter = new BufferedWriter(fw);
			this.unsentTokenBufferWriter.newLine();
			Date logDate = new Date();
			this.unsentTokenBufferWriter.write("=========" + logDate + "=========");
			this.unsentTokenBufferWriter.newLine();
			
			for (APNBasicNotification pushNotification : unsentNotifications) {
				this.unsentTokenBufferWriter.write(pushNotification.getToken());
				this.unsentTokenBufferWriter.newLine();
			}
			
			this.unsentTokenBufferWriter.write("============= end ============");
			this.unsentTokenBufferWriter.newLine();
			this.unsentTokenBufferWriter.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
