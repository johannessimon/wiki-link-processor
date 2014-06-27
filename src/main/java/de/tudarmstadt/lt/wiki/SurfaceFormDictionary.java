package de.tudarmstadt.lt.wiki;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.Set;

import org.apache.commons.lang.StringUtils;

import de.tudarmstadt.lt.util.MapHelper;
import de.tudarmstadt.lt.util.MonitoredFileReader;
import de.tudarmstadt.lt.util.WikiUtil;

public class SurfaceFormDictionary {
	public static void main(String[] args) throws IOException {
		if (args.length != 3) {
			System.out.println("Usage: SurfaceFormDictionary <wiki-link-file> <redirect-file> <out>");
			return;
		}
		String in = args[0];
		String redirectFile = args[1];
		String out = args[2];

		Map<String, String> redirects = MapHelper.readMapFromFile(redirectFile, "\t");

		BufferedReader reader = new BufferedReader(new MonitoredFileReader(in));
		Map<String, Set<String>> surfaceForm2Resources = new HashMap<String, Set<String>>();
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
			while (s.hasNext()) {
				try {
					String link = s.next();
					int atPos = link.lastIndexOf('@');
					String resource = WikiUtil.getLinkedResource(redirects, link.substring(0, atPos));
					String beginEnd[] = link.substring(atPos + 1).split(":");
					int begin = Integer.parseInt(beginEnd[0]);
					int end = Integer.parseInt(beginEnd[1]);
					String surfaceForm = sentence.substring(begin, end);
					Set<String> resources = surfaceForm2Resources.get(surfaceForm);
					if (resources == null) {
						resources = new HashSet<String>();
						surfaceForm2Resources.put(surfaceForm, resources);
					}
					resources.add(resource);
				} catch (Exception e) {
					System.err.println("Malformatted link column: " + links);
					break;
				}
			}
			s.close();
		}
		reader.close();
		
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(out), "UTF-8"));
		
		for (Entry<String, Set<String>> surfaceForm : surfaceForm2Resources.entrySet()) {
			writer.write(surfaceForm.getKey() + "\t");
			writer.write(StringUtils.join(surfaceForm.getValue(), "  "));
			writer.write("\n");
		}
		writer.close();
	}
}
