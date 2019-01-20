/**
 * 
 */
package com.amazonaws.lambda.imageprocessor;

/**
 * @author ryanorr
 * Student No: 40025263
 *
 */
public class S3Buckets {

	// repositories for images and json
	/**
	 * Bucket that all images initially are uploaded to.
	 */
	static final String IMG_REPO = "imgs-40025263";

	/**
	 * Bucket that all json files initially are uploaded to.
	 */
	static final String JSON_REPO = "imgs-40025263-json";

	/**
	 * Bucket that the images are sent to when processing has finished
	 */
	static final String IMG_DONE = "imgs-40025263-done";

	/**
	 * Bucket images will be sent to when filters functions have finished. This
	 * bucket will then trigger the merge function which will merge sub-images if
	 * necessary.
	 */
	static final String IMG_FILTERED = "imgs-40025263-filtered";
	
	/**
	 * Bucket that all the thumbnails created will be saved in.
	 */
	static final String IMG_THUMBNAILS = "imgs-40025263-thumbnails";

	/**
	 * pre-filter buckets, these are the buckets that will trigger the specified
	 * filter when image files arrive in them
	 */
	
	static final String MEDIAN_FILTER = "imgs-40025263-medianfilter";
	static final String MEDIAN_FILTER_ZERO_FILL = "imgs-40025263-medianfilterzerofill";
	static final String MEDIAN_FILTER_VALUE_FILL = "imgs-40025263-medianfiltervaluefill";
	static final String SPATIAL_FILTER = "imgs-40025263-spatialfilter";
	static final String EDGE_DETECTOR = "imgs-40025263-edgedetector";
	static final String BLOB_DETECTOR = "imgs-40025263-blobdetector";

	/**
	 * The prefixes that will be added to the filenames of images depending on the
	 * filter used.
	 */
	
	static final String MEDIAN_FILTER_PREFIX = "medianfilter-";
	static final String MEDIAN_FILTER_ZERO_FILL_PREFIX = "medianfilterzerofill-";
	static final String MEDIAN_FILTER_VALUE_FILL_PREFIX = "medianfiltervaluefill-";
	static final String SPATIAL_FILTER_PREFIX = "spatialfilter-";
	static final String EDGE_DETECTOR_PREFIX = "edgedetector-";
	static final String BLOB_DETECTOR_PREFIX = "blobdetector-";
	
	/**
	 * Method that returns the appropriate prefix to be used with the image filename when 
	 * the name of the filter bucket used during the file's processing is passed.
	 * @param bucketName the name of the filter bucket used during processing
	 * @return the prefix to be added to the image file name
	 */
	public static String getPrefixForFileName(String bucketName) {
		if (bucketName.equals(MEDIAN_FILTER)) {
			return MEDIAN_FILTER_PREFIX;
		} else if (bucketName.equals(MEDIAN_FILTER_ZERO_FILL)) {
			return MEDIAN_FILTER_ZERO_FILL_PREFIX;
		} else if (bucketName.equals(MEDIAN_FILTER_VALUE_FILL)) {
			return MEDIAN_FILTER_VALUE_FILL_PREFIX;
		} else if (bucketName.equals(SPATIAL_FILTER)) {
			return SPATIAL_FILTER_PREFIX;
		} else if (bucketName.equals(EDGE_DETECTOR)) {
			return EDGE_DETECTOR_PREFIX;
		} else if (bucketName.equals(BLOB_DETECTOR)) {
			return BLOB_DETECTOR_PREFIX;
		} else {
			return "";
		}
	}
}
