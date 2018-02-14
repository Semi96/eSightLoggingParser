package eSightLoggingParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

public class Parser {
	
	static File logFile;
//	static String logPath = "C:" + File.separator + "Users" + File.separator + "Semi" + File.separator + "Downloads";
	static String logPath = "C:" + File.separator + "Users" + File.separator + "Semi" + File.separator + "eSightLogFiles";
	static String logFileName = "600058.txt";
	static File log;
	static String logFullPath = logPath + File.separator + logFileName;
	static FileInputStream fis;
	static String text;
	static HashMap<String, Integer> eventMapFOLDER = new HashMap<String, Integer>();
	static HashMap<String, Integer> menuMapFOLDER = new HashMap<String, Integer>();
	static HashMap<String, Integer> eventMapFILE = new HashMap<String, Integer>();
	static HashMap<String, Integer> menuMapFILE = new HashMap<String, Integer>();
	// stores individual statistics for each user
	// it holds the key (device serial number ideally), and a collection of the menu & default stats, in form of a hashmap
	static HashMap< String, HashSet<HashMap<String, Integer>> > userDB = new HashMap<String, HashSet<HashMap<String, Integer>>>(); 
	static int menuCounterFolder;
	static int eventCounterFolder;

	
	public Parser() {
		openFolder(logPath); // hardcoded for ease of use for now
	}
	
	public Parser(String logFilesDirectory) {
		openFolder(logFilesDirectory);
	}
		
