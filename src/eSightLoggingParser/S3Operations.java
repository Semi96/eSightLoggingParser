package eSightLoggingParser;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.transfer.MultipleFileDownload;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import java.io.File;

public class S3Operations {

	private static String bucketName = "esight-log-analytics";

	public static void downloadLogs() {

		File f = new File(System.getProperty("user.dir") + File.separator + "Log_Files");
		if (f.exists()) {
			System.out.println("\"Log_Files\" directory already exists");
		}
		else {
			System.out.println("downloading log files...");
			TransferManager tm = TransferManagerBuilder.standard().build();

			try {
				MultipleFileDownload mfd = tm.downloadDirectory("esight-log-objects", "Log_Files", new File(System.getProperty("user.dir")));
				while (!mfd.isDone()) {
					// wait
				}
				System.out.println("download complete!");
			} catch(AmazonServiceException e) {
				e.getErrorMessage();
				e.printStackTrace();
			}
			tm.shutdownNow();
		}

	}
	
	public static void uploadFile(String dirName, String filename) {

		try {
			AmazonS3 s3Client = AmazonS3ClientBuilder.standard().withRegion(Regions.US_WEST_2).build();

			File f = new File(dirName + File.separator + filename);

			PutObjectRequest putRequest = new PutObjectRequest(bucketName, filename, f);
			s3Client.putObject(putRequest);
			System.out.println("Successfully uploaded file " + f.getName());

		} catch (AmazonServiceException ase) {
			System.out.println("Caught an AmazonServiceException, which " + "means your request made it "
					+ "to Amazon S3, but was rejected with an error response" + " for some reason.");
			System.out.println("Error Message:    " + ase.getMessage());
			System.out.println("HTTP Status Code: " + ase.getStatusCode());
			System.out.println("AWS Error Code:   " + ase.getErrorCode());
			System.out.println("Error Type:       " + ase.getErrorType());
			System.out.println("Request ID:       " + ase.getRequestId());
		} catch (AmazonClientException ace) {
			System.out.println("Caught an AmazonClientException, which " + "means the client encountered "
					+ "an internal error while trying to " + "communicate with S3, "
					+ "such as not being able to access the network.");
			System.out.println("Error Message: " + ace.getMessage());
		}
	}

}
