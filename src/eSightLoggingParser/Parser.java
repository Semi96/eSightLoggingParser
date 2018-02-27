package eSightLoggingParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

public class Parser {

	////// ********************* ATTRIBUTE DECLARATION ************************* //////

//	static String logPathDefault = "C:" + File.separator + "Users" + File.separator + "Semi" + File.separator + "eSightLogFiles";
//	static String logFileName = "600058.txt";
//	static String logFullPath = logPathDefault + File.separator + logFileName;
	static String outputDir;
	static HashMap<String, Integer[]> eventMapFOLDER = new HashMap<String, Integer[]>();
	static HashMap<String, Integer[]> menuMapFOLDER = new HashMap<String, Integer[]>();
//	static HashMap<String, Integer[]> eventMapFILE = new HashMap<String, Integer[]>();
//	static HashMap<String, Integer[]> menuMapFILE = new HashMap<String, Integer[]>();
	// stores individual statistics for each user
	// it holds the key (device serial number ideally), and a collection of the menu & default stats, in form of a hashmap
//	static HashMap< String, List<HashMap<String, Integer[]>> > userDB = new HashMap<String, List<HashMap<String, Integer[]>>>(); 
	static int menuCounterFolder;
	static int eventCounterFolder;
	static HashMap<String, Integer[]> IndividualEventMap = new HashMap<String, Integer[]>();
	static HashMap<String, Integer[]> IndividualMenuMap = new HashMap<String, Integer[]>();
	static Writer writer;
	static String fileNameCSV;
	static String fileNameTXT;

//	public Parser() {
//		getLogStats(logDirName, true);
//	}

	// User will call this constructor from command line to initiate script
	public Parser(String logFilesDirectory, boolean needLocal) {
		getLogStats(logFilesDirectory, needLocal);
	}

