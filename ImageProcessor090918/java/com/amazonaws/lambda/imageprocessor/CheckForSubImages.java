package com.amazonaws.lambda.imageprocessor;

import java.util.ArrayList;

import com.amazonaws.services.s3.AmazonS3;

/**
 * 
 * @author ryanorr
 *
 */
public class CheckForSubImages implements Runnable {

	private static ArrayList<String> keys;
	private static AmazonS3 s3;
	private static String bucketName;

	public CheckForSubImages() {
	}

	public CheckForSubImages(ArrayList<String> keys, AmazonS3 s3, String bucketName) {
		CheckForSubImages.keys = keys;
		CheckForSubImages.s3 = s3;
		CheckForSubImages.bucketName = bucketName;
	}

	public void run() {
		int numToFind = keys.size();
		int numFound;
		
		do {
			numFound = 0;
			for (String sub : keys) {
				if (s3.doesObjectExist(bucketName, sub)) {
					System.out.println("Found key : " + sub);
					numFound++;
				}
			}
			try {
				System.out.println("Number of total found this time : " +numFound +"/" +numToFind);
				System.out.println("Waiting one quarter second before checking again.");
				Thread.sleep(250);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		} while (numToFind != numFound);

		System.out.println("\n All subimages found in bucket. Continue...");
	}
}