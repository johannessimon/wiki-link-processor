package de.tudarmstadt.lt.util;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

public class FileHelper {
	public static BufferedWriter createBufferedWriter(String fileName) throws IOException {
		return new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileName), "UTF-8"));
	}
}
