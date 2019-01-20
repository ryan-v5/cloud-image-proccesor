/**
 * 
 */
package com.amazonaws.lambda.imageprocessor;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author ryanorr
 *
 */
public class Image {

	/**
	 * Allowed image types
	 * Declared static so will not appear in JSON serialisation.
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
	 * instance variables
	 */
	private String imageKey;
	private String srcBucket;
	private String filterId;
	private String[] filterParams;
	private ArrayList<String> subImages;
	private String originalFileName;
	private String imageType;

	/**
	 * default constructor
	 */
	public Image() {
	}

	/**
	 * constructor with arguments
	 * 
	 * @param imageKey
	 * @param filter
	 * @param filterParam
	 */
	public Image(String imageKey, String srcBucket, String filterId, String[] filterParams, ArrayList<String> subImages,
			String originalFileName) {
		this.imageKey = imageKey;
		this.srcBucket = srcBucket;
		this.filterId = filterId;
		this.filterParams = filterParams;
		this.subImages = subImages;
		this.originalFileName = originalFileName;
	}

	/**
	 * @return the imgKey
	 */
	public String getImageKey() {
		return imageKey;
	}

	/**
	 * @param imgKey
	 *            the imgKey to set
	 */
	public void setImageKey(String imgKey) {
		this.imageKey = imgKey;
	}

	/**
	 * @return the srcBucket
	 */
	public String getSrcBucket() {
		return srcBucket;
	}

	/**
	 * @param srcBucket
	 *            the srcBucket to set
	 */
	public void setSrcBucket(String srcBucket) {
		this.srcBucket = srcBucket;
	}

	/**
	 * @return the filterId
	 */
	public String getFilterId() {
		return filterId;
	}

	/**
	 * @param filterId
	 *            the filterId to set
	 */
	public void setFilterId(String filterId) {
		this.filterId = filterId;
	}

	/**
	 * @return the filterParam
	 */
	public String[] getFilterParams() {
		return filterParams;
	}

	/**
	 * @param filterParam
	 *            the filterParam to set
	 */
	public void setFilterParams(String[] filterParams) {
		this.filterParams = filterParams;
	}

	/**
	 * @return the subImages
	 */
	public ArrayList<String> getSubImages() {
		return subImages;
	}

	/**
	 * @param subImages
	 *            the subImages to set
	 */
	public void setSubImages(ArrayList<String> subImages) {
		this.subImages = subImages;
	}

	/**
	 * @return the originalFileName
	 */
	public String getOriginalFileName() {
		return originalFileName;
	}

	/**
	 * @param originalFileName
	 *            the originalFileName to set
	 */
	public void setOriginalFileName(String originalFileName) {
		this.originalFileName = originalFileName;
	}

	/**
	 * @return the imageType
	 */
	public String getImageType() {
		return imageType;
	}

	/**
	 * @param imageType
	 *            the imageType to set
	 */
	public void setImageType(String imageType) {
		this.imageType = imageType;
	}

	@Override
	public String toString() {
		return "[imgKey=" + imageKey + "/srcBucket=" + srcBucket + "/filterId=" + filterId + "/filterParams="
				+ filterParams + "/subimages=" + subImages + "]";
	}

	/**
	 * Method that finds and sets the image type based on the image's key name.
	 * Finds the suffix of the key name and infers the image type from it.
	 * 
	 * REF NEEDED
	 * 
	 * @return a String containing the image type (eg. jpg, png, tiff)
	 * @throws IllegalArgumentException
	 */
	public void inferAndSetImageType() throws IllegalArgumentException {
		Matcher matcher = Pattern.compile(".*\\.([^\\.]*)").matcher(imageKey);
		if (!matcher.matches()) {
			System.out.println("Unable to infer image type for key : " + imageKey);
			throw new IllegalArgumentException();
		} else {
			this.imageType = matcher.group(1);
		}
	}

	/**
	 * Method that checks if the image file type is one of the types allowed by the
	 * program.
	 * 
	 * @return returns true if the image type is allowed and false if not.
	 */
	public boolean checkIfImageTypeIsValid() {
		if (!(JPG_TYPE.equalsIgnoreCase(imageType)) && !(PNG_TYPE.equalsIgnoreCase(imageType))
				&& !(TIFF_TYPE.equalsIgnoreCase(imageType)) && !(TIF_TYPE.equalsIgnoreCase(imageType))
				&& !(JPEG_TYPE.equalsIgnoreCase(imageType))) {
			return false;
		} else {
			return true;
		}
	}

	/**
	 * Method that returns the content type of the image object
	 * to be used as part of the resulting file's meta data on Amazon S3. 
	 * The content type will take the form of a String, for example, expressed as
	 * "image/jpg" for a JPG image.
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

}
