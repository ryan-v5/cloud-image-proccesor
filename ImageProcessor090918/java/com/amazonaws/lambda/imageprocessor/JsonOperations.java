package com.amazonaws.lambda.imageprocessor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;

import com.amazonaws.services.s3.model.S3Object;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Contains methods for getting a Java object of type Image from a JSON file and 
 * for creating a JSON file from an object of type Image.
 * @author ryanorr
 *
 */
public class JsonOperations {

	/**
	 * Reads the JSON file that has been created in an S3 Bucket and converts it
	 * into an object of type Image and returns it.
	 * The JSON file can be accessed from the S3Object that is retrieved from the S3Event
	 * using the bucket and key name.
	 * Note: Should only be used when the Lambda Function is set to trigger when a file
	 * with the '.json' extension is created in an S3 Bucket.
	 * @param s3Object - the S3Object that triggered the function
	 * @return - returns an object of type Image
	 * @throws IOException
	 */
	public static Image jsonFileToImageObject(S3Object s3Object) throws IOException {
		Gson gson = new Gson();
		InputStream input = s3Object.getObjectContent();
		BufferedReader buff = new BufferedReader(new InputStreamReader(input));
		Image image = gson.fromJson(buff, Image.class);
		buff.close();
		input.close();
		return image;
	}
	
	/**
	 * Creates a JSON file from the Image object and returns it.
	 * @param image - An Image object
	 * @return - returns a File object that can be saved to an S3 Bucket.
	 * @throws IOException
	 */
	public static File imageObjectToJsonFile(Image image) throws IOException {
		// create GSON builder
		GsonBuilder builder = new GsonBuilder();
		builder.serializeNulls();
		Gson gson = builder.create();
		
        File file = File.createTempFile("aws-","");
        file.deleteOnExit();
        Writer writer = new OutputStreamWriter(new FileOutputStream(file));
        writer.write(gson.toJson(image));
        writer.close();
        return file;
	}	

}
