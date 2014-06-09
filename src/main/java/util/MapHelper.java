package util;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MapHelper {
	public static Map<String, String> readMapFromFile(String fileName, String delimiter) throws IOException {
		Map<String, String> map = new HashMap<String, String>();
		MonitoredFileReader reader = new MonitoredFileReader(fileName);
		
		String line;
		while ((line = reader.readLine()) != null) {
			String[] parts = line.split(delimiter);
			if (parts.length == 2) {
				map.put(parts[0], parts[1]);
			} else {
				System.err.println("MapHelper.readMapFromFile: column count != 2: " + line);
			}
		}
		
		reader.close();
		return map;
	}

	public static Set<String> readSetFromFile(String fileName) throws IOException {
		Set<String> set = new HashSet<String>();
		MonitoredFileReader reader = new MonitoredFileReader(fileName);
		
		String line;
		while ((line = reader.readLine()) != null) {
			set.add(line);
		}
		
		reader.close();
		return set;
	}
	
	public static void main(String[] args) throws IOException {
		readMapFromFile("/Volumes/G-DRIVE/Masterarbeit/wiki/enwiki-lex-filtered.txt.redirects", "\t");
	}
}
