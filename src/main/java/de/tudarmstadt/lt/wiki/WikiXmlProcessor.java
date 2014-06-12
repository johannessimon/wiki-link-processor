package de.tudarmstadt.lt.wiki;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.commons.io.input.CountingInputStream;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;


public class WikiXmlProcessor {
	//	private SentenceDetectorME sentenceDetector;
	//	private MediaWikiParser parser;
	Writer outputWriter;
	int numThreads;
	BlockingQueue<String> queue;
	private Writer redirectWriter;
	private Writer pageWriter;
	private CountingInputStream xmlIn;
	private long xmlInBytesTotal;
	private int pageCount = 0;
	private int redirectCount = 0;
	int runningThreads = 0;

	String STOP = "<KILL>";

	public WikiXmlProcessor() {
		this(1);
	}

	public WikiXmlProcessor(int numThreads) {
		this.numThreads = numThreads;
		// reserve 1 spot for KILL element
		queue = new ArrayBlockingQueue<String>(numThreads + 1);
	}

	public static void main(String[] args) throws Exception {
		if (args.length != 3) {
			System.out.println("Usage: <articles xml (bzip2-compressed)> <output dir> <num threads>");
		}
		String in = args[0];
		String outDir = args[1] + "/";
		int numThreads = Integer.parseInt(args[2]);

		WikiXmlProcessor p = new WikiXmlProcessor(numThreads);
		p.processXml(in, outDir + "enwiki-lex-filtered.txt");
	}

	public void processXml(String xmlFile, String outputFile) throws IOException, SAXException, CompressorException {
		// Ensure output directory exists
		new File(outputFile).getParentFile().mkdirs();

		xmlIn = new CountingInputStream(new BufferedInputStream(new FileInputStream(xmlFile)));
		xmlInBytesTotal = new File(xmlFile).length();
		Reader xmlFileReader = new BufferedReader(new InputStreamReader(new CompressorStreamFactory().createCompressorInputStream(xmlIn)));
		XMLReader xmlReader = XMLReaderFactory.createXMLReader();
		InputSource inputSource = new InputSource(xmlFileReader);
		xmlReader.setContentHandler(new WikiXmlContentHandler(this));

		outputWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFile), "UTF-8"));
		pageWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFile + ".pages"), "UTF-8"));
		redirectWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFile + ".redirects"), "UTF-8"));

		for (int i = 0; i < numThreads; i++) {
			System.out.println("Initializing thread " + i);
			Thread p = new WikiXmlProcessorThread(this);
			p.start();
			runningThreads++;
		}
		xmlReader.parse(inputSource);

		waitUntilQueueEmpty();
		stopRemainingThreads();

		outputWriter.close();
		pageWriter.close();
		redirectWriter.close();

		System.out.println("# pages: " + pageCount);
		System.out.println("# redirects: " + redirectCount);
	}

	private void waitUntilQueueEmpty() {
		while (!queue.isEmpty()) {
			synchronized(queue) {
				try {
					queue.wait();
				} catch (InterruptedException e) {
					System.out.println("Interrupted while waiting for queue to become empty.");
					e.printStackTrace();
				}
			}
		}
	}

	private void stopRemainingThreads() {
		while (runningThreads > 0) {
			// Kill threads waiting for queue input by STOP element
			try {
				synchronized(queue) {
					queue.put(STOP);
					queue.wait();
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	public void addPage(String title) {
		try {
			pageCount++;
			pageWriter.write(title + "\n");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	String _msgCheck = "";
	long _time = 0;
	long _pageCount;
	public void processPage(String title, String content) throws IOException {
		long bytesRead = xmlIn.getByteCount();
		double percent = 100.0 * (double)bytesRead / (double)xmlInBytesTotal;
		double pagesPerSecond = 0;
		String msgCheck = String.format("%.1f%%", percent);
		if (!msgCheck.equals(_msgCheck)) {
			long time = System.nanoTime();
			if (_time > 0) {
				pagesPerSecond = (double)(pageCount - _pageCount) * 1000000000.0 / (double)(time - _time);
			}
			_time = time;
			_pageCount = pageCount;
			String msg = String.format("Process: %d/%d bytes read (%.3f%%, %d pages, %d redirects, %.1f pages/s)", bytesRead, xmlInBytesTotal, percent, pageCount, redirectCount, pagesPerSecond);
			System.out.println(msg);
			_msgCheck = msgCheck;
		}

		try {
			// System.out.println("Put content on queue of length " + content.length());
			queue.put(content);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	public void addRedirect(String from, String to) {
		redirectCount++;
		try {
			redirectWriter.write(from + "\t" + to + "\n");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