	public static void getLogStats(String logFilesDirectory, boolean needLocal) {
		try {
			makeTXTfile(); // FIX: this will add a file to the list of log files in the folder, will cause a parse error... dont want that
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		openFolder(logFilesDirectory, needLocal);
	}
	
	public static void openFolder(String folderPathName, boolean needLocal) {
		try {
			// Open directory and get each file inside
			File dir = new File(folderPathName);
			File[] directoryListing = dir.listFiles();
			if (directoryListing != null) {
				for (File child : directoryListing) {
					// Begin Analysis of Each File in Folder
					try {
						getStats(child, needLocal);



					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				writer.close(); // close the TXT file writer
				// finally, sort the FOLDER maps add make a CSV file out of them
				eventMapFOLDER = (HashMap<String, Integer[]>) sortFrequency(eventMapFOLDER);
				menuMapFOLDER = (HashMap<String, Integer[]>) sortFrequency(menuMapFOLDER);

//				System.out.println("menuMap FOLDER: " + menuMapFOLDER.toString());
//				System.out.println("defaultMap FOLDER: " + eventMapFOLDER.toString());
//				System.out.println("***Overall userDB: " + userDB.toString());

				makeCSVfile();
				S3Operations.uploadFile(outputDir, fileNameCSV + ".csv");
				S3Operations.uploadFile(outputDir, fileNameTXT + ".txt");

			} else {
				// Handle the case where dir is not really a directory.
				// Checking dir.isDirectory() above would not be sufficient
				// to avoid race conditions with another process that deletes
				// directories.
			}
		} catch (Exception e) {e.printStackTrace();}
	}


	public static void getStats(File logFile, boolean needLocal) {
		String logFileName = logFile.getName();
		logFileName = logFileName.substring(0, logFileName.lastIndexOf(".")); // name without the file extension (i.e '.txt')
		try {
			parseText(readFile(logFile), needLocal, logFileName);
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}


	public static String readFile(File f) throws IOException {
		byte[] encoded = Files.readAllBytes(Paths.get(f.getAbsolutePath()));
		System.out.println(f.getAbsolutePath());
		return new String(encoded);
	}

	public static void parseText(String logText, boolean needLocal, String logFileName) {
		String[] textLines = logText.split("\r\n"); // split word by each new line

		// IF Local Stats are requested, reassign maps here, such that new maps are instantiated each time we access a new log file; we have no need to keep a giant record of all users in one convoluted hashmap		
		if (needLocal) {
//			IndividualEventMap = new HashMap<String, Integer[]>();
//			IndividualMenuMap = new HashMap<String, Integer[]>();
			Parser.IndividualEventMap = new HashMap<String, Integer[]>();
			Parser.IndividualMenuMap = new HashMap<String, Integer[]>();
		}

		// Analysis each line of text in the file, getting relevant time stamps, event keys
		for (int i=0; i < textLines.length; i++) {
			try {
				String word = textLines[i];
				int commaIndex = word.indexOf(","); // find where the comma is, get the following special key combo
				int spaceIndex = word.indexOf(" "); // find where the space is, indicates end of special key combo
				String eventKey = word.substring(commaIndex+1, spaceIndex); // eventKey e.g. "MC", "ZP", etc.

				checkKey(eventKey, word, menuMapFOLDER, eventMapFOLDER, true, false);
				if (needLocal) {
					checkKey(eventKey, word, IndividualMenuMap, IndividualEventMap, false, true);
				}
				//				checkKey(eventKey, word, menuMapFOLDER, eventMapFOLDER, true);
				//				checkKey(eventKey, word, menuMapFILE, eventMapFILE, false);
			} catch (Exception e) {e.getMessage();}
		}
		// Once all lines have been processed in this particular log file, we append it to the TXT file for individual devices
		if (needLocal) {
			try {
				// sort then write
//				HashMap<String, Integer[]> eventMapFILExx = (HashMap<String, Integer[]>) sortFrequency(IndividualEventMap);
//				HashMap<String, Integer[]> menuMapFILExx = (HashMap<String, Integer[]>) sortFrequency(IndividualMenuMap);
				writer.append(logFileName + ":	");
				writeToTXT(writer, "Menu",  sortFrequency(IndividualMenuMap));
				writeToTXT(writer, "Event", sortFrequency(IndividualEventMap));
				writer.append("\r\n");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}


	static long startTime = 0;
	static long endTime = 0;
	static String menuNameHandle;
	static HashMap<String, Integer[]> menuMapHolder;
	static long timeDiff;
	static boolean needEndTime = false;
	static boolean recordTimeDiff = false;

	public static long convertTimeToMillis(String date) throws ParseException {
		String timeInString = date.substring(0, date.indexOf(',')).replaceAll("-", "");
		Calendar c = Calendar.getInstance();
		c.setTime(new SimpleDateFormat("yyyyMMddHHmmss").parse(timeInString));
		return c.getTimeInMillis();
	}


	// Compare the provided eventKey to our maps
	// Based on the key, it is either an event/menu selection, and is added to the the respective map
	// word is provided to allow time-keeping of events, only when shouldCountTime = true

	public static void checkKey(String key, String word, HashMap<String, Integer[]> menuMap, HashMap<String, Integer[]> eventMap, boolean shouldCountTime, boolean isIndividualRequest) {

		if (needEndTime && shouldCountTime) {
			try { 
				endTime = convertTimeToMillis(word);
				System.out.println("****** end time: " + endTime + ", we got end time, lets do the difference");
				needEndTime = false;
				timeDiff = (endTime-startTime)/1000;
				System.out.println("****** DIFF: " + timeDiff);
				if (Math.abs(timeDiff) < 7*24*60*60) { // arbitrary large value (one week) to catch condition where eSight time switches on wifi connection, causing big jump in time
					System.out.println("**ignoring this difference");
					recordTimeDiff = true;
				}
			} catch (Exception e) {e.getMessage();}
		}

		if (key.equals("MC")) {
			int i = word.lastIndexOf(".");
			String menuKey = word.substring(i+1, word.length()); // the new key holds the name of the menu accessed (i.e. WifiMenu), at the end of <word>
			add2Map(menuKey, menuMap, recordTimeDiff, isIndividualRequest);

			if (shouldCountTime) {
				menuCounterFolder++;
				try { 
					startTime = convertTimeToMillis(word);
					System.out.println("****** start time: " + startTime + ", now we need end time");
					needEndTime = true; // on the next call of checkKey, we get the time and then will take the difference
					// this name handle holds the name of the current menu accessed; required because on the next line analyzed,
					// we need to remember what the previous menu accessed was & be able to add timeDifference to that key's value[1]
					menuNameHandle = menuKey;
				} catch (Exception e) {e.getMessage();}
			}
		} else { 	// event map implementation
			add2Map(key, eventMap, recordTimeDiff, isIndividualRequest);
			if (shouldCountTime) {eventCounterFolder++;}
		}
		recordTimeDiff = false;
	}


	// This method checks the provided key against the provided map, determining whether to put a new key/value set in the hashmap if
	// the map contains that key, or change value[0] contained by that key, incremented it to indicate this particular key/menu has been
	// used by the User once more. 
	// How long a particular menu has been accessed for is record in value[1], if needed 
	
	public static void add2Map(String key, HashMap<String, Integer[]> map, boolean shouldTrackTime, boolean isIndividualRequest) {
		// check if the eventKey already exists in map; 
		// if it doesn't, add it to the map and set its integer value (freq counter) = 1
		// if it does exist, then increment its integer value to indicate the key was called
		if (map.containsKey(key)) {
			int timeCounter = map.get(key)[1];
			int newCounter = map.get(key)[0]+1;
			Integer[] newInteger = {newCounter, timeCounter};
			// map.replace(eventKey, oldInteger, newInteger); // replace() does NOT work because Integer[] objects are technically 
			// considered different objects even if their values are the same
			// Thus, we we remove this key/value pair altogether and put it again with updated values
			map.remove(key);
			map.put(key, newInteger);
		} else {
			// first occurrence of this event key
			Integer[] initialValues = {1, 0};
			map.put(key, initialValues);
			System.out.println("new event key added: " + key);
		}

		if (shouldTrackTime && !isIndividualRequest) { // isIndividualRequest flag avoids wrongly performing the below procedure on every individual device's hashmap
//			Integer[] j = {menuMapFOLDER.get(menuNameHandle)[0], menuMapFOLDER.get(menuNameHandle)[1] + (int)timeDiff};
			Integer[] j = {menuMapFOLDER.get(menuNameHandle)[0], menuMapFOLDER.get(menuNameHandle)[1] + (int)timeDiff};
			//				menuMapFOLDER.replace(menuNameHandle, i, j); // HARD-CODING menuMapFolder for keeping track of time; NOT ideal for scalability
			menuMapFOLDER.remove(menuNameHandle);
			menuMapFOLDER.put(menuNameHandle, j);

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

	public static LinkedHashMap<String, Integer[]> sortFrequency(Map<String, Integer[]> unSortedMap) {
		List<Map.Entry<String, Integer[]>> list = new ArrayList<Map.Entry<String, Integer[]>>(unSortedMap.entrySet());

		Collections.sort(list, new Comparator<Map.Entry<String, Integer[]>>() {
			public int compare(Map.Entry<String, Integer[]> a, Map.Entry<String, Integer[]> b) {
				return b.getValue()[0].compareTo(a.getValue()[0]);
			}
		});

		LinkedHashMap<String, Integer[]> result = new LinkedHashMap<String, Integer[]>();
		for (Entry<String, Integer[]> entry : list) {
			result.put(entry.getKey(), entry.getValue());
		}

		return result;
	}


	public static void makeTXTfile() throws IOException {
		String date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
		String dateFileNameSafe = date.replaceAll("\\W+", "-");

		fileNameTXT = "log_TXT_" + dateFileNameSafe; 
		// figure out where to put the TXT file
		writer = new FileWriter(outputDir + File.separator + fileNameTXT + ".txt"); 
	}

	public static void makeCSVfile() throws IOException {
		String date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
		String dateFileNameSafe = date.replaceAll("\\W+", "-");

		fileNameCSV = "log_CSV_" + dateFileNameSafe; 
//		System.out.println(fileName);
		Writer writerCSV = new FileWriter(outputDir + File.separator + fileNameCSV + ".csv");
		writeToCSV(writerCSV, "Menu Name", "Frequency", menuMapFOLDER, menuCounterFolder);
		writeToCSV(writerCSV, "Event Name", "Frequency", eventMapFOLDER, eventCounterFolder);
		writerCSV.close();

	}

	public static void writeToCSV(Writer writer, String col1Name, String col2Name, HashMap<String, Integer[]> map, int occuranceCounter) throws IOException {
		String eol = System.getProperty("line.separator");

		writer.append(col1Name + ',' + col2Name + ',' + "Total Time (min)" + ',' + "Average Time (sec)" + ',' + "\n");
		writer.append("Total: " + ',' + occuranceCounter + "\n");
		for (Entry<String, Integer[]> entry : map.entrySet()) {
			writer.append(entry.getKey())
			.append(',')
			.append(entry.getValue()[0].toString())
			.append(',')
			.append( (String.format( "%.2f", ((float)(entry.getValue()[1])/60.0f) )) )
			.append(',')
			.append(String.format( "%.2f",   ((float)(entry.getValue()[1])) / ((float)entry.getValue()[0])  )) // average sec/menu
			.append(eol);
		}
		writer.append("\n");
	}

	public static void writeToTXT(Writer writer, String mapName, HashMap<String, Integer[]> map) throws IOException {
//		String date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
//		String dateFileNameSafe = date.replaceAll("\\W+", "-");
//
//		String fileName = "log_TXT_" + dateFileNameSafe; 
//		Writer writer = new FileWriter(logPathDefault + File.separator + fileName + ".txt");
		
		writer.append(mapName + "{");
		for (Entry<String, Integer[]> entry : map.entrySet()) {

		
		
		
//		for (Entry<String, List<HashMap<String, Integer[]>>> entry : userDB.entrySet()) {
//			writer.append(entry.getKey()) // this gets serial number
//			.append(": 	");
//			for(int i=0; i < entry.getValue().size(); i++) { // size should be = 2
//				writer.append("{");
//				for (Entry<String, Integer[]> entryInner : entry.getValue().get(i).entrySet()) { // this gets the inner hashmap of the list
					writer.append(entry.getKey())
					.append(" = ")
					.append(entry.getValue()[0].toString())
					.append(", ");
//					//			         	 .append(entryInner.getValue()[1].toString());
				}
				writer.append("} ");
//			}
			writer.append("\r\n")
				.append("		");

//			//			    writer.append(entry.getKey())
//			//			          .append(": 	")
//			//			    writer.append(entryInner) // get the first element of the list; its a hashmap
//			//			          .append(", 	")
//			//			          .append(entry.getValue().get(1).entrySet().toString()) // get the first element of the list; its a hashmap
//			//			          .append("\r\n");
//		}
//		writer.close();
	}
	
	public static void createDir(String dirName) {
//		new File(System.getProperty("user.dir") + File.separator + dirName).mkdirs();
		outputDir = System.getProperty("user.dir") + File.separator + dirName;
		File theDir = new File(outputDir);

		// if the directory does not exist, create it
		if (!theDir.exists()) {
		    System.out.println("creating directory: " + theDir.getName());
			new File(outputDir).mkdirs();
		}
//		    boolean result = false;
//
//		    try{
//		        theDir.mkdir();
//		        result = true;
//		    } 
//		    catch(SecurityException se){
//		        //handle it
//		    }        
//		    if(result) {    
//		        System.out.println("DIR created");  
//		    }
//		}
	}

	public static void main(String[] args) {
//		new Parser();
		System.out.println("Working Directory = " + System.getProperty("user.dir"));
		createDir("Output_Files");
		
		new Parser("C:\\Users\\Semi\\eSightLogFiles", true);
	}

}
