package de.tudarmstadt.lt.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.util.HashSet;
import java.util.Set;

/**
 * Program similar to Unix' "shuf" program. Time consumed to print
 * n random lines is only proportional to n, not the file size.
 */
public class Shuf {
	public static void main(String[] args) throws IOException {
		if (args.length < 2 && args.length > 3) {
			System.out.println("Usage: shuf <file> <num-lines> [<output-file>]");
			return;
		}
		Set<Long> lines = new HashSet<Long>();
		RandomAccessFile file = new RandomAccessFile(args[0], "r");
		BufferedWriter writer;
		if (args.length == 3) {
			writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(args[2]), "UTF-8"));
		} else {
			writer = new BufferedWriter(new OutputStreamWriter(System.out, "UTF-8"));
		}
		int numLines = Integer.parseInt(args[1]);
		
		long fileSize = file.length();
		for (int i = 0; i < numLines; i++) {
			long linePos = -1L;
			String line = null;
			while (line == null) {
				long pos = (long)(Math.random() * fileSize);
				file.seek(pos);
				line = file.readLine();
				if (line != null) {
					linePos = file.getFilePointer();
					line = file.readLine();
				}
			}
			lines.add(linePos);

			InputStream is = new FileInputStream(args[0]);
			is.skip(linePos);
			BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
			line = reader.readLine();
			reader.close();
			
			writer.write(line + "\n");
		}
		
		writer.close();
		file.close();
	}
}
