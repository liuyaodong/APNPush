package com.hxd.push;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.security.KeyStoreException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.google.gson.Gson;
import com.hxd.push.APNLogEnvironment.APNLogLevel;

public class FeedbackManager implements FeedbackController {

	private final FeedbackService feedbackService;
	private final BufferedWriter feedbackTokensFileWriter;
	private boolean initialSuccess = false;
	
	public FeedbackManager(final APNConfiguration configuration, final APNLogEnvironment logEnvironment) {
		FeedbackService feedbackService = null;
		try {
			feedbackService = new FeedbackService(this, 
					configuration.isDebug() ? APNSEnviroment.getSandboxEnvironment() : APNSEnviroment.getProductionEnvironment(),
					configuration.getPkcs12(),
					configuration.getPassword());
		} catch (KeyStoreException e) {
			e.printStackTrace();
		}
		this.feedbackService = feedbackService;
		
		final Date currentDate = new Date();
		DateFormat dateFormat = new SimpleDateFormat("yyyy_MM_dd_HH");
		String dateTimeString = dateFormat.format(currentDate);
		BufferedWriter feedbackTokensFileWriter = null;
		try {
			feedbackTokensFileWriter = new BufferedWriter(new FileWriter(new File(logEnvironment.getLogFilePath() + "feedback_" + dateTimeString + ".txt"), true));
			feedbackTokensFileWriter.write("=======" + currentDate + "==========");
			feedbackTokensFileWriter.newLine();
		} catch (IOException e) {
			e.printStackTrace();
			this.initialSuccess = false;
			System.out.println("Feedback tokens file writer initialization failed. Feedback service won't start.");
		}
		this.feedbackTokensFileWriter = feedbackTokensFileWriter;
		this.initialSuccess = true;
	}

	@Override
	public void feedbackServiceDidRead(ExpiredToken expiredToken) {
		try {
			this.feedbackTokensFileWriter.write(String.format("%s-%s"	, expiredToken.getTokenString(), expiredToken.getExpirationDate()));
			this.feedbackTokensFileWriter.newLine();
		} catch (IOException e) {
			e.printStackTrace();
			try {
				this.feedbackTokensFileWriter.close();
			} catch (IOException e1) {
				System.err.println("BufferedWriter cannot be closed!");
				e1.printStackTrace();
			}
		}
	}

	@Override
	public void feedbackServiceDidClose() {
		try {
			this.feedbackTokensFileWriter.close();
		} catch (IOException e) {
			System.err.println("BufferedWriter cannot be closed!");
			e.printStackTrace();
		}
		this.feedbackService.shutdown();
	}

	@Override
	public void feedbackServiceDidStart() {
		// TODO Auto-generated method stub
		
	}
	
	public void doFeedback() {
		
		System.out.println("Ready to do feedback");
		if (!this.initialSuccess) {
			System.out.println("initial failed");
			return;
		}
		System.out.println("feedback start");
		try {
			if (this.feedbackService.start()) {
				System.out.println("feedback service is checking feedback...");
			} else {
				System.out.println("feedback service start failed!");
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
			Thread.currentThread().interrupt();
		}
	}
	
	public static void main(String args[]) {
		if (args.length != 1) {
			System.err.println("Wrong args list!");
			return;
		}
		
		String jsonFilePath = args[0];
		APNConfiguration configuration = null;
		try {
			FileReader jsonFileReader = new FileReader(new File(jsonFilePath));
			configuration = new Gson().fromJson(jsonFileReader, APNConfiguration.class);
		} catch (FileNotFoundException e2) {
			e2.printStackTrace();
		}
		
		if (configuration == null) {
			System.err.println("Illegal json file!");
			return;
		}

		APNLogEnvironment logEnvironment = new APNLogEnvironment(configuration.getLogPath());
		if (!logEnvironment.configure(APNLogLevel.LOG_LEVEL_DEBUG)) {
			System.err.println(String.format("Log environment configuration failed! Apn abort!"));
			return;
		}
		
		new FeedbackManager(configuration, logEnvironment).doFeedback();
	}

}
