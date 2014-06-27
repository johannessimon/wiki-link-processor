package de.tudarmstadt.lt.wiki;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

import de.tudarmstadt.lt.util.FileHelper;
import de.tudarmstadt.lt.util.MapHelper;
import de.tudarmstadt.lt.util.MonitoredFileReader;
import de.tudarmstadt.lt.util.WikiUtil;


public class RedirectReplacer {
	public static void main(String[] args) throws IOException {
		if (args.length != 3) {
			System.out.println("Usage: <lex-file-in> <redirect-file> <pages-file> <lex-file-out>");
		}
		String inFile = args[0];
		String redirectFile = args[1];
		String pagesFile = args[2];
		String outFile = args[3];
		
		RedirectReplacer rr = new RedirectReplacer();
		rr.run(inFile, redirectFile, pagesFile, outFile);
	}

	Set<String> pages;
	Map<String, String> redirects;
	public void run(String inFile, String redirectFile, String pagesFile, String outFile) throws IOException {
		redirects = MapHelper.readMapFromFile(redirectFile, "\t");
		pages = MapHelper.readSetFromFile(pagesFile);
		BufferedReader inReader = new BufferedReader(new MonitoredFileReader(inFile));
		BufferedWriter outWriter = FileHelper.createBufferedWriter(outFile);
		
		String line;
		while ((line = inReader.readLine()) != null) {
			String[] parts = line.split("\t");
			if (parts.length == 3) {
				String linkText = parts[0];
				String target = parts[1];
				String sentence = parts[2];
				target = WikiUtil.getLinkedResource(redirects, target, true);
				if (pages.contains(target)) {
					outWriter.write(linkText + "\t" + target + "\t" + sentence + "\n");
				}
			}
		}
		
		inReader.close();
		outWriter.close();
	}
}
