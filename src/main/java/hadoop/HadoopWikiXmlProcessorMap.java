package hadoop;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.util.Span;

import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.lib.output.MultipleOutputs;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import de.tudarmstadt.ukp.wikipedia.api.WikiConstants.Language;
import de.tudarmstadt.ukp.wikipedia.parser.Link;
import de.tudarmstadt.ukp.wikipedia.parser.Paragraph;
import de.tudarmstadt.ukp.wikipedia.parser.ParsedPage;
import de.tudarmstadt.ukp.wikipedia.parser.mediawiki.MediaWikiParser;
import de.tudarmstadt.ukp.wikipedia.parser.mediawiki.MediaWikiParserFactory;

public class HadoopWikiXmlProcessorMap extends Mapper<LongWritable, Text, Text, Text> {
	private MultipleOutputs<Text, Text> mos;
	private SentenceDetectorME sentenceDetector;
	private MediaWikiParser parser;
	private String currentPage;
	private XPath xPath;
	private DocumentBuilder documentBuilder;
	
	@Override
	public void setup(Context context) {
		mos = new MultipleOutputs<Text, Text>(context);
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
			
			xPath = XPathFactory.newInstance().newXPath();
			documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			
		} catch (final IOException e) {
			e.printStackTrace();
		} catch (ParserConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			if (modelIn != null) {
				try {
					modelIn.close();
				} catch (final IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	@Override
	public void cleanup(Context context) {
		try {
			mos.close();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	private void handleText(String content) {
		Pattern refRemovePattern = Pattern.compile("<ref([^<]*?)/>", Pattern.DOTALL);
		content = refRemovePattern.matcher(content).replaceAll("");
		refRemovePattern = Pattern.compile("<ref(.*?)>(.*?)</ref>", Pattern.DOTALL);
		content = refRemovePattern.matcher(content).replaceAll("");
		// Pattern refTemplatePattern = Pattern.compile("\\{\\{(.*?)\\}\\}", Pattern.DOTALL);
		// content = refTemplatePattern.matcher(content).replaceAll("");
		ParsedPage pp = parser.parse(content);
		
		if (pp == null || pp.getParagraphs() == null) {
			return;
		}
		
		for (Paragraph p : pp.getParagraphs()){
			String pText = p.getText();
			// Replace newlines by spaces. This keeps all spans (e.g. of links) intact,
			// as the number of characters does not change anywhere.
			pText = pText.replace('\n', ' ');
			Span[] sentenceSpans = sentenceDetector.sentPosDetect(pText);
			
			try {
				// Offset in paragraph. In first sentence 0, in second sentence.length(), etc.
//				int pOffset = 0;
				for (Span sSpan : sentenceSpans) {
					int sStart = sSpan.getStart();
					int sEnd = sSpan.getEnd();
					String sentence = pText.substring(sStart, sEnd);
					mos.write("sentences", new Text(sentence), NullWritable.get());
					
					StringBuilder linkRefs = new StringBuilder();
					for (Link link : p.getLinks(Link.type.INTERNAL)) {
						de.tudarmstadt.ukp.wikipedia.parser.Span lSpan = link.getPos();
						int lStart = lSpan.getStart();
						int lEnd = lSpan.getEnd();
						int spanLength = lEnd - lStart;
						if (spanLength > 0 && lStart >= sStart && lEnd < sEnd) {
							String target = formatResourceName(link.getTarget());
							String linkRef = target + "@" + (lStart - sStart) + ":" + (lEnd - sStart);
							if (linkRefs.length() > 0) {
								linkRefs.append(",");
							}
							linkRefs.append(linkRef);
						}
					}
					if (linkRefs.length() > 0) {
						mos.write("links", new Text(sentence), new Text(linkRefs.toString()));
					}
					
//					pOffset += sStart;
				}
			} catch (IOException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	
	private void handleTitle(String pageName) throws IOException, InterruptedException {
		currentPage = formatResourceName(pageName);
		mos.write("pages", new Text(currentPage), NullWritable.get());
	}
	
	private void handleRedirect(String target) throws IOException, InterruptedException {
		mos.write("redirects", new Text(currentPage), new Text(formatResourceName(target)));
	}
	
	private String formatResourceName(String resource) {
		if (resource.length() == 0) {
			System.err.println("WARNING: RESOURCE OF LENGTH 0!");
			Thread.dumpStack();
			return resource;
		}
		// Uppercase first letter
		resource = Character.toUpperCase(resource.charAt(0)) + resource.substring(1);
		resource = resource.replace(' ', '_');
		return resource;
	}

	
	@Override
	public void map(LongWritable key, Text value, Context context)
		throws IOException, InterruptedException {
		String xml = value.toString();
		
		try {
			InputSource source = new InputSource(new StringReader(xml));
			Document doc = documentBuilder.parse(source);
			
			Object ns = xPath.evaluate("/page/ns", doc, XPathConstants.NODE);
			if (ns != null) {
				String nsText = ((Node)ns).getTextContent();
				if (!nsText.equals("0")) {
					return;
				}
			}
			Object title = xPath.evaluate("/page/title", doc, XPathConstants.NODE);
			Object text = xPath.evaluate("/page/revision/text", doc, XPathConstants.NODE);
			Object redirect = xPath.evaluate("/page/redirect/@title", doc, XPathConstants.NODE);
			
			if (title != null) {
				String titleText = ((Node)title).getTextContent();
				handleTitle(titleText);
			}
			if (redirect != null) {
				String redirectTitle = ((Node)redirect).getTextContent();
				handleRedirect(redirectTitle);
			}
			if (text != null) {
				String textText = ((Node)text).getTextContent();
				handleText(textText);
			}
			
		} catch (XPathExpressionException e) {
			e.printStackTrace();
		} catch (SAXException e) {
			e.printStackTrace();
		}
		/*
		for (String xmlLine : xmlLines) {
		xmlLine = xmlLine.trim();
		int beginIndex = xmlLine.indexOf('>') + 1;
		int endIndex = xmlLine.lastIndexOf('<');
		String xmlContent = null;
		if (endIndex > beginIndex) {
		xmlContent = xmlLine.substring(beginIndex, endIndex);
		}
		
		if (xmlLine.startsWith("<text")) {
		handleText(xmlContent);
		} else if (xmlLine.startsWith("<title")) {
		handleTitle(xmlContent);
		} else if (xmlLine.startsWith("<redirect")) {
		String beginText = "title=\"";
		String endText = "\"";
		beginIndex = xmlLine.indexOf(beginText) + beginText.length();
		endIndex = xmlLine.lastIndexOf(endText);
		String target = xmlLine.substring(beginIndex, endIndex);
		handleRedirect(target);
		}
		}
		*/
	}
}