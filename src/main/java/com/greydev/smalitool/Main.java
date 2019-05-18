package com.greydev.smalitool;

import java.io.File;
import java.io.FileNotFoundException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.dom4j.DocumentException;
import org.slf4j.Logger;

import com.greydev.smalitool.model.Apk;
import com.greydev.smalitool.model.Service;

/*
 * To copy any apk from the emulator to your current directory do the following in your terminal:
 * 		'adb shell pm list packages', copy the package name you want to pull 'com.instagram.android'
 * 		'adb shell pm path com.instagram.android'  , returns: package:/data/app/com.instagram.android-8rjwDMo7jo7ONJYp8RUX5Q==/base.apk
 * 		'adb pull /data/app/com.instagram.android-8rjwDMo7jo7ONJYp8RUX5Q==/base.apk .'
 *  DOES NOT WORK WITH GIT BASH ?
 */

/*
 * TODO maybe: smalitool -delete -gui <folderPath>
 */
public class Main {

	private static final Logger LOG = Utils.getConfiguredLogger(Main.class);
	public static final String PREFIX_GENERATED = "generated_";

	public static void main(String[] args) {

		if (args.length != 1) {
			LOG.info("Expecting only one argument");
			System.exit(0);
		}
		LOG.info("input: {}", args[0]);

		File targetFolder = FileSystem.getFolder(args[0]);

		if (targetFolder == null) {
			LOG.info("Directory does not exist or is not a directory!");
			System.exit(0);
		}

		List<File> targetFolderContent = Arrays.asList(targetFolder.listFiles());
		List<File> apkFiles = new ArrayList<>();
		List<String> generatedFolderPaths = new ArrayList<>();
		List<String> folderPathsToDelete = new ArrayList<>();

		targetFolderContent.forEach(item -> {
			if (item.isFile() && (StringUtils.endsWithIgnoreCase(item.getName(), ".apk"))) {
				apkFiles.add(item);
			}
		});

		ProcessBuilder processBuilder = new ProcessBuilder();
		processBuilder.directory(targetFolder); // sets the working directory for the processBuilder

		apkFiles.forEach(apkFile -> {
			String apkPath = apkFile.getPath();
			String generatedApkFolderName = PREFIX_GENERATED + StringUtils.removeEndIgnoreCase(apkFile.getName(), ".apk");
			String commandToExecute = MessageFormat.format("apktool d {0} -o {1}", apkPath, generatedApkFolderName);
			if (Utils.isOsWindows()) {
				processBuilder.command("cmd.exe", "/c", commandToExecute);
			}
			else if (Utils.isOsUnixBased()) {
				processBuilder.command("/bin/bash", "-c", commandToExecute);
			}
			int exitCode = Utils.startProcessWithExitCode(processBuilder);

			if (exitCode == 0) { // success
				generatedFolderPaths.add(targetFolder.getAbsolutePath() + File.separator + generatedApkFolderName);
			}
			// apktool still generates an empty folder on any error
			folderPathsToDelete.add(targetFolder.getAbsolutePath() + File.separator + generatedApkFolderName);
		});

		//		int failCount = Collections.frequency(exitCodes, 1);
		LOG.info(MessageFormat.format("\nFound {0} apk(s) in the destination folder\nsuccessful decoded: {1}\nfailure: {2}\n",
				apkFiles.size(), generatedFolderPaths.size(), apkFiles.size() - generatedFolderPaths.size()));

		LOG.info("Generated Folder(s):");
		generatedFolderPaths.forEach(folderPath -> {
			LOG.info(folderPath);
		});

		// extract apks from all the generated smali folders
		Map<String, Apk> apkList = new HashMap<>();
		for (String apkFolderPath : generatedFolderPaths) {
			Apk apk = null;
			try {
				ApkInfoExtractor apkExtractor = new ApkInfoExtractor();
				apk = apkExtractor.extractApkFromSmaliFolder(apkFolderPath);
			} catch (FileNotFoundException | DocumentException e) {
				e.printStackTrace();
				LOG.error(e.getStackTrace().toString());
			}
			if (apk != null) {
				String apkName = StringUtils.substringAfter(apkFolderPath, PREFIX_GENERATED);
				apkList.put(apkName, apk);
			}
		}
		apkList.values().forEach(apk -> LOG.info(apk.toString()));

		Apk instagramApk = apkList.get("instagram");

		//		Activity myActivity = instagramApk.getActivities().get("com.instagram.share.tumblr.TumblrAuthActivity");
		//		Activity myActivity = instagramApk.getActivities().get("com.instagram.direct.share.handler.DirectShareHandlerActivity");
		//		myActivity.printInfo();
		//		myActivity.printCodeForSmaliClass("TumblrAuthActivity.smali");

		Service myService = instagramApk.getServices().get("com.instagram.inappbrowser.service.BrowserLiteCallbackService");
		myService.printInfo();
		myService.printCodeForSmaliClass("BrowserLiteCallbackService$BrowserLiteCallbackImpl.smali");

		// TODO if they already exist then don't delete, show error message?
		FileSystem.deleteFiles(folderPathsToDelete);
	}

}