	public static void openFolder(String folderPathName) {
		try {
			// open directory and get each file inside
		  File dir = new File(folderPathName);
		  File[] directoryListing = dir.listFiles();
		  if (directoryListing != null) {
		    for (File child : directoryListing) {
		      // Do something with child
		    	try {
		    		String logFileName = child.getName();
		    		logFileName = logFileName.substring(0, logFileName.lastIndexOf(".")); // get the name without the file extension i.e .txt

					text = readFile(child);
					parseText(text);
							
					// sort the FILE maps based on their value; this way when exported, our table is already sorted by frequency
					eventMapFILE = (HashMap<String, Integer>) sortByValue(eventMapFILE);
					menuMapFILE = (HashMap<String, Integer>) sortByValue(menuMapFILE);

					System.out.println("menuMap FILE: " + menuMapFILE.toString());
					System.out.println("defaultMap FILE: " + eventMapFILE.toString());
					
					HashSet<HashMap<String, Integer>> c = new HashSet<HashMap<String, Integer>>();
					c.add(menuMapFILE);
					c.add(eventMapFILE);
					userDB.put(logFileName, c); // accumulate a hashmap for each individual user statistics
					
					// quick & dirty way to reset the FILE maps so they contain no previous file's stats
					eventMapFILE = new HashMap<String, Integer>();
					menuMapFILE = new HashMap<String, Integer>();
										
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
		    }
		    
		    // finally, sort the FOLDER maps add make a CSV file out of them
		    eventMapFOLDER = (HashMap<String, Integer>) sortByValue(eventMapFOLDER);
			menuMapFOLDER = (HashMap<String, Integer>) sortByValue(menuMapFOLDER);
			
			System.out.println("menuMap FOLDER: " + menuMapFOLDER.toString());
			System.out.println("defaultMap FOLDER: " + eventMapFOLDER.toString());
			System.out.println("***Overall userDB: " + userDB.toString());

			makeCSVfile();
			makeTXTfile();

		  } else {
		    // Handle the case where dir is not really a directory.
		    // Checking dir.isDirectory() above would not be sufficient
		    // to avoid race conditions with another process that deletes
		    // directories.
		  }
		} catch (Exception e) {e.printStackTrace();}
	}
	
//	public static String readFile() throws IOException {
//		byte[] encoded = Files.readAllBytes(Paths.get(logFullPath));
//		return new String(encoded);
//	}
	
	public static String readFile(File f) throws IOException {
		byte[] encoded = Files.readAllBytes(Paths.get(f.getAbsolutePath()));
		System.out.println(f.getAbsolutePath());
		return new String(encoded);
	}
	
	public static void parseText(String logText) {
		String[] textLines = logText.split("\r\n"); // split word by each new line
		for (int i=0; i < textLines.length; i++) {
			String word = textLines[i];
			System.out.println(word);
			int commaIndex = word.indexOf(","); // find where the comma is, get the following special key combo
//			System.out.println(commaIndex);
			int spaceIndex = word.indexOf(" "); // find where the space is, indicates end of special key combo
//			System.out.println(spaceIndex);
			String eventKey = word.substring(commaIndex+1, spaceIndex);
//			System.out.println(eventKey); // this is our hashmap key
			
			// add to map for both the overall FOLDER maps, and the individual FILE maps
			checkKey(eventKey, word, menuMapFOLDER, eventMapFOLDER, true);
			checkKey(eventKey, word, menuMapFILE, eventMapFILE, false);
		}
	}
		
			
		public static void checkKey(String eventKey, String word, HashMap<String, Integer> menuFolderMap, HashMap<String, Integer> eventFolderMap, boolean shouldCount) {
			// we will use a whole different hashmap for menu actions
			
			if (eventKey.equals("MC")) { // menu map implementation
				int i = word.lastIndexOf(".");
				String menuKey = word.substring(i+1, word.length()); // the new key is the name of the menu accessed
				add2Map(menuKey, menuFolderMap);
				if (shouldCount) {menuCounterFolder++;}
			} else { 					// default map implementation
				add2Map(eventKey, eventFolderMap);
				if (shouldCount) {eventCounterFolder++;}
			}
		}
		
		
		public static void add2Map(String eventKey, HashMap<String, Integer> map) {
					// check if the eventKey already exists in map; 
					// if it doesn't, add it to the map and set its integer value (freq counter) = 1
					// if it does exist, then increment its integer value to indicate the key was called
			if (map.containsKey(eventKey)) {
				int currentCounter = map.get(eventKey);
				System.out.println("current counter: " + currentCounter);

				map.replace(eventKey, currentCounter, currentCounter+1);
				int newCounter = map.get(eventKey);
				System.out.println("new counter: " + newCounter);

			} else {
				map.put(eventKey, 1); // first occurrence of this event key
				System.out.println("new event key added: " + eventKey);

			}


		}
		
		public static <K, V extends Comparable<? super V>> Map<K, V> sortByValue(Map<K, V> map) { // sort our hashmaps by value
		    return map.entrySet()
		              .stream()
		              .sorted(Map.Entry.comparingByValue(Collections.reverseOrder()))
		              .collect(Collectors.toMap(
		                Map.Entry::getKey, 
		                Map.Entry::getValue, 
		                (e1, e2) -> e1, 
		                LinkedHashMap::new
		              ));
		}
	
		public static void makeCSVfile() throws IOException {
			String date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
			String dateFileNameSafe = date.replaceAll("\\W+", "-");

			String fileName = "log_CSV_" + dateFileNameSafe; 
			System.out.println(fileName);
			Writer writer = new FileWriter(logPath + File.separator + fileName + ".csv");
			writeToCSV(writer, "Menu Name", "Frequency", menuMapFOLDER, menuCounterFolder);
			writeToCSV(writer, "Event Name", "Frequency", eventMapFOLDER, eventCounterFolder);
			writer.close();

		}
		
		public static void writeToCSV(Writer writer, String col1Name, String col2Name, HashMap<String, Integer> map, int occuranceCounter) throws IOException {
			String eol = System.getProperty("line.separator");

			writer.append(col1Name + ',' + col2Name + "\n");
			writer.append("Total: " + ',' + occuranceCounter + "\n");
			  for (Entry<String, Integer> entry : map.entrySet()) {
				    writer.append(entry.getKey())
				          .append(',')
				          .append(entry.getValue().toString())
				          .append(eol);
				  }
				writer.append("\n");
		}
		
		public static void makeTXTfile() throws IOException {
			String date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
			String dateFileNameSafe = date.replaceAll("\\W+", "-");

			String fileName = "log_TXT_" + dateFileNameSafe; 
			System.out.println(fileName);
			Writer writer = new FileWriter(logPath + File.separator + fileName + ".txt");
			for (Entry<String, HashSet<HashMap<String, Integer>>> entry : userDB.entrySet()) {
			    writer.append(entry.getKey())
			          .append(": 	")
			          .append(entry.getValue().toString())
			          .append("\r\n");
			  }
			writer.close();
		}


	public static void main(String[] args) {
		// TODO Auto-generated method stub
//		try {
//			text = readFile();
//			parseText(text);
//					
//			// sort the maps based on their value; this way when exported, our table is already sorted by frequency
//			hMap = (HashMap<String, Integer>) sortByValue(hMap);
//			menuMap = (HashMap<String, Integer>) sortByValue(menuMap);
//
//			System.out.println("menuMap: " + menuMap.toString());
//			System.out.println("defaultMap: " + hMap.toString());
//			
//			makeCSVfile();
//			
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
		new Parser();
	}

}
