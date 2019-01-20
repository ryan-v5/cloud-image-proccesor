package com.amazonaws.lambda.imageprocessor;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.util.ArrayList;
import javax.imageio.ImageIO;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;

/**
 * This class creates multiple rectangular subimages based on a larger image.
 * 
 * @author ryanorr
 */
public class GridImage implements RequestHandler<S3Event, String> {
	private static final long SMALL_IMAGE_SIZE = 2000000;
	private static final long MEDIUM_IMAGE_SIZE = 4000000;

	private static Image image;
	private static BufferedImage buffImage;
	private static S3Object imageFile;
	private static int rows, columns;
	private static BufferedImage[][] smallImages;
	private static int smallWidth;
	private static int smallHeight;
	private static ArrayList<String> subImageKeys = new ArrayList<String>();
	private static AmazonS3 s3 = AmazonS3ClientBuilder.standard().build();

	private static String dstBucket;
	private static String dstKey;
	private static String srcKey;
	private static String srcBucket;
	private static String[] filterParams;
	private static long imageSize;
	private static String imageType;

	public GridImage() {
	}

	// test purpose only
	public GridImage(AmazonS3 s3) {
		GridImage.s3 = s3;
	}

	/**
	 * 
	 */
	@Override
	public String handleRequest(S3Event event, Context context) {
		// output to logs the event that has been received
		context.getLogger().log("Received event: " + event + "\n");

		try {
			// Get the source bucket and key from the event object
			srcBucket = event.getRecords().get(0).getS3().getBucket().getName();
			// Object key may have spaces or unicode non-ASCII characters.
			srcKey = event.getRecords().get(0).getS3().getObject().getKey().replace('+', ' ');
			srcKey = URLDecoder.decode(srcKey, "UTF-8");

			// get the s3 object which will be a JSON file
			S3Object jsonFile = s3.getObject(new GetObjectRequest(srcBucket, srcKey));

			// get the JSON file from the S3 Bucket
			// use JSON file to instantiate an instance of class Image
			try {
				image = parseJsonFileToImageObject(jsonFile);
			} catch (IllegalArgumentException e) {
				System.err.println("Image object is null.");
				return "";
			}

			// create values for destination bucket and destination key
			// destination key will be the same for JSON file, phrasing this way for
			// readability
			dstKey = srcKey;
			// destination bucket is the filter specified in JSON file
			dstBucket = image.getFilterId();
			// set filter params to be added to the filename
			filterParams = image.getFilterParams();

			// Sanity check - validate that source and destination are different buckets
			if (srcBucket.equals(dstBucket)) {
				System.out.println("Destination bucket must not match source bucket\n");
				return "";
			}

			try {
				image.inferAndSetImageType();
				System.out.println("Image type is : " + image.getImageType());
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
				return "";
			}

			if (!image.checkIfImageTypeIsValid()) {
				System.out.println("Image must be in the JPEG, PNG or TIFF format. Skipping file : " + srcKey);
				return "";
			}

			// store the imageType in a local variable
			imageType = image.getImageType();

			// get the image file
			imageFile = s3.getObject(new GetObjectRequest(image.getSrcBucket(), image.getImageKey()));

			// get the size of the image in bytes
			getImageSize();
			System.out.println("Image size is " + imageSize);

			// check if image is too small to split, copy it to filter bucket if so
			if (!imageRequiresSplitting()) {
				System.out.println("Image is too small to split. Copying image to filter bucket...");
				copyImageNoSplit();
				serializeAndPutJson();
				System.out.println("Image and json file copied to : " + image.getFilterId());
				return "";
			}

			// check if image has been set to use the blob or edge detection algorithms, if
			// so do not split
			// may also be caught above if the image is too small for splitting, result is
			// the same
			if (willUseNoSplitAlgorithm()) {
				System.out.println("Image is using a no-split algorithm. Copying to filter bucket...");
				copyImageNoSplit();
				serializeAndPutJson();
				System.out.println("Image and json file copied to : " + image.getFilterId());
				return "";
			}

			// image does need to be split - set number of sub-images
			setNumberOfSubImages();

			// set dimensions for sub-images
			setDimensionsForSubImages();

			// run method to split images and save sub-images to S3 bucket
			subDivideAndSaveImages();

			// add the values for the sub-image keys to the Image object
			image.setSubImages(subImageKeys);

			// test - print out image class instance
			System.out.println("Data in the image object: " + image.toString());

			// serialize and put json into the filter bucket
			serializeAndPutJson();
		} catch (SdkClientException | IOException e) {
			e.printStackTrace();
		}

		// clean up
		subImageKeys.clear();
		return dstKey + " successfully saved to " + dstBucket;
	}

