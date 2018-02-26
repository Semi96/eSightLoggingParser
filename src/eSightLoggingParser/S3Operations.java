package eSightLoggingParser;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.ContainerCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.*;

import java.io.File;

import com.amazonaws.AmazonServiceException;

public class S3Operations {
	
	private static String bucketName     = "esight-log-analytics";
    public static final String IDENTITY_POOL_ID = "us-west-2:45a8a5fc-60ef-4fe6-9a47-88ce52e832b6";
    static String access_key_id = "AKIAJE22T7VQG42BQVFA";
    static String secret_key_id = "BQcnDwEKAEj9dzOx7jmIrPUvjY/X957fJQLIQNLk";


	public static void uploadFile(String dirName, String filename) {
		
        BasicAWSCredentials credentials = new BasicAWSCredentials(access_key_id, secret_key_id);
        AmazonS3 s3Client = AmazonS3ClientBuilder.standard()
        						.withCredentials(new AWSStaticCredentialsProvider(credentials))
        						.build();
	        try {
	        	
	            System.out.println("Uploading a new object to S3 from a file:  " + filename);
	            File f = new File(dirName + File.separator + filename);
	            System.out.println("NAME:  " + f.getName());

	            PutObjectRequest putRequest = new PutObjectRequest(bucketName, filename, f);
	            s3Client.putObject(putRequest);
//			    s3.putObject("esight-log-analytics", filename, System.getProperty("user.dir") + File.separator + filename);

	         } catch (AmazonServiceException ase) {
	            System.out.println("Caught an AmazonServiceException, which " +
	            		"means your request made it " +
	                    "to Amazon S3, but was rejected with an error response" +
	                    " for some reason.");
	            System.out.println("Error Message:    " + ase.getMessage());
	            System.out.println("HTTP Status Code: " + ase.getStatusCode());
	            System.out.println("AWS Error Code:   " + ase.getErrorCode());
	            System.out.println("Error Type:       " + ase.getErrorType());
	            System.out.println("Request ID:       " + ase.getRequestId());
	        } catch (AmazonClientException ace) {
	            System.out.println("Caught an AmazonClientException, which " +
	            		"means the client encountered " +
	                    "an internal error while trying to " +
	                    "communicate with S3, " +
	                    "such as not being able to access the network.");
	            System.out.println("Error Message: " + ace.getMessage());
	        }
	    }
		
	}


