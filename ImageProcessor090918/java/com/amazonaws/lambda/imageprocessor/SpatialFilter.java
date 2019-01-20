package com.amazonaws.lambda.imageprocessor;

import java.io.IOException;
import java.util.Arrays;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;

public class SpatialFilter extends Filter implements RequestHandler<S3Event, String> {
	
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

	/**
	 * REFERENCE !!!
	 */
	@Override
	public void filterOperation() {
        /**
         * red, green and blue are a 2D square of odd size like 3x3, 5x5, 7x7, ...
         * For simplicity storing it into 1D array.
         */
        int red[], green[], blue[];
        
        /** spatial Filter operation */
        for(int y = 0; y < srcHeight; y++){
            for(int x = 0; x < srcWidth; x++){
                red = new int[maskSize * maskSize];
                green = new int [maskSize * maskSize];
                blue = new int [maskSize * maskSize];
                int count = 0;
                for(int r = y - (maskSize / 2); r <= y + (maskSize / 2); r++){
                    for(int c = x - (maskSize / 2); c <= x + (maskSize / 2); c++){
                        if(r < 0 || r >= srcHeight || c < 0 || c >= srcWidth){
                            /** Some portion of the mask is outside the image. */
                            continue;
                        }else if(x == c && y == r){
                            /** pixel below the center of the mask */
                            continue;
                        }else{
                            red[count] = getRed(c, r);
                            green[count] = getGreen(c, r);
                            blue[count] = getBlue(c, r);
                            count++;
                        }
                    }
                }
                
                /** sort red, green, blue array */
                Arrays.sort(red);
                Arrays.sort(green);
                Arrays.sort(blue);
                
                //RGB value of image pixel
                int pixelRED = getRed(x, y);
                int pixelGREEN = getGreen(x, y);
                int pixelBLUE = getBlue(x, y);
                
                //final RGB value
                int fRED, fGREEN, fBLUE;
                
                //compute final RGB value
                if(pixelRED > red[maskSize*maskSize - 1]){
                    fRED = red[maskSize*maskSize - 1];
                }else if(pixelRED < red[maskSize*maskSize - count]){
                    fRED = red[maskSize*maskSize - count];
                }else{
                    fRED = pixelRED;
                }
                
                if(pixelGREEN > green[maskSize*maskSize - 1]){
                    fGREEN = green[maskSize*maskSize - 1];
                }else if(pixelGREEN < green[maskSize*maskSize - count]){
                    fGREEN = green[maskSize*maskSize - count];
                }else{
                    fGREEN = pixelGREEN;
                }
                
                if(pixelBLUE > blue[maskSize*maskSize - 1]){
                    fBLUE = blue[maskSize*maskSize - 1];
                }else if(pixelBLUE < blue[maskSize*maskSize - count]){
                    fBLUE = blue[maskSize*maskSize - count];
                }else{
                    fBLUE = pixelBLUE;
                }
                
                /** save spatial value in outputPixels array */
                int p = getPixelValueFromARGBValue(255, fRED, fGREEN, fBLUE);
                outputPixels[x+y*srcWidth] = p;
            }
        }
        /** Write the output pixels to the image pixels */
        for(int y = 0; y < srcHeight; y++){
            for(int x = 0; x < srcWidth; x++){
                srcImage.setRGB(x, y, outputPixels[x+y*srcWidth]);
            }
        }
    }
	
    /**
     * This method will return pixel value from the ARGB value.
     * 
     * @param a Alpha value [0-255].
     * @param r Red value [0-255].
     * @param g Green value [0-255].
     * @param b Blue value [0-255].
     * @return Pixel value.
     */
    public static int getPixelValueFromARGBValue(int a, int r, int g, int b){
        return (a<<24) | (r<<16) | (g<<8) | b;
    }
    
    /**
     * This method will return the amount of red value between 0-255 at the pixel (x,y)
     * 
     * @param x the x coordinate of the pixel
     * @param y the y coordinate of the pixel
     * @return the amount of red
     * 
     * 0 means none
     * 255 means fully red
     */
    public int getRed(int x, int y){
        return (outputPixels[x+(y*srcWidth)] >> 16) & 0xFF;
    }
    
    /**
     * This method will return the amount of green value between 0-255 at the pixel (x,y)
     * 
     * @param x the x coordinate of the pixel
     * @param y the y coordinate of the pixel 
     * @return the amount of green
     * 
     * 0 means none
     * 255 means fully green
     */
    public int getGreen(int x, int y){
        return (outputPixels[x+(y*srcWidth)] >> 8) & 0xFF;
    }
    
    /**
     * This method will return the amount of blue value between 0-255 at the pixel (x,y)
     * 
     * @param x the x coordinate of the pixel
     * @param y the y coordinate of the pixel
     * @return the amount of blue
     * 
     * 0 means none
     * 255 means fully blue
     */
    public int getBlue(int x, int y){
        return outputPixels[x+(y*srcWidth)] & 0xFF;
    }
}

