package de.tudarmstadt.lt.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

import org.apache.commons.lang.StringUtils;

public class WikiLinkFilter {
	public static void main(String[] args) throws IOException {
		if (args.length != 3) {
			System.out.println("Usage: SurfaceFormDictionary <wiki-link-file> <word-file> <out>");
			return;
		}
		String in = args[0];
		String wordFile = args[1];
		String out = args[2];

		Set<String> words = MapHelper.readSetFromFile(wordFile);

		BufferedReader reader = new BufferedReader(new MonitoredFileReader(in));
		BufferedWriter writer = FileHelper.createBufferedGzipWriter(out);
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
			List<String> linksToWrite = new LinkedList<String>();
			while (s.hasNext()) {
				try {
					String link = s.next();
					int atPos = link.lastIndexOf('@');
					String beginEnd[] = link.substring(atPos + 1).split(":");
					int begin = Integer.parseInt(beginEnd[0]);
					int end = Integer.parseInt(beginEnd[1]);
					String surfaceForm = sentence.substring(begin, end);
					if (words.contains(surfaceForm)) {
						linksToWrite.add(link);
					}
				} catch (Exception e) {
					System.err.println("Malformatted link column: " + links);
					break;
				}
			}
			if (!linksToWrite.isEmpty()) {
				writer.write(sentence);
				writer.write("\t");
				writer.write(StringUtils.join(linksToWrite, "  "));
				writer.write("\n");
			}
			s.close();
		}
		reader.close();
		writer.close();
	}
}
