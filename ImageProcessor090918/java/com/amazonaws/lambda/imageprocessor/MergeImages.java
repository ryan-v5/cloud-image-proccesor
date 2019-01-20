package com.amazonaws.lambda.imageprocessor;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import javax.imageio.ImageIO;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;

public class MergeImages implements RequestHandler<S3Event, String> {
	
	private static final String DST_BUCKET = S3Buckets.IMG_DONE;

	private AmazonS3 s3 = AmazonS3ClientBuilder.standard().build();

	private static String srcBucket;
	private static String srcKey;
	private static S3Object jsonFile;
	private static Image image;
	private static String dstKey;
	private static BufferedImage merged;
	private static BufferedImage[] subImages;
	private static InputStream outputData;
	private static ObjectMetadata meta;
	private static ByteArrayOutputStream outputStream;
	public MergeImages() {
	}

	// Test purpose only.
	MergeImages(AmazonS3 s3) {
		this.s3 = s3;
	}

	@Override
	public String handleRequest(S3Event event, Context context) {
		context.getLogger().log("Received event: " + event);

		// Get the object from the event and show its content type
		srcBucket = event.getRecords().get(0).getS3().getBucket().getName();
		srcKey = event.getRecords().get(0).getS3().getObject().getKey();

		try {
			jsonFile = s3.getObject(new GetObjectRequest(srcBucket, srcKey));

			// create an ArrayList of the object keys by reading the json file containing
			image = JsonOperations.jsonFileToImageObject(jsonFile);

			// set value for the destination key
			dstKey = createFileNameWithFilterUsed();

			// make an array list of the sub-image keys
			ArrayList<String> keys = image.getSubImages();

			// check if all sub_images are present in bucket
			CheckForSubImages checkForSubImages = new CheckForSubImages(keys, s3, srcBucket);
			Thread thread = new Thread(checkForSubImages);
			thread.start();

			// if the subimages size is equivalent to one, the image has not been split
			if (keys.size() == 1) {
				System.out.println("Image was not split, no merge necessary...");
				try {
					thread.join();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				s3.copyObject(srcBucket, image.getSubImages().get(0), DST_BUCKET, dstKey);
				return srcKey + ": OK";
			}

			// Array to hold BufferedImage objects
			subImages = new BufferedImage[keys.size()];

			// need to have subimages at this point, wait until they're all accounted for
			try {
				thread.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			// get the subimages
			getSubImages(keys, s3);

			// merge the sub-images
			merged = joinImages(subImages);

			writeMergedImageToInputStream();

			createMetaData();

			saveImageToS3Bucket(s3);

			// clean-up
			keys.clear();

		} catch (Exception e) {
			e.printStackTrace();
		}
		return srcKey + " : OK";
	}

	public static BufferedImage joinImages(BufferedImage[] images) throws IllegalArgumentException {

		if (images.length == 4) {
			BufferedImage result = joinHorizontal(joinVertical(images[0], images[1], images.length / 2),
					joinVertical(images[2], images[3], images.length / 2), images.length / 2);
			return result;
		} else if (images.length == 8) {
			BufferedImage leftSide = joinHorizontal(joinVertical(images[0], images[1], 2),
					joinVertical(images[2], images[3], 2), 2);
			BufferedImage rightSide = joinHorizontal(joinVertical(images[4], images[5], 2),
					joinVertical(images[6], images[7], 2), 2);
			BufferedImage result = joinHorizontal(leftSide, rightSide, 2);
			return result;
		} else {
			throw new IllegalArgumentException();
		}
	}

	public static BufferedImage joinHorizontal(BufferedImage i1, BufferedImage i2, int numToMerge) {

		// if the height of img1 and 2 match then throw exception
		if (i1.getHeight() != i2.getHeight()) {
			throw new IllegalArgumentException("Images i1 and i2 are not the same height");
		}

		// create an empty BufferedImage with the dimensions required to include all
		// images to be merged
		BufferedImage result = new BufferedImage(i1.getWidth() * numToMerge, i1.getHeight(),
				BufferedImage.TYPE_USHORT_GRAY);
		Graphics2D graphics = result.createGraphics();
		graphics.drawImage(i1, 0, 0, null);
		graphics.drawImage(i2, i1.getWidth(), 0, null);

		return result;

	}

	public static BufferedImage joinVertical(BufferedImage i1, BufferedImage i2, int numToMerge) {

		if (i1.getWidth() != i2.getWidth()) {
			throw new IllegalArgumentException("Images i1 and i2 are not the same width");
		}

		// create an empty BufferedImage with the dimensions required to include all
		// images to be merged
		BufferedImage result = new BufferedImage(i1.getWidth(), i1.getHeight() * numToMerge,
				BufferedImage.TYPE_USHORT_GRAY);
		Graphics2D graphics = result.createGraphics();
		graphics.drawImage(i1, 0, 0, null);
		graphics.drawImage(i2, 0, i1.getHeight(), null);

		return result;
	}

	public static void getSubImages(ArrayList<String> keys, AmazonS3 s3) throws IOException {
		// get the image files by looping through and getting their keys
		System.out.println("\n Sub images about to be processed: ");
		for (int loop = 0; loop < keys.size(); loop++) {
			// test purposes
			System.out.println(keys.get(loop));
			S3Object s3Object = s3.getObject(new GetObjectRequest(srcBucket, keys.get(loop)));
			subImages[loop] = ImageIO.read(s3Object.getObjectContent());
		}
	}

	public static void writeMergedImageToInputStream() throws IOException {
		// write the merged image to an input stream
		outputStream = new ByteArrayOutputStream();
		ImageIO.write(merged, image.getImageType(), outputStream);
		outputData = new ByteArrayInputStream(outputStream.toByteArray());
	}

	public static void createMetaData() {
		// set the meta data for the S3 object
		meta = new ObjectMetadata();
		meta.setContentLength(outputStream.size());
		meta.setContentType(image.getContentTypeForMetaData());
	}

	public static void saveImageToS3Bucket(AmazonS3 s3) {
		// upload the image file to the S3 Bucket
		s3.putObject(DST_BUCKET, dstKey, outputData, meta);
		System.out.println("Merged " + image.getSubImages() + " and saved to " + DST_BUCKET + "/" + dstKey);
	}
	
	public static String createFileNameWithFilterUsed() {
		String prefix = S3Buckets.getPrefixForFileName(image.getFilterId());
		//may be original file name instead
		return prefix + image.getImageKey();	
	}
}