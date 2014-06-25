package de.tudarmstadt.lt.util;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipException;

import org.apache.commons.io.input.CountingInputStream;

public class MonitoredFileReader extends Reader {
	private CountingInputStream countingIn;
	private Reader inReader;
	private long fileSize;
	private File file;
	private double reportProgressAfter;
	
	private double lastProgress = 0.0;
	
	public MonitoredFileReader(String fileName, String encoding, double reportProgressAfter) throws IOException {
		file = new File(fileName);
		fileSize = file.length();
		InputStream in = countingIn = new CountingInputStream(new FileInputStream(file));
		if (fileName.endsWith(".gz")) {
			try {
				@SuppressWarnings("resource")
				InputStream gzIn = new GZIPInputStream(in);
				System.out.println("[" + file.getName() + "] GZipped file detected. Reading using decompressor.");
				in = gzIn;
			} catch (ZipException e) {
				// proceed like nothing happened (gzIn has not been assigned to in)
				System.err.println("[" + file.getName() + "] Warning: Unsuccessfully tried top uncompress file ending with .gz, reading file without decompression.");
			}
		}
		inReader = new InputStreamReader(in, encoding);
		this.reportProgressAfter = reportProgressAfter;
		System.out.println("[" + file.getName() + "] Starting to read... (" + fileSize + " bytes)");
	}
	
	public MonitoredFileReader(String fileName, String encoding) throws IOException {
		this(fileName, encoding, 0.01);
	}
	
	public MonitoredFileReader(String fileName) throws IOException {
		this(fileName, "UTF-8", 0.01);
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
