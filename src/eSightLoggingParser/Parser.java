package eSightLoggingParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
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
	static HashMap<String, Integer[]> eventMapFOLDER = new HashMap<String, Integer[]>();
//	static HashMap<String, Integer> eventMapFOLDER_TIME = new HashMap<String, Integer>(); // use this to parallel store time values of menus accessed
	static HashMap<String, Integer[]> menuMapFOLDER = new HashMap<String, Integer[]>();
	static HashMap<String, Integer[]> eventMapFILE = new HashMap<String, Integer[]>();
	static HashMap<String, Integer[]> menuMapFILE = new HashMap<String, Integer[]>();
	// stores individual statistics for each user
	// it holds the key (device serial number ideally), and a collection of the menu & default stats, in form of a hashmap
	static HashMap< String, List<HashMap<String, Integer[]>> > userDB = new HashMap<String, List<HashMap<String, Integer[]>>>(); 
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
					eventMapFILE = (HashMap<String, Integer[]>) sortFrequency(eventMapFILE);
					menuMapFILE = (HashMap<String, Integer[]>) sortFrequency(menuMapFILE);

					System.out.println("menuMap FILE: " + menuMapFILE.toString());
					System.out.println("defaultMap FILE: " + eventMapFILE.toString());
					
					List<HashMap<String, Integer[]>> c = new ArrayList<HashMap<String, Integer[]>>();
					c.add(menuMapFILE);
					c.add(eventMapFILE);
					userDB.put(logFileName, c); // accumulate a hashmap for each individual user statistics
					
					// quick & dirty way to reset the FILE maps so they contain no previous file's stats
					eventMapFILE = new HashMap<String, Integer[]>();
					menuMapFILE = new HashMap<String, Integer[]>();
										
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
		    }
		    
		    // finally, sort the FOLDER maps add make a CSV file out of them
		    eventMapFOLDER = (HashMap<String, Integer[]>) sortFrequency(eventMapFOLDER);
			menuMapFOLDER = (HashMap<String, Integer[]>) sortFrequency(menuMapFOLDER);
			
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
			try {
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
			} catch (Exception e) {e.getMessage();}
		}
	}
		
			
	static long startTime = 0;
	static long endTime = 0;
	static String menuNameHandle;
	static long timeDiff;
	static boolean needEndTime = false;
	static boolean recordTimeDiff = false;

	public static long convertTimeToMillis(String date) throws ParseException {
		String timeInString = date.substring(0, date.indexOf(',')).replaceAll("-", "");
		Calendar c = Calendar.getInstance();
		c.setTime(new SimpleDateFormat("yyyyMMddHHmmss").parse(timeInString));
		return c.getTimeInMillis();
	}
	
	
		public static void checkKey(String eventKey, String word, HashMap<String, Integer[]> menuFolderMap, HashMap<String, Integer[]> eventFolderMap, boolean shouldCount) {
			// we will use a whole different hashmap for menu actions
			
			if (needEndTime && shouldCount) {
				try { 
					endTime = convertTimeToMillis(word);
					System.out.println("****** end time: " + endTime + ", we got end time, lets do the difference");
					needEndTime = false;
//					recordTimeDiff = true;
					timeDiff = (endTime-startTime)/1000;
					System.out.println("****** DIFF: " + timeDiff);
					if (Math.abs(timeDiff) < 7*24*60*60) {
						recordTimeDiff = true;
					} // arbitrary large value (one week) to catch condition where eSight time switches on wifi connection, causing big jump in time
					} catch (Exception e) {e.getMessage();
					}
				}
	
			
			if (eventKey.equals("MC")) { // menu map implementation
				int i = word.lastIndexOf(".");
				String menuKey = word.substring(i+1, word.length()); // the new key is the name of the menu accessed
				add2Map(menuKey, menuFolderMap, recordTimeDiff);

				if (shouldCount) {
					try { 
					startTime = convertTimeToMillis(word);
					System.out.println("****** start time: " + startTime + ", now we need end time");
					needEndTime = true; // on the next call of checkKey, we get the time and then will take the difference
					menuNameHandle = menuKey;
					} catch (Exception e) {e.getMessage();}
				}
							
				if (shouldCount) {menuCounterFolder++;}
			} else { 					// default map implementation
				add2Map(eventKey, eventFolderMap, recordTimeDiff);
				if (shouldCount) {eventCounterFolder++;}
			}
			recordTimeDiff = false;
		}
		
		
		public static void add2Map(String eventKey, HashMap<String, Integer[]> map, boolean shouldTrackTime) {
					// check if the eventKey already exists in map; 
					// if it doesn't, add it to the map and set its integer value (freq counter) = 1
					// if it does exist, then increment its integer value to indicate the key was called
			if (map.containsKey(eventKey)) {
				int currentCounter = map.get(eventKey)[0];
				int timeCounter = map.get(eventKey)[1];
				Integer[] oldInteger = {currentCounter, timeCounter};
				System.out.println("current counter: " + currentCounter);

				int newCounter = currentCounter+1;
				Integer[] newInteger = {newCounter, timeCounter};
//				map.replace(eventKey, oldInteger, newInteger); // replace() does NOT work because Integer[] objects are techinically considered diferent even if their values are the same
				map.remove(eventKey);
				map.put(eventKey, newInteger);
				System.out.println("new counter: " + newCounter);

			} else {
				Integer[] initialValues = {1, 0};
				map.put(eventKey, initialValues); // first occurrence of this event key
				System.out.println("new event key added: " + eventKey);

			}
			
			if (shouldTrackTime) {
				Integer[] i = {menuMapFOLDER.get(menuNameHandle)[0], menuMapFOLDER.get(menuNameHandle)[1]};
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
		
		public static void writeToCSV(Writer writer, String col1Name, String col2Name, HashMap<String, Integer[]> map, int occuranceCounter) throws IOException {
			String eol = System.getProperty("line.separator");

			writer.append(col1Name + ',' + col2Name + ',' + "Total Time (sec)" + "\n");
			writer.append("Total: " + ',' + occuranceCounter + "\n");
			  for (Entry<String, Integer[]> entry : map.entrySet()) {
				    writer.append(entry.getKey())
				          .append(',')
				          .append(entry.getValue()[0].toString())
				          .append(',')
				          .append( (String.format( "%.2f", ((float)(entry.getValue()[1])/60.0f) )) )
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
			for (Entry<String, List<HashMap<String, Integer[]>>> entry : userDB.entrySet()) {
				writer.append(entry.getKey()) // this gets serial number
		          .append(": 	");
				for(int i=0; i < entry.getValue().size(); i++) { // size should be = 2
					writer.append("{");
					for (Entry<String, Integer[]> entryInner : entry.getValue().get(i).entrySet()) { // this gets the inner hashmap of the list
						writer.append(entryInner.getKey())
						.append(" = ")
						.append(entryInner.getValue()[0].toString())
						.append(", ");
//			         	 .append(entryInner.getValue()[1].toString());
					}
					writer.append("}, ");
				}
				writer.append("\r\n");
//			    writer.append(entry.getKey())
//			          .append(": 	")
//			    writer.append(entryInner) // get the first element of the list; its a hashmap
//			          .append(", 	")
//			          .append(entry.getValue().get(1).entrySet().toString()) // get the first element of the list; its a hashmap
//			          .append("\r\n");
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
