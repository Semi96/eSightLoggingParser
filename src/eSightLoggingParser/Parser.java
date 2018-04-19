package eSightLoggingParser;

import java.io.File;
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

	// hashmaps containing overall user stats (for use in .csv output file)
	static HashMap<String, Integer[]> eventMap_overallStats = new HashMap<String, Integer[]>();
	static HashMap<String, Integer[]> menuMap_overallStats = new HashMap<String, Integer[]>();

	// hashmaps containing individual user stats (for use in .txt output file)
	static HashMap<String, Integer[]> eventMap_individualStats = new HashMap<String, Integer[]>();
	static HashMap<String, Integer[]> menuMap_individualStats = new HashMap<String, Integer[]>();

	static int eventCounter_overall;
	static int menuCounter_overall;
	static String outputDir;
	static Writer writerTXT;
	static String fileNameCSV;
	static String fileNameTXT;
	static long startTime = 0;
	static long endTime = 0;
	static String menuNameHandle;
	static HashMap<String, Integer[]> menuMapHolder;
	static long timeDiff;
	static boolean needEndTime = false;
	static boolean recordTimeDiff = false;

	// public Parser() {
	// getLogStats(logDirName, true);
	// }

	// User will call this constructor from command line to initiate script
	// <logFilesDir> is the absolute system directory of the folder containing log
	// files to be analyzed; must be specified by user
	// <needLocalStats> is a boolean that the user specifies; if true, .txt file
	// that contains individual user stats will be produced; if false, this file
	// will not be produced
	public Parser(String logFilesDir, boolean needLocalStats) {
//		System.out.println("Working Directory = " + System.getProperty("user.dir"));
		S3Operations.downloadLogs();

		
		// check that log file directory exists
		File theDir = new File(logFilesDir);
		if (!theDir.exists()) {
			System.out.println("Cannot find folder containing log files. Ensure folder name is \"Log_Files\" and is located in current working directory");
			System.out.println("Current working directory is: " + System.getProperty("user.dir"));
			return;
		}
		
		createDir("Parser_Output_Files");
		System.out.println("Beginning Log Analysis...");
		getLogStats(logFilesDir, needLocalStats);
	}

	public static void getLogStats(String logFilesDirectory, boolean needLocal) {
		try {
			makeTXTfile();
		} catch (IOException e) {
			System.out.println("Error- could not create text file");
			e.printStackTrace();
		}
		accessFiles(logFilesDirectory, needLocal);
	}

	public static void accessFiles(String folderPathName, boolean needLocal) {
		try {
			// Open directory and get each file inside
			File dir = new File(folderPathName);
			if (!dir.exists()) {
				System.out.println("Error- specified directory does not exist");
				return;
			}

			File[] directoryListing = dir.listFiles();
			if (directoryListing != null) {
				for (File child : directoryListing) {
					// Begin Analysis of Each File in Folder
					try {
						getStats(child, needLocal);
						// ensure we are not carrying-over stats across different logs (i.e. time-keeping across two events in different files)
						resetLocalFlags();
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				// close the TXT file writer after all files analyzed
				writerTXT.close();

				// afterwards, sort the Folder hashmaps
				System.out.println("Analysis complete. Building output files...");
				eventMap_overallStats = (HashMap<String, Integer[]>) sortFrequency(eventMap_overallStats);
				menuMap_overallStats = (HashMap<String, Integer[]>) sortFrequency(menuMap_overallStats);

				// create & upload the .csv file containing total user statistics
				makeCSVfile();
				System.out.println("Files built. Uploading to S3...");

				S3Operations.uploadFile(outputDir, fileNameCSV + ".csv");

				// upload the .txt file containing individual user stats, if requested
				if (needLocal) {
					S3Operations.uploadFile(outputDir, fileNameTXT + ".txt");
				}
				
				removeDirectory(dir);
				
				System.out.println("Upload & Analysis Complete!");

			} else {
				System.out.println("Error- specified directory appears to be empty");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public static void removeDirectory(File dir) {
	    if (dir.isDirectory()) {
	        File[] files = dir.listFiles();
	        if (files != null && files.length > 0) {
	            for (File aFile : files) {
	                removeDirectory(aFile);
	            }
	        }
	        dir.delete();
	    } else {
			dir.delete();
			System.out.println("deleted log files folder & contents");
	    }
	}
	
	public static void resetLocalFlags() {
		startTime = 0;
		endTime = 0;
		needEndTime = false;
		recordTimeDiff = false;
		timeDiff = 0;
	}

	public static void getStats(File logFile, boolean needLocal) {
		try {
			// name will be device's Serial Number
			String logFileName = logFile.getName();
			if (logFileName.contains(".")) {
			logFileName = logFileName.substring(0, logFileName.lastIndexOf(".")); // name without the file extension
																				// (i.e '.txt')
			} else {
				logFileName = logFile.getName();
			}
			parseText(readFile(logFile), needLocal, logFileName);
		} catch (Exception e) {
			System.out.println("Error reading log file with name: " + logFile.getName());
			e.printStackTrace();
		}
	}

	// returns all text from a single file as a single String
	public static String readFile(File f) throws IOException {
		byte[] encoded = Files.readAllBytes(Paths.get(f.getAbsolutePath()));
		return new String(encoded);
	}

	public static void parseText(String logText, boolean needLocal, String logFileName) {
		// split log text by each new line
		String[] textLines = logText.split("\r\n");

		// If Local Stats are requested, empty maps so that no previous file data is
		// kept when accessing a new log file;
		if (needLocal) {
			Parser.eventMap_individualStats = new HashMap<String, Integer[]>();
			Parser.menuMap_individualStats = new HashMap<String, Integer[]>();
		}

		// Analysis each line of text in the file, getting relevant time & event
		// information
		for (int i = 0; i < textLines.length; i++) {
			try {
				String word = textLines[i];
				int commaIndex = word.indexOf(","); // find where the comma is to get the event string that immediately
													// follows
				int spaceIndex = word.indexOf(" "); // find where the space is, indicates end of event string
				String eventKey = word.substring(commaIndex + 1, spaceIndex); // eventKey e.g. "MC", "ZP", "MainMenu",
																				// etc.

				// process this event/action occurring, recording it in the proper OVERALL stats
				checkKey(eventKey, word, menuMap_overallStats, eventMap_overallStats, true, false);

				// Additionally process this event/action occurring, recording it in the proper
				// INDIVIDUAL stats
				if (needLocal) {
					checkKey(eventKey, word, menuMap_individualStats, eventMap_individualStats, false, true);
				}
			} catch (Exception e) {
				e.getMessage();
			}
		}

		// Once all lines have been processed in this particular log file, write the
		// stats to the TXT file for individual devices, if requested
		if (needLocal) {
			try {
				writerTXT.append(logFileName + ":	");
				// sort then write
				writeToTXT(writerTXT, "Menu", sortFrequency(menuMap_individualStats));
				writeToTXT(writerTXT, "Event", sortFrequency(eventMap_individualStats));
				writerTXT.append("\r\n");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	// Compare the provided eventKey to our maps
	// Based on the key, it is either an event/menu selection, and is added to the
	// the respective map.
	// ** Time-Keeping stats are ONLY tracked for menu events in OVERALL statistics
	// analysis **

	// <key> contains the event String, i.e. "ZP", "P+", "MainMenu"
	// <word> is provided to allow time-keeping of events (as it contains the action
	// time-stamp) (only when shouldCountTime = true)
	// <menuMap> and <eventMap> are the passed hashmaps that will store stats
	// <shouldCountTime> indicates whether we want to keep track of time stats for
	// the event
	// <isIndividualRequest> indicates whether this analysis is for overall or
	// individual stats analysis
	public static void checkKey(String key, String word, HashMap<String, Integer[]> menuMap,
			HashMap<String, Integer[]> eventMap, boolean shouldCountTime, boolean isIndividualRequest) {
		if (needEndTime && shouldCountTime) {
			try {
				endTime = convertTimeToMillis(word);
				needEndTime = false;
				timeDiff = (endTime - startTime) / 1000; // seconds
				// arbitrary large value (one week) to catch condition where eSight time
				// switches on wifi connection, causing big jump in system time
				if (Math.abs(timeDiff) < 7 * 24 * 60 * 60) {
					// indicate that we do in fact want to record the time difference calculated
					// above
					recordTimeDiff = true;
				}
			} catch (Exception e) {
				e.getMessage();
			}
		}

		if (key.equals("MC")) { // menu map implementation
			int i = word.lastIndexOf(".");
			// the new key holds the name of the menu accessed (i.e. WifiMenu), at the end
			// of <word>
			String menuKey = word.substring(i + 1, word.length());
			addToMap(menuKey, menuMap, recordTimeDiff, isIndividualRequest);

			if (shouldCountTime) {
				menuCounter_overall++;
				try {
					startTime = convertTimeToMillis(word);
					// indicate we need an end-time, so on the next call of checkKey, we get the
					// time difference
					needEndTime = true;

					// this name handle holds the name of the current menu accessed; required
					// because on the next line analyzed,
					// we need to remember what the previous menu key was to access its 2nd value
					// (containing total time used)
					menuNameHandle = menuKey;
				} catch (Exception e) {
					e.getMessage();
				}
			}
		} else { // event map implementation
			addToMap(key, eventMap, recordTimeDiff, isIndividualRequest);
			if (shouldCountTime) {
				eventCounter_overall++;
			}
		}
		recordTimeDiff = false;
	}

	// takes the time-keeping info from the line as a String, and convert to an
	// equivalent time, in milliseconds
	public static long convertTimeToMillis(String date) throws ParseException {
		String timeInString = date.substring(0, date.indexOf(',')).replaceAll("-", "");
		Calendar c = Calendar.getInstance();
		c.setTime(new SimpleDateFormat("yyyyMMddHHmmss").parse(timeInString));
		return c.getTimeInMillis();
	}

	// checks the provided key against the provided map, determining
	// whether to put a new key/value set in the hashmap if
	// the map contains that key, or change value[0] contained by that key,
	// incremented it to indicate this particular key/menu has been
	// used by the User once more.
	// How long a particular menu has been accessed for is record in value[1], if
	// specific by <shouldTrackTime>

	public static void addToMap(String key, HashMap<String, Integer[]> map, boolean shouldTrackTime,
			boolean isIndividualRequest) {
		// check if the eventKey already exists in map;
		// if it doesn't, add it to the map and set its integer value (freq counter) = 1
		// if it does exist, then increment its integer value to indicate the key was
		// called once more
		if (map.containsKey(key)) {
			int newCounter = map.get(key)[0] + 1;
			int timeCounter = map.get(key)[1];
			Integer[] newInteger = { newCounter, timeCounter };
			// Note: map.replace(eventKey, oldInteger, newInteger) does NOT work because a
			// new Integer[] object is
			// considered a different object, thus this method never works in this usecase
			// Thus, we remove this key/value pair altogether and put it again with updated
			// values
			map.remove(key);
			map.put(key, newInteger);
		} else {
			// first occurrence of this event key
			Integer[] initialValues = { 1, 0 };
			map.put(key, initialValues);
		}

		// Time-keeping, if required
		if (shouldTrackTime && !isIndividualRequest) {
			Integer[] j = { menuMap_overallStats.get(menuNameHandle)[0],
					menuMap_overallStats.get(menuNameHandle)[1] + (int) timeDiff };
			menuMap_overallStats.remove(menuNameHandle);
			menuMap_overallStats.put(menuNameHandle, j);
		}
	}

	// sort hashmap by value; only works when map value is singular (i.e. not an
	// array of values)
	public static <K, V extends Comparable<? super V>> Map<K, V> sortByValue(Map<K, V> map) {
		return map.entrySet().stream().sorted(Map.Entry.comparingByValue(Collections.reverseOrder()))
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
	}

	// sort hashmap by a particular value; works when holding multiple values
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
		writerTXT = new FileWriter(outputDir + File.separator + fileNameTXT + ".txt");
	}

	public static void makeCSVfile() throws IOException {
		String date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
		String dateFileNameSafe = date.replaceAll("\\W+", "-");

		fileNameCSV = "log_CSV_" + dateFileNameSafe;
		Writer writerCSV = new FileWriter(outputDir + File.separator + fileNameCSV + ".csv");
		writeToCSV(writerCSV, "Menu Name", "Frequency", menuMap_overallStats, menuCounter_overall, true);
		writeToCSV(writerCSV, "Event Name", "Frequency", eventMap_overallStats, eventCounter_overall, false);
		writerCSV.close();
	}

	public static void writeToCSV(Writer writer, String col1Name, String col2Name, HashMap<String, Integer[]> map,
			int occuranceCounter, boolean needTiming) throws IOException {
		String eol = System.getProperty("line.separator");

		if (needTiming) {
		writer.append(col1Name + ',' + col2Name + ',' + "Total Time (min)" + ',' + "Average Time (sec)" + ',' + "\n");
		} else {
			writer.append(col1Name + ',' + col2Name + "\n");
		}
		
		writer.append("Total: " + ',' + occuranceCounter + "\n");
		for (Entry<String, Integer[]> entry : map.entrySet()) {
			writer.append(entry.getKey()).append(',').append(entry.getValue()[0].toString()).append(',');
			
			if (needTiming) {
				writer.append((String.format("%.2f", ((float) (entry.getValue()[1]) / 60.0f)))).append(',')
				.append(String.format("%.2f", ((float) (entry.getValue()[1])) / ((float) entry.getValue()[0]))); // average sec/menu
			}
			
			writer.append(eol);
		}
		writer.append("\n");
	}

	public static void writeToTXT(Writer writer, String mapName, HashMap<String, Integer[]> map) throws IOException {
		writer.append(mapName + "{");
		for (Entry<String, Integer[]> entry : map.entrySet()) {
			writer.append(entry.getKey()).append(" = ").append(entry.getValue()[0].toString()).append(", ");
		}
		writer.append("} ").append("\r\n").append("		");
	}

	public static void createDir(String dirName) {
		outputDir = System.getProperty("user.dir") + File.separator + dirName;
		File theDir = new File(outputDir);

		// if the directory does not exist, create it
		if (!theDir.exists()) {
			System.out.println("Creating directory: " + theDir.getName() + " to store produced files");
			new File(outputDir).mkdirs();
		} else {
			System.out.println("Directory: " + theDir.getName() + " will store produced files");
		}
	}

	public static void main(String[] args) {
		new Parser(System.getProperty("user.dir") + File.separator + "Log_Files", true);
//		S3Operations.downloadLogs();
	}

}
