package wiki;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.regex.Pattern;

import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.util.Span;
import de.tudarmstadt.ukp.wikipedia.api.WikiConstants.Language;
import de.tudarmstadt.ukp.wikipedia.parser.Link;
import de.tudarmstadt.ukp.wikipedia.parser.Paragraph;
import de.tudarmstadt.ukp.wikipedia.parser.ParsedPage;
import de.tudarmstadt.ukp.wikipedia.parser.mediawiki.MediaWikiParser;
import de.tudarmstadt.ukp.wikipedia.parser.mediawiki.MediaWikiParserFactory;


public class WikiXmlProcessorThread extends Thread {
	private SentenceDetectorME sentenceDetector;
	private MediaWikiParser parser;
	private WikiXmlProcessor p;

	public WikiXmlProcessorThread(WikiXmlProcessor p) {
		this.p = p;

		MediaWikiParserFactory pf = new MediaWikiParserFactory(Language.english);
		pf.addDeleteTemplate("infobox");
		parser = pf.createParser();
		InputStream modelIn = null;
		try {
			// Loading sentence detection model
			modelIn = getClass().getResourceAsStream("/en-sent.bin");
			final SentenceModel sentenceModel = new SentenceModel(modelIn);
			modelIn.close();

			sentenceDetector = new SentenceDetectorME(sentenceModel);

		} catch (final IOException ioe) {
			ioe.printStackTrace();
		} finally {
			if (modelIn != null) {
				try {
					modelIn.close();
				} catch (final IOException e) {} // oh well!
			}
		}
	}

	public void run() {
		while (true) {
			// Notify queue we're ready to process the next element
			synchronized(p.queue) {
				p.queue.notify();
			}
			
			String content;
			try {
				content = p.queue.take();
				
				if (content == p.STOP) {
					synchronized(p.queue) {
						// System.out.println("Thread stopped by STOP queue element.");
						p.runningThreads--;
						p.queue.notify();
						return;
					}
				}
			} catch (InterruptedException e) {
				System.out.println("Processor thread interrupted.");
				e.printStackTrace();
				p.runningThreads--;
				return;
			}
			// System.out.println(this + " took from queue content of length " + content.length());
			Pattern refRemovePattern = Pattern.compile("<ref([^<]*?)/>", Pattern.DOTALL);
			content = refRemovePattern.matcher(content).replaceAll("");
			refRemovePattern = Pattern.compile("<ref(.*?)>(.*?)</ref>", Pattern.DOTALL);
			content = refRemovePattern.matcher(content).replaceAll("");
			// Pattern refTemplatePattern = Pattern.compile("\\{\\{(.*?)\\}\\}", Pattern.DOTALL);
			// content = refTemplatePattern.matcher(content).replaceAll("");
			ParsedPage pp = parser.parse(content);

			if (pp == null || pp.getParagraphs() == null) {
				continue;
			}

			for (Paragraph p : pp.getParagraphs()){
				String pText = p.getText();
				Span[] sentenceSpans = sentenceDetector.sentPosDetect(p.getText());
				int offset = 0;
				for (Link link : p.getLinks(Link.type.INTERNAL)) {
					de.tudarmstadt.ukp.wikipedia.parser.Span span = link.getPos();
					int start = span.getStart() + offset;
					int end = span.getEnd() + offset;
					int spanLength = end - start;
					if (spanLength > 0) {
						try {
							addDataLine(pText, sentenceSpans, link);
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				}
			}
		}
	}

	private void addDataLine(String paragraph, Span[] paragraphSentences, Link link) throws IOException {
		int linkStart = link.getPos().getStart();
		int linkEnd = link.getPos().getEnd();
		StringBuilder sentence = null;
		for (Span span : paragraphSentences) {
			// paragraphSentences are ordered with increasing index, thus only need to check span.getEnd()
			if (linkStart < span.getEnd()) {
				sentence = new StringBuilder(paragraph.substring(span.getStart(), span.getEnd()));
				// for "foo.  [[bar]]." (double-space before new sentence with link as first word),
				// linkStart is one index before sentence start, i.e. it would be -1 when
				// span.getStart() is subtracted, thus Math.max(..., 0)
				linkStart -= span.getStart();
				linkEnd -= span.getStart();
				if (linkStart < 0) {
					linkStart = 0;
				}
				if (linkEnd < 0) {
					linkEnd = 0;
				}
				break;
			}
		}
		if (sentence != null) {
			String replaceText = "<head>" + link.getText() + "</head>";
			sentence = sentence.replace(linkStart, linkEnd, replaceText);
			synchronized(p.outputWriter) {
				p.outputWriter.write(link.getText() + "\t" + link.getTarget() + "\t" + sentence + "\n");
			}
		} else {
			System.err.println("Error: sentence == null:");
			System.err.println("Paragraph: " + paragraph);
			System.err.println("Paragraph sentences: " + Arrays.asList(paragraphSentences));
			System.err.println("Link text: " + link.getText());
			System.err.println("Link pos: " + link.getPos());
		}
	}
}
