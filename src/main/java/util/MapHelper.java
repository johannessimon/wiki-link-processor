package util;

import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

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
	
	public static <K, V extends Comparable<V>> Map<K, V> sortMapByValue(Map<K, V> map) {
		ValueComparator<K, V> vc = new ValueComparator<K, V>(map);
		Map<K, V> sortedMap = new TreeMap<K, V>(vc);
		sortedMap.putAll(map);
		return sortedMap;
	}
	
	static class ValueComparator<K, V extends Comparable<V>> implements Comparator<K> {
	    Map<K, V> base;
	    public ValueComparator(Map<K, V> base) {
	        this.base = base;
	    }

	    // Note: this comparator imposes orderings that are inconsistent with equals.    
	    public int compare(K a, K b) {
	        if (base.get(a).compareTo(base.get(b)) > 0) {
	            return -1;
	        } else {
	            return 1;
	        } // returning 0 would merge keys
	    }
	}
	
	public static void main(String[] args) throws IOException {
		readMapFromFile("/Volumes/G-DRIVE/Masterarbeit/wiki/enwiki-lex-filtered.txt.redirects", "\t");
	}
}
