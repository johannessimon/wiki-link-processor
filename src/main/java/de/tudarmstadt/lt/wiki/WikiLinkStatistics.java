package de.tudarmstadt.lt.wiki;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.io.input.CountingInputStream;

import de.tudarmstadt.lt.util.MapHelper;
import de.tudarmstadt.lt.util.WikiUtil;


public class WikiLinkStatistics {
	private void readPageTitles(String file) throws IOException {
		BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
		String line;
		while((line = in.readLine()) != null) {
			pageTitles.add(line);
		}
		in.close();
	}

	private void readRedirects(String file) throws IOException {
		BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
		String line;
		while((line = in.readLine()) != null) {
			String splits[] = line.split("\t");
			if (splits.length == 2) {
				String target = splits[1];
				if (target.contains("#")) {
					target = target.substring(0, target.indexOf('#'));
				}
//				System.out.println("readRedirects: " + splits[0] + " -> " + target);
				redirects.put(splits[0], target);
			} else {
				System.out.println("Warning: Redirect line contains more/less than 2 columns: " + line);
			}
		}
		in.close();
	}

	private static final String PREFIX = "/Volumes/G-DRIVE/Masterarbeit/wiki/";
//	private static final String PREFIX = "/Users/jsimon/No-Backup/wiki/";

	public static void main(String[] args) throws IOException {
		WikiLinkStatistics s = new WikiLinkStatistics();
		s.run();
	}

	private CountingInputStream countingIn;
	private BufferedReader inReader;
	private long fileSize;
	Set<String> pageTitles = new HashSet<String>();
	Map<String, String> redirects = new HashMap<String, String>();
	Map<String, Integer> resourceInlinkCounts = new HashMap<String, Integer>();
	Map<String, Integer> resourceSurfaceFormCounts = new HashMap<String, Integer>();
	int lastProgress = 0;
	
	private void processRedirects() throws IOException {
		Map<String, Integer> resourceInRedirectCounts = new HashMap<String, Integer>();
		for (Entry<String, String> redirect : redirects.entrySet()) {
			String to = redirect.getValue();
			if (to.contains("#")) {
				to = to.substring(0, to.indexOf('#'));
			}
			// Ignore non-existing pages
			if (pageTitles.contains(to)) {
				if (!resourceInRedirectCounts.containsKey(to)) {
					resourceInRedirectCounts.put(to, 0);
				}
				resourceInRedirectCounts.put(to, resourceInRedirectCounts.get(to) + 1);
			}
		}
		int nonRedirectPageCount = pageTitles.size() - redirects.size();
		MapHelper.writeMap(createCountStat(resourceInRedirectCounts, nonRedirectPageCount), PREFIX + "enwiki-stat-inredirects.txt");
	}

	public void run () throws IOException {
		String in = PREFIX + "enwiki-lex-filtered.txt";
		countingIn = new CountingInputStream(new FileInputStream(in));
		inReader = new BufferedReader(new InputStreamReader(countingIn, "UTF-8"));
		File file = new File(in);
		fileSize = file.length();

		readPageTitles(PREFIX + "enwiki-lex-filtered.txt.pages");
		readRedirects(PREFIX + "enwiki-lex-filtered.txt.redirects");
		int nonRedirectPageCount = pageTitles.size() - redirects.size();
		processRedirects();
		System.out.println("# Resources in article namespace (ns 0): " + pageTitles.size());
		System.out.println("# Redirects in article namespace (ns 0): " + redirects.size());

		String line;
		while((line = inReader.readLine()) != null) {
			processLine(line);
		}
		
		MapHelper.writeMap(resourceSurfaceFormCounts, PREFIX + "enwiki-stat-surfaceforms.txt");
		Map<String, Integer> surfaceFormsPerResource = countSurfaceFormsPerResource();
		MapHelper.writeMap(createCountStat(surfaceFormsPerResource, nonRedirectPageCount), PREFIX + "enwiki-stat-surfaceforms-per-resource.txt");

		System.out.println("Results:");
		System.out.println("# Resources in article namespace (ns 0): " + pageTitles.size());
		System.out.println("# Linked Resources: " + resourceInlinkCounts.size());
		System.out.println("# Redirect-linked Resources: " + redirects.size());

		MapHelper.writeMap(createCountStat(resourceInlinkCounts, nonRedirectPageCount), PREFIX + "enwiki-stat-inlinks.txt");

		inReader.close();
	}
	
	public Map<String, Integer> countSurfaceFormsPerResource() {
		Map<String, Integer> counts = new HashMap<String, Integer>();
		for(Entry<String, Integer> entry : resourceSurfaceFormCounts.entrySet()) {
			String key = entry.getKey();
			int resourceEndIndex = key.indexOf('\t');
			String resource = key.substring(0, resourceEndIndex);
			increaseCount(counts, resource, entry.getValue());
		}
		return counts;
	}

	/**
	 * Processes a line in the format
	 * "link text" \t link_target \t Sentence with the <head>link text</head>
	 * @param line 
	 */
	public void processLine(String line) {
		long bytesRead = countingIn.getByteCount();
		double percent = 100.0 * (double)bytesRead / (double)fileSize;
		// show progress whenever the first decimal place after comma changes
		int progress = (int)(percent * 10);
		if (progress != lastProgress) {
			System.out.printf("Processed %.1f%%\n", percent);
			lastProgress = progress;
		}

		String[] lineSplits = line.split("\t");
		if (lineSplits.length == 3) {
			String linkText = lineSplits[0];
			String linkTarget = lineSplits[1];
			String resource = WikiUtil.getLinkedResource(redirects, linkTarget);
			// Ignore non-existing pages
			if (pageTitles.contains(resource)) {
				increaseCount(resourceSurfaceFormCounts, resource + "\t" + linkText, 1);
				increaseCount(resourceInlinkCounts, resource, 1);
			}
		}
	}
	
	private static <T> void increaseCount(Map<T, Integer> map, T key, int increaseBy) {
		Integer count = map.get(key);
		if (count == null) {
			count = 0;
		}
		map.put(key, count + increaseBy);
	}


	/**
	 * Takes a map of the form
	 * "a" -> 12
	 * "b" -> 271
	 * ...
	 * to create a statistic in the form
	 * 1 -> # entries with value 1
	 * 2 -> # entries with value 2
	 * ...
	 * 
	 * @param map
	 * @param nonRedirectPageCount
	 * @return
	 */
	public static Map<Integer, Integer> createCountStat(Map<String, Integer> map, int nonRedirectPageCount)
	{
		Map<Integer, Integer> countMap = new TreeMap<Integer, Integer>();
		int linkedPageCount = 0;
		for (Entry<String, Integer> entry : map.entrySet()) {
			linkedPageCount++;
			int count = entry.getValue();
			if (!countMap.containsKey(count)) {
				countMap.put(count, 0);
			}
			countMap.put(count, countMap.get(count) + 1);
		}

		countMap.put(0, nonRedirectPageCount - linkedPageCount);

		return countMap;
	}
}
