package com.amazonaws.lambda.imageprocessor;

import java.awt.image.BufferedImage;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;

public class EdgeDetector extends Filter implements RequestHandler<S3Event, String> {
	private static float lowThreshold;
	private static float highThreshold;
	private static BufferedImage edgeImage;

	//private AmazonS3 s3 = AmazonS3ClientBuilder.standard().build();

	public EdgeDetector() {
	}

	/**
	 * Test purpose only. EdgeDetector(AmazonS3 s3) { this.s3 = s3; }
	 */

	@Override
	public String handleRequest(S3Event event, Context context) {
		context.getLogger().log("Received event: " + event);

		try {
			super.setBucketsAndKeys(event);
			
			super.sanityCheckBuckets();

			// if it is the json file, move it to the next bucket
			if (super.moveJsonFile()) {
				System.out.println("Moved JSON file to next bucket. Function terminated");
				return "";
			}
			
			setFilterParams();
			
			setImageType();
			
			super.checkIfImageTypeIsValid();
			
			super.downloadImageToInputStream();
			
			super.readSourceImage();
			
			filterOperation();
			
			super.reencodeImage(edgeImage);
			
			super.uploadImageToS3();
			
			return "OK";

		} catch (Exception e) {
			e.printStackTrace();
			context.getLogger().log(String.format("Error getting object %s from bucket %s. Make sure they exist and"
					+ " your bucket is in the same region as this function.", srcKey, srcBucket));
			return "Error";
		}
	}

	@Override
	public void setFilterParams() {
		// Get the filter parameters and the image type from the file name
		params = srcKey.split("_");
		lowThreshold = Float.parseFloat(params[1]);
		highThreshold = Float.parseFloat(params[2]);
	}
	
	@Override
	public void setImageType() {
		imageType = params[3];
	}

	@Override
	public void filterOperation() {
		// create detector
		CannyEdgeDetector detector = new CannyEdgeDetector();
		// adjust its parameters as desired
		detector.setLowThreshold(lowThreshold);
		detector.setHighThreshold(highThreshold);
		// apply it to an image
		detector.setSourceImage(srcImage);
		detector.process();
		edgeImage = detector.getEdgesImage();
	}
	
}