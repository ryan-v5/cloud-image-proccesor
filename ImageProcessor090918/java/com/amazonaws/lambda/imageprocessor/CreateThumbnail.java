package com.amazonaws.lambda.imageprocessor;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.event.S3EventNotification.S3EventNotificationRecord;
import com.amazonaws.services.s3.model.ObjectMetadata;

/**
 * Ref - https://docs.aws.amazon.com/lambda/latest/dg/with-s3-example-deployment-pkg.html
 * @author ryanorr
 *
 */
public class CreateThumbnail extends Filter implements RequestHandler<S3Event, String> {
	
	/**
	 * Amazon S3 Client
	 */
	private AmazonS3 s3 = AmazonS3ClientBuilder.standard().build();
	
	private static final float MAX_WIDTH = 100;
	private static final float MAX_HEIGHT = 100;
	private static final String JPG_TYPE = "jpg";
	private static final String JPG_MIME = "image/jpeg";
	private static final String DST_BUCKET_THUMBNAILS = S3Buckets.IMG_THUMBNAILS;
	
	private static BufferedImage resizedImage;

	public String handleRequest(S3Event event, Context context) {
		
		//TODO - add validation to make sure the image is in the right format
		
		try {
			S3EventNotificationRecord record = event.getRecords().get(0);

			srcBucket = record.getS3().getBucket().getName();
			// Object key may have spaces or unicode non-ASCII characters.
			srcKey = record.getS3().getObject().getKey().replace('+', ' ');
			srcKey = URLDecoder.decode(srcKey, "UTF-8");
			dstKey = "thumbnail-" +srcKey;

			// Sanity check: validate that source and destination are different
			// buckets.
			if (srcBucket.equals(DST_BUCKET_THUMBNAILS)) {
				System.out.println("Destination bucket must not match source bucket.");
				return "";
			}
			
			setImageType();
			
			super.checkIfImageTypeIsValid();

			super.downloadImageToInputStream();
			
			super.readSourceImage();
			
			super.setImageHeightAndWidth();
			
			filterOperation();

			reencodeImage(resizedImage);

			uploadImageToS3();
			
			return "OK";
			
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}


	/**
	 * REF NEEDED
	 */
	@Override
	public void setImageType() throws IllegalArgumentException {
		Matcher matcher = Pattern.compile(".*\\.([^\\.]*)").matcher(srcKey);
		if (!matcher.matches()) {
			System.out.println("Unable to infer image type for key : " + srcKey);
			throw new IllegalArgumentException();
		} else {
			imageType = matcher.group(1);
		}
	}


	@Override
	public void filterOperation() {
		// Infer the scaling factor to avoid stretching the image
		// unnaturally
		float scalingFactor = Math.min(MAX_WIDTH / srcWidth, MAX_HEIGHT / srcHeight);
		int width = (int) (scalingFactor * srcWidth);
		int height = (int) (scalingFactor * srcHeight);

		resizedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = resizedImage.createGraphics();
		// Fill with white before applying semi-transparent (alpha) images
		g.setPaint(Color.white);
		g.fillRect(0, 0, width, height);
		// Simple bilinear resize
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.drawImage(srcImage, 0, 0, width, height, null);
		g.dispose();	
	}
	
	@Override
	public void reencodeImage(BufferedImage dstImage) throws IOException {
		// Re-encode image to target format
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		ImageIO.write(dstImage, JPG_TYPE, os);
		outputData = new ByteArrayInputStream(os.toByteArray());
		// set content-length and content type
		meta = new ObjectMetadata();
		meta.setContentLength(os.size());
		meta.setContentType(JPG_MIME);
	}
	
	@Override
	public void uploadImageToS3() {
		// UPLOADING TO S3 DESTINATION BUCKET
		System.out.println("Writing to: " + DST_BUCKET_THUMBNAILS + "/" + dstKey);
		s3.putObject(DST_BUCKET_THUMBNAILS, dstKey, outputData, meta);
		System.out.println(
				"Successfully filtered " + srcBucket + "/" + srcKey + " and uploaded to " + DST_BUCKET_THUMBNAILS + "/" + dstKey);
	}
	
}