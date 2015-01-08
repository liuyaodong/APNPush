package com.hxd.push;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.security.KeyStoreException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.hxd.push.APNLogEnvironment.APNLogLevel;

public class BroadcastPushManager implements NotificationProducerDelegate, PushControllerDelegate {
	private final Logger logger = LoggerFactory.getLogger(BroadcastPushManager.class);
	private final APNConfiguration configuration;
	private final PushController pushController;
	private final APNLogEnvironment logEnvironment;
	private final String payload;
	private final BufferedWriter invalidTokenBufferedWriter;
	
	private BroadcastPushManager(final APNConfiguration configuration, final APNLogEnvironment logEnvironment) {
		this.logger.debug("configuration: " + configuration);
		this.configuration = configuration;
		this.logEnvironment = logEnvironment;
		APNPayloadBuilder payloadBuilder = new APNPayloadBuilder().alertBody(this.configuration.getAlertBody())
			    .badge(configuration.getBadge())
			    .sound("Default");
		if (this.configuration.getCustomField() != null && !this.configuration.getCustomField().isEmpty()) {
			payloadBuilder.customFields(this.configuration.getCustomField());
		}
		this.payload = payloadBuilder.build();
		this.logger.debug("payload: " + payload);
		
		APNConnectionManager pushController = null;
		try {
			pushController = new APNConnectionManager(this.configuration.isDebug() ? APNSEnviroment.getSandboxEnvironment() : APNSEnviroment.getProductionEnvironment(),
									this.configuration.getPkcs12(),
									this.configuration.getPassword(),
									this);
		} catch (KeyStoreException e) {
			e.printStackTrace();
		}
		this.pushController = pushController;
		
		BufferedWriter bufferedWriter = null;
		try {
			bufferedWriter = new BufferedWriter(new FileWriter(new File(logEnvironment.getLogFilePath() + "invalidToken.txt"), true));
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("Invalid tokens file writer initialization failed.");
		}
		this.invalidTokenBufferedWriter = bufferedWriter;
	}
	
	private void doPush() {
		this.pushController.start();
		BroadcastNotificationProducer producer = new BroadcastNotificationProducer(pushController, this.configuration.getTokenFile(), this.payload, this);
		ExecutorService producerService = Executors.newSingleThreadExecutor();
		producerService.submit(producer);
		producerService.shutdown();
	}
	
	@Override
	public void pushControllerDidStop() {
		this.logEnvironment.logUnsentTokens(this.pushController.getRemainNotifications());
		try {
			this.invalidTokenBufferedWriter.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public void handleRejectedNotification(final String token, final RejectedNotificationReason reason) {
		if (reason.equals(RejectedNotificationReason.INVALID_TOKEN) || reason.equals(RejectedNotificationReason.INVALID_TOKEN_SIZE)) {
			try {
				this.invalidTokenBufferedWriter.write(token);
				this.invalidTokenBufferedWriter.newLine();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void producerDidComplete(BroadcastNotificationProducer producer, Exception e) {
		String messageString = "Tokens have been processed";
		if (e != null) {
			messageString += " with exception: " + e.getMessage();
		}
		System.out.println(messageString);
		System.out.println("All connection will be closed in 5 minutes.");
		ScheduledExecutorService scheduleToTerminate = Executors.newSingleThreadScheduledExecutor();
		scheduleToTerminate.schedule(new Runnable() {
			@Override
			public void run() {
				System.out.println("Try to terminate");
				pushController.stop();
			}
		}, 5 * 60, TimeUnit.SECONDS);
		scheduleToTerminate.shutdown();
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
			System.err.println("File not found, push manager abort!");
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
		
		System.out.println("Begin to push...");
		try {
			new BroadcastPushManager(configuration, logEnvironment).doPush();
		} catch (Throwable e) {
			e.printStackTrace();
		}
		
	}




}
