package de.tudarmstadt.lt.wiki;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import de.tudarmstadt.lt.util.FileUtil;
import de.tudarmstadt.lt.util.MapUtil;
import de.tudarmstadt.lt.util.MonitoredFileReader;

public class MidFrequentWordFilter {
	static Logger log = Logger.getLogger("de.tudarmstadt.lt.wiki");
	
	public static void main(String[] args) throws IOException {
		if (args.length != 6) {
			log.info("Usage: WordListNounFilter <word-file> <sample-file> <top-cutoff> <min-freq> <out> <word-out>");
			return;
		}
		String wordFile = args[0];
		String sampleFile = args[1];
		int topCutoff = Integer.parseInt(args[2]);
		int minFreq = Integer.parseInt(args[3]);
		String out = args[4];
		String wordOut = args[5];

		Map<String, Integer> words = new HashMap<String, Integer>();
		BufferedReader reader = new BufferedReader(new MonitoredFileReader(wordFile));
		String line;
		while ((line = reader.readLine()) != null) {
			try {
				String cols[] = line.split("\t");
				int freq = Integer.parseInt(cols[1]);
				if (freq >= minFreq) {
					words.put(cols[0], freq);
				}
			} catch (Exception e) {
				log.warn("Malformatted word-count line: " + line, e);
			}
		}
		int numCutoffWords = Math.min(topCutoff, words.size());
		List<String> cutoffWords = MapUtil.sortMapKeysByValue(words)
				                          .subList(0, numCutoffWords);
		for (String cutoffWord : cutoffWords) {
			words.remove(cutoffWord);
		}
		reader.close();

		BufferedWriter writer = FileUtil.createWriter(out);
		reader = new BufferedReader(new MonitoredFileReader(sampleFile));
		while ((line = reader.readLine()) != null) {
			String lineParts[] = line.split("\t");
			String word = lineParts[0].split("@@")[0];
			if (words.containsKey(word)) {
				writer.write(line);
				writer.write("\n");
			}
		}
		writer.close();
		

		MapUtil.writeMap(words, wordOut);
	}
}