	/**
	 * 
	 * @param jsonFile
	 * @return
	 */
	public Image parseJsonFileToImageObject(S3Object jsonFile) throws IllegalArgumentException {
		// get the JSON file from the S3 Bucket
		// use JSON file to instantiate an instance of class Image
		try {
			Image image = JsonOperations.jsonFileToImageObject(jsonFile);
			jsonFile.close();
			if (image == null) {
				throw new IllegalArgumentException();
			} else {
				return image;
			}
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	public static void getImageSize() {
		ObjectMetadata meta = imageFile.getObjectMetadata();
		imageSize = meta.getContentLength();

	}

	public static boolean imageRequiresSplitting() {
		if (imageSize < SMALL_IMAGE_SIZE) {
			return false;
		} else {
			return true;
		}
	}

	/**
	 * Checks if the image to be filtered has been specified to use the blob
	 * detector or edge detection algorithms. Both of these algorithms require the
	 * image to be full and not split.
	 *
	 * @return true if the image is not to be split, false if it is
	 */
	public static boolean willUseNoSplitAlgorithm() {
		if (image.getFilterId().equals(S3Buckets.BLOB_DETECTOR)
				|| image.getFilterId().equals(S3Buckets.EDGE_DETECTOR)) {
			return true;
		} else {
			return false;
		}
	}

	public static void copyImageNoSplit() {
		String noSplitDstKey = createFileNameWithParams();
		// create an array list with one entry for the image file's current file name
		ArrayList<String> currentKeyName = new ArrayList<>();
		// add the single key name to subimages, it will be accessed later during the
		// merge function
		currentKeyName.add(noSplitDstKey);
		image.setSubImages(currentKeyName);
		s3.copyObject(image.getSrcBucket(), image.getImageKey(), dstBucket, noSplitDstKey);
		System.out.println("Image file copied to " + dstBucket + "/" + noSplitDstKey);
	}

	public static void serializeAndPutJson() throws AmazonServiceException, SdkClientException, IOException {
		// save JSON representation of Image object to destination bucket using the
		// imageObjectToJsonFile method from class JsonOperations
		s3.putObject(dstBucket, dstKey, JsonOperations.imageObjectToJsonFile(image));
		System.out.println("Json file copied to " + dstBucket + "/" + dstKey);
	}

	/**
	 * Method that sets the number of subimages by deciding the number of rows and
	 * columns the image will be split along.
	 */
	public static void setNumberOfSubImages() {
		// TODO - work out optimum amount of splitting based on image size
		// decide how many rows and columns in should be divided into based on size
		if (imageSize < MEDIUM_IMAGE_SIZE) {
			rows = 2;
			columns = 2;
		} else {
			rows = 2;
			columns = 4;
		}
	}

	/**
	 * Method that sets the dimensions for the sub-images that will be created based
	 * on no. of rows and columns.
	 * 
	 * @param rows
	 * @param columns
	 */
	public static void setDimensionsForSubImages() throws IOException {
		// create a buffered image object
		InputStream objectData = imageFile.getObjectContent();
		buffImage = ImageIO.read(objectData);
		objectData.close();

		// work out height and width of the sub-images
		smallWidth = buffImage.getWidth() / columns;
		smallHeight = buffImage.getHeight() / rows;

	}

	public static String createFileNameWithParams() {
		// set destination key to contain parameters for the filter and the image type
		String fileName = dstKey.replace(".json", "");
		// remove all underscores that will be used as part of the split function by the
		// filter
		// to get the parameters
		fileName = fileName.replaceAll("_", "-");
		// loop through the array of params and assign them to fileName
		if (filterParams == null) {
			System.out.println("No params needing to be passed.");
			fileName += ("_" + imageType);
			return fileName;
		}
		for (int loop = 0; loop < filterParams.length; loop++) {
			fileName += ("_" + filterParams[loop]);
		}
		fileName += ("_" + imageType);
		return fileName;
	}

	public static String createFileNameWithParams(int count) {
		// set destination key to contain parameters for the filter and the image type
		String fileName = dstKey.replace(".json", "") + "-" + count;
		// remove all underscores that will be used as part of the split function by the
		// filter
		// to get the parameters
		fileName = fileName.replaceAll("_", "-");
		// loop through the array of params and assign them to fileName
		if (filterParams == null) {
			System.out.println("No params needing to be passed.");
			fileName += ("_" + imageType);
			return fileName;
		}
		for (int loop = 0; loop < filterParams.length; loop++) {
			fileName += ("_" + filterParams[loop]);
		}
		fileName += ("_" + imageType);
		return fileName;
	}

	/**
	 * Splits the image based on no. of rows and columns and writes the sub-images
	 * to an S3 bucket. Reference -
	 * https://codereview.stackexchange.com/questions/20529/slicing-up-an-image-into-rows-and-columns-in-java#comment32905_20536
	 * 
	 * @param buffImage
	 */
	public static void subDivideAndSaveImages() {

		String dstKeySubImage = "";
		int count = 0;

		// create 2D array of type BufferedImage
		smallImages = new BufferedImage[columns][rows];

		// loop across number of columns(width x)
		for (int xloop = 0; xloop < columns; xloop++) {
			// loop across number of rows(height y)
			for (int yloop = 0; yloop < rows; yloop++) {
				// assign a sub image to smallImages 2D array, for example, first iteration is
				// at 0,0 in array
				smallImages[xloop][yloop] = buffImage.getSubimage(xloop * smallWidth, yloop * smallHeight, smallWidth,
						smallHeight);

				// write the sub-image to an S3 bucket
				try {
					// write the sub-images to an input stream
					ByteArrayOutputStream os = new ByteArrayOutputStream();
					ImageIO.write(smallImages[xloop][yloop], imageType, os);
					InputStream is = new ByteArrayInputStream(os.toByteArray());
					// set meta-data; content-length and content type
					ObjectMetadata meta = new ObjectMetadata();
					meta.setContentLength(os.size());
					meta.setContentType(image.getContentTypeForMetaData());
					// iterate the count for labelling of the sub-images
					count++;
					dstKeySubImage = createFileNameWithParams(count);

					System.out.println("Writing to: " + dstBucket + "/" + dstKeySubImage);
					s3.putObject(dstBucket, dstKeySubImage, is, meta);
					System.out.println(
							"Successfully divided " + srcBucket + "/" + srcKey.replace(".json", "." + imageType)
									+ " and uploaded to " + dstBucket + "/" + dstKeySubImage);
					// add the name of each sub-image to an ArrayList
					subImageKeys.add(dstKeySubImage);
					// System.out.println("The subImageKeys array list at this point : "
					// +subImageKeys);
					// System.out.println("The loop count : " +count);

				} catch (IOException e) {
					System.err.println("Problem writing sub-images to S3 Bucket.");
					e.printStackTrace();
				}
			}
		}
	}
}
