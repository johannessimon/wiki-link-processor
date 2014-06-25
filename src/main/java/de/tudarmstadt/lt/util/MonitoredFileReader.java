package de.tudarmstadt.lt.util;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;

import org.apache.commons.io.input.CountingInputStream;

public class MonitoredFileReader extends Reader {
	private CountingInputStream countingIn;
	private Reader inReader;
	private long fileSize;
	private File file;
	private double reportProgressAfter;
	
	private double lastProgress = 0.0;
	
	public MonitoredFileReader(String fileName, double reportProgressAfter) throws IOException {
		countingIn = new CountingInputStream(new FileInputStream(fileName));
		inReader = new InputStreamReader(countingIn, "UTF-8");
		file = new File(fileName);
		fileSize = file.length();
		this.reportProgressAfter = reportProgressAfter;
		System.out.println("[" + file.getName() + "] Starting to read... (" + fileSize + " bytes)");
	}
	
	public MonitoredFileReader(String fileName) throws IOException {
		this(fileName, 0.01);
	}

	@Override
	public int read(char[] cbuf, int off, int len) throws IOException {
		int res = inReader.read(cbuf, off, len);
		
		double progress = (double)countingIn.getByteCount() / (double)fileSize;
		if (progress - lastProgress >= reportProgressAfter) {
			System.out.printf("[%s] Processed %.2f%%\n", file.getName(), progress * 100.0);
			lastProgress = progress;
		}
		
		return res;
	}

	@Override
	public void close() throws IOException {
		inReader.close();
		countingIn.close();
	}
}
