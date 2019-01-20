package com.amazonaws.lambda.imageprocessor;

import java.io.IOException;
import java.util.Arrays;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;

public class MedianFilterValueFill extends Filter implements RequestHandler<S3Event, String> {
	
	//private AmazonS3 s3 = AmazonS3ClientBuilder.standard().build();
	
	private static int maskSize;
	
	@Override
	public String handleRequest(S3Event event, Context context) {
		context.getLogger().log("Received event: " + event);
	
		try {
			super.setBucketsAndKeys(event);

			super.sanityCheckBuckets();
			
			//if it is the json file, move it to the next bucket
			if (super.moveJsonFile()) {
				return "Moved JSON file to next bucket. Function terminated";
			}
			
			setFilterParams();
			
			setImageType();
			
			super.checkIfImageTypeIsValid();
			
			super.downloadImageToInputStream();
			
			super.readSourceImage();
			
			super.setImageHeightAndWidth();
			
			super.setOutputPixels();
			
			filterOperation();
			
			super.reencodeImage(srcImage);
			
			super.uploadImageToS3();
			
			return "OK";
	
		} catch(IOException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}
	
	@Override
	public void setFilterParams() {
		// Get the filter parameters and the image type from the file name
		params = srcKey.split("_");
		// test - print out params
		for (int i = 0; i < params.length; i++) {
			System.out.println(i + " : at this element : " + params[i]);
		}
		 maskSize = Integer.parseInt(params[1]);
	}
	
	@Override
	public void setImageType() {
		imageType = params[2];		
	}

	@Override
	public void filterOperation() {
        /**
         * Buff is a 2D square of odd size like 3x3, 5x5, 7x7, ...
         * For simplicity storing it into 1D array.
         */
        int buff[];
        
        /** Median Filter operation */
        for(int y = 0; y < srcHeight; y++){
            for(int x = 0; x < srcWidth; x++){
                buff = new int[maskSize * maskSize];
                int i = 0;
                for(int r = y - (maskSize / 2); r <= y + (maskSize / 2); r++){
                    for(int c = x - (maskSize / 2); c <= x + (maskSize / 2); c++){
                        if(r < 0 || r >= srcHeight || c < 0 || c >= srcWidth){
                            /** Some portion of the mask is outside the image. */
                            int tr = r, tc = c;
                            if(r < 0){
                                tr = r+1;
                            }else if(r == srcHeight){
                                tr = r-1;
                            }
                            if(c < 0){
                                tc = c+1;
                            }else if(c == srcWidth){
                                tc = c-1;
                            }
                            buff[i] = srcImage.getRGB(tc, tr);
                        }else{
                            buff[i] = srcImage.getRGB(c, r);
                        }
                        i++;
                    }
                }
                Arrays.sort(buff);
                outputPixels[x+y*srcWidth] = buff[(maskSize*maskSize)/2];
            }
        }
        /** Write the output pixels to the image pixels */
        for(int y = 0; y < srcHeight; y++){
            for(int x = 0; x < srcWidth; x++){
                srcImage.setRGB(x, y, outputPixels[x+y*srcWidth]);
            }
        }
    }
		
}


