package de.tudarmstadt.lt.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

public class FileHelper {
	public static BufferedWriter createBufferedWriter(String fileName) throws IOException {
		return new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileName), "UTF-8"));
	}

	public static BufferedReader createBufferedReader(String fileName) throws IOException {
		return new BufferedReader(new InputStreamReader(new FileInputStream(fileName), "UTF-8"));
	}
}
