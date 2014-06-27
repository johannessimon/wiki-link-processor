package de.tudarmstadt.lt.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class MapUtil {
	public static Map<String, String> readMapFromFile(String fileName, String delimiter) throws IOException {
		Map<String, String> map = new HashMap<String, String>();
		BufferedReader reader = new BufferedReader(new MonitoredFileReader(fileName));
		
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
		BufferedReader reader = new BufferedReader(new MonitoredFileReader(fileName));
		
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
	
	/**
	 * Writes out a map to a UTF-8 file in tsv (tab-separated value) format.
	 * @param map Map to write out
	 * @param out File to write map out to
	 * @throws IOException 
	 */
	public static void writeMap(Map<?,?> map, String out) throws IOException
	{
		Writer outputWriter = FileUtil.createBufferedWriter(out);
		for (Object key : map.keySet()) {
			outputWriter.write(key + "\t" + map.get(key) + "\n");
		}
		outputWriter.close();
	}
}
