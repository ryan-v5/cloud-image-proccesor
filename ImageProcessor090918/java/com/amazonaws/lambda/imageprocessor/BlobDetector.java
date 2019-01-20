package com.amazonaws.lambda.imageprocessor;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.Raster;
import java.util.ArrayList;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;

public class BlobDetector extends Filter implements RequestHandler<S3Event, String> {
	
	private static BufferedImage blobImage;

	// private static AmazonS3 s3 = AmazonS3ClientBuilder.standard().build();

	// test purpose constructor thing

	public BlobDetector() {
	}

	@Override
	public String handleRequest(S3Event event, Context context) {
		context.getLogger().log("Received event: " + event);

		try {
			super.setBucketsAndKeys(event);

			super.sanityCheckBuckets();

			if (super.moveJsonFile()) {
				System.out.println("File was json. Has been moved. Finsihing function");
				return "";
			}

			setImageType();

			super.checkIfImageTypeIsValid();

			super.downloadImageToInputStream();

			super.readSourceImage();

			super.setImageHeightAndWidth();

			filterOperation();
			
			super.reencodeImage(blobImage);
			
			super.uploadImageToS3();

			return "OK";

		} catch (Exception e) {
			e.printStackTrace();
			context.getLogger().log(String.format("Error getting object %s from bucket %s. Make sure they exist and"
					+ " your bucket is in the same region as this function.", srcKey, srcBucket));
			return "Error";
		}
	}

	public static void getParamsFromFileName() {
		// Get the filter parameters and the image type from the file name
		String[] params = srcKey.split("_");
		imageType = params[1];
	}

	@Override
	public void setFilterParams() {
		// no filter parameters necessary for blob detector
	}

	@Override
	public void setImageType() {
		// Get the filter parameters and the image type from the file name
		params = srcKey.split("_");
		// test - print out params
		for (int i = 0; i < params.length; i++) {
			System.out.println(i + " : at this element : " + params[i]);
		}
		imageType = params[1];
	}

	@Override
	public void filterOperation() {
		// get raw image data
		Raster raster = srcImage.getData();
		DataBuffer buffer = raster.getDataBuffer();

		int type = buffer.getDataType();

		if (type != DataBuffer.TYPE_BYTE) {
			System.err.println("Wrong image data type");
			BufferedImage newImage = new BufferedImage(srcWidth, srcHeight, BufferedImage.TYPE_3BYTE_BGR);
			Graphics2D graphics = newImage.createGraphics();
			graphics.drawImage(srcImage, 0, 0, null);
			raster = newImage.getData();
			buffer = raster.getDataBuffer();
			// System.exit(1);
		}

		if (buffer.getNumBanks() != 1) {
			System.err.println("Wrong image data format");
			System.exit(1);
		}

		DataBufferByte byteBuffer = (DataBufferByte) buffer;
		byte[] srcData = byteBuffer.getData(0);

		// Sanity check image
		if (srcWidth * srcHeight * 3 != srcData.length) {
			System.err.println("Unexpected image data size. Should be RGB image");
			System.exit(1);
		}

		// Output Image info
		System.out.printf("Loaded image: '%s', width: %d, height: %d, num bytes: %d\n", srcKey, srcWidth, srcHeight,
				srcData.length);

		// Create Monochrome version - using basic threshold technique
		byte[] monoData = new byte[srcWidth * srcHeight];
		int srcPtr = 0;
		int monoPtr = 0;

		while (srcPtr < srcData.length) {
			int val = ((srcData[srcPtr] & 0xFF) + (srcData[srcPtr + 1] & 0xFF) + (srcData[srcPtr + 2] & 0xFF)) / 3;
			monoData[monoPtr] = (val > 128) ? (byte) 0xFF : 0;

			srcPtr += 3;
			monoPtr += 1;
		}

		byte[] dstData = new byte[srcData.length];

		// Create Blob Finder
		BlobFinder finder = new BlobFinder(srcWidth, srcHeight);

		ArrayList<BlobFinder.Blob> blobList = new ArrayList<BlobFinder.Blob>();
		finder.detectBlobs(monoData, dstData, 0, -1, (byte) 0, blobList);

		// List Blobs
		System.out.printf("Found %d blobs:\n", blobList.size());
		for (BlobFinder.Blob blob : blobList)
			System.out.println(blob);

		RGBFrame dstFrame = new RGBFrame(srcWidth, srcHeight, dstData);

		// create the blob image
		blobImage = dstFrame.getImage();
		
	}
}