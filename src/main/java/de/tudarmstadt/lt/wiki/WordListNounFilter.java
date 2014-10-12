package de.tudarmstadt.lt.wiki;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;

import de.tudarmstadt.lt.util.FileUtil;
import de.tudarmstadt.lt.util.MonitoredFileReader;

public class WordListNounFilter {
	public static void main(String[] args) throws IOException {
		if (args.length != 4) {
			System.out.println("Usage: WordListNounFilter <word-file> <offset> <num-words> <out>");
			return;
		}
		String wordFile = args[0];
		int offset = Integer.parseInt(args[1]);
		int numWords = Integer.parseInt(args[2]);
		String out = args[3];

		BufferedReader reader = new BufferedReader(new MonitoredFileReader(wordFile));
		BufferedWriter writer = FileUtil.createWriter(out);
		String line;
		int lineNo = 0;
		int wordCount = 0;
		while ((line = reader.readLine()) != null && wordCount < numWords) {
			if (lineNo++ < offset) {
				continue;
			}
			try {
			String cols[] = line.split("\t");
			if (cols.length != 2) {
				System.err.println("Malformatted word-count line: " + line);
				continue;
			}
			String word = cols[0];
			if (Character.isLowerCase(word.charAt(0)) &&
				!word.contains(" ")) {
				int count = Integer.parseInt(cols[1]);
				wordCount++;
				writer.write(word + "\t" + count + "\n");
			}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		reader.close();
		writer.close();
	}
}
