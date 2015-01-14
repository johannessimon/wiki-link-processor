package de.tudarmstadt.lt.wiki;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import de.tudarmstadt.lt.util.FileUtil;
import de.tudarmstadt.lt.util.MonitoredFileReader;

public class WikiLinkRand100Filter {
	public static void main(String[] args) throws IOException {
		if (args.length != 2) {
			System.out.println("Usage: SurfaceFormDictionary <wiki-link-file-randomized> <out>");
			return;
		}
		String in = args[0];
		String out = args[1];

		Map<String, Integer> sentenceCountsPerLemma = new HashMap<String, Integer>();
		BufferedReader reader = new BufferedReader(new MonitoredFileReader(in));
		BufferedWriter writer = FileUtil.createWriter(out);
		String line;
		while ((line = reader.readLine()) != null) {
			String cols[] = line.split("\t");
			if (cols.length != 2) {
				System.err.println("Malformatted sentence-links line: " + line);
				continue;
			}
			String sentence = cols[0];
			String links = cols[1];
			Scanner s = new Scanner(links);
			s.useDelimiter("  ");
			// Use this sentence only for first link
			if (s.hasNext()) {
				try {
					String link = s.next();
					String linkParts[] = link.split("@@");
					String lemma = linkParts[0];

					Integer count = sentenceCountsPerLemma.get(lemma);
					if (count == null) {
						count = 0;
					}
					if (count != 100) {
						writer.write(lemma + "\t" + sentence + "\t" + link + "\n");
						sentenceCountsPerLemma.put(lemma, count + 1);
					}
				} catch (Exception e) {
					System.err.println("Malformatted link column: " + links);
					break;
				}
			}
			s.close();
		}
		reader.close();
		writer.close();
	}
}
