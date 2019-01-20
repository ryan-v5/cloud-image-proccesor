/**
 * 
 */
package com.amazonaws.lambda.imageprocessor;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;

import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;

/**
 * The superclass of all Filter Lambda Functions. Contains commonly used
 * variables and methods for all the filters. It is intended that many of these
 * operations will be overridden depending on the needs of the specific filter.
 * Also take note that not all filters will have need to use all the variables and 
 * methods specified in this class.
 * @author ryanorr
 *
 */
public abstract class Filter {

	/**
	 * Amazon S3 Client
	 */
	private AmazonS3 s3 = AmazonS3ClientBuilder.standard().build();

	/**
	 * Allowed image types
	 */
	private static final String JPG_TYPE = (String) "jpg";
	private static final String JPEG_TYPE = (String) "jpeg";
	private static final String JPG_MIME = (String) "image/jpeg";
	private static final String PNG_TYPE = (String) "png";
	private static final String PNG_MIME = (String) "image/png";
	private static final String TIFF_TYPE = (String) "tiff";
	private static final String TIFF_MIME = (String) "image/tiff";
	private static final String TIF_TYPE = (String) "tif";
	private static final String TIF_MIME = (String) "image/tif";

	/**
	 * All filters have the same destination bucket.
	 */
	protected static final String DST_BUCKET = S3Buckets.IMG_FILTERED;
	protected static String srcKey;
	protected static String srcBucket;
	protected static String dstKey;
	protected static String[] params;

	protected static String imageType;
	protected static InputStream objectData;
	protected static BufferedImage srcImage;
	protected static int srcHeight;
	protected static int srcWidth;
	protected static int outputPixels[];
	protected static InputStream outputData;
	protected static ObjectMetadata meta;

	/**
	 * Default Constructor
	 */
	public Filter() {
	}

	public void setBucketsAndKeys(S3Event event) {
		// Get the object from the event and show its content type
		srcBucket = event.getRecords().get(0).getS3().getBucket().getName();
		srcKey = event.getRecords().get(0).getS3().getObject().getKey();
		dstKey = srcKey;
	}

	public void sanityCheckBuckets() {
		// Sanity check - validate that source and destination are different buckets
		if (srcBucket.equals(DST_BUCKET)) {
			System.err.println("Destination bucket must not match source bucket");
			System.exit(0);
		}
	}

	public boolean moveJsonFile() {
		// if it is the json file, move it to the next bucket
		if (srcKey.endsWith(".json")) {
			s3.copyObject(srcBucket, srcKey, DST_BUCKET, dstKey);
			System.out.println("Moving JSON file to next bucket...");
			return true;
		} else {
			return false;
		}
	}

	/**
	 * 
	 * @param srcKey
	 * @return
	 */
	public void setFilterParams() {
	}

	/**
	 * 
	 * @return
	 */
	public abstract void setImageType();

	public String getImageType() {
		return imageType;
	}

	public void checkIfImageTypeIsValid() {
		if (!(JPG_TYPE.equalsIgnoreCase(imageType)) && !(PNG_TYPE.equalsIgnoreCase(imageType))
				&& !(TIFF_TYPE.equalsIgnoreCase(imageType)) && !(TIF_TYPE.equalsIgnoreCase(imageType))
				&& !(JPEG_TYPE.equalsIgnoreCase(imageType))) {
			System.err.println("Image must be in JPG, PNG or TIFF format. Skipping file...");
			System.exit(0);
		}
	}

	public void downloadImageToInputStream() {
		// DOWNLOAD THE IMAGE FROM S3 INTO A STREAM **IMPORTANT**
		S3Object s3Object = s3.getObject(new GetObjectRequest(srcBucket, srcKey));
		objectData = s3Object.getObjectContent();
	}

	public void readSourceImage() throws IOException {
		srcImage = ImageIO.read(objectData);
	}

	public void setImageHeightAndWidth() {
		// get the height and width of image
		srcHeight = srcImage.getHeight();
		srcWidth = srcImage.getWidth();
	}

	public void setOutputPixels() {
		// calculate total pixels
		int totalPixels = srcWidth * srcHeight;
		outputPixels = new int[totalPixels];
	}

	/**
	 * Method that returns the content type of the image object to be used as part
	 * of the resulting file's meta data on Amazon S3. The content type will take
	 * the form of a String, for example, expressed as "image/jpg" for a JPG image.
	 * 
	 * @return
	 * @throws IllegalArgumentException
	 */
	public String getContentTypeForMetaData() throws IllegalArgumentException {
		// set content type
		if (JPG_TYPE.equalsIgnoreCase(imageType) || (JPEG_TYPE.equalsIgnoreCase(imageType))) {
			return JPG_MIME;
		} else if (PNG_TYPE.equalsIgnoreCase(imageType)) {
			return PNG_MIME;
		} else if (TIFF_TYPE.equalsIgnoreCase(imageType)) {
			return TIFF_MIME;
		} else if (TIF_TYPE.equalsIgnoreCase(imageType)) {
			return TIF_MIME;
		} else {
			throw new IllegalArgumentException();
		}
	}

	public void reencodeImage(BufferedImage dstImage) throws IOException {
		// Re-encode image to target format
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		ImageIO.write(dstImage, imageType, os);
		outputData = new ByteArrayInputStream(os.toByteArray());
		// set content-length and content type
		meta = new ObjectMetadata();
		meta.setContentLength(os.size());
		meta.setContentType(getContentTypeForMetaData());
	}

	public void uploadImageToS3() {
		// UPLOADING TO S3 DESTINATION BUCKET
		System.out.println("Writing to: " + DST_BUCKET + "/" + dstKey);
		s3.putObject(DST_BUCKET, dstKey, outputData, meta);
		System.out.println(
				"Successfully filtered " + srcBucket + "/" + srcKey + " and uploaded to " + DST_BUCKET + "/" + dstKey);
	}

	/**
	 * Abstract method that will be implemented differently in every subclass of
	 * filter, due to its specific filtering algorithm.
	 */
	public abstract void filterOperation();
}
