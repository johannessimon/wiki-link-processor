package test;

import java.io.IOException;

import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;

public class HadoopWikiXmlProcessorMap extends Mapper<LongWritable, Text, Text, Text> {
	/*private MultipleOutputs<Text, Text> mos;
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
			int offset = 0;
			
			String pText = p.getText();
			Span[] sentenceSpans = sentenceDetector.sentPosDetect(pText);
			
			try {
				for (Span span : sentenceSpans) {
					String sentence = pText.substring(span.getStart(), span.getEnd());
					mos.write("sentences", new Text(sentence), NullWritable.get());
				}
				for (Link link : p.getLinks(Link.type.INTERNAL)) {
					de.tudarmstadt.ukp.wikipedia.parser.Span span = link.getPos();
					int start = span.getStart() + offset;
					int end = span.getEnd() + offset;
					int spanLength = end - start;
					if (spanLength > 0) {
						handleParagraph(p, sentenceSpans, link);
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	
	private void handleParagraph(Paragraph p, Span[] sentenceSpans, Link link) throws IOException, InterruptedException {
		String pText = p.getText();
		int linkStart = link.getPos().getStart();
		int linkEnd = link.getPos().getEnd();
		StringBuilder sentence = null;
		
		for (Span span : sentenceSpans) {
			// sentenceSpans are ordered with increasing index, thus only need to check span.getEnd()
			if (linkStart < span.getEnd()) {
				sentence = new StringBuilder(pText.substring(span.getStart(), span.getEnd()));
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
			String target = formatResourceName(link.getTarget());
			mos.write("links", link.getText(), target + "\t" + sentence);
		} else {
			System.err.println("Error: sentence == null:");
			System.err.println("Paragraph: " + p);
			System.err.println("Link text: " + link.getText());
			System.err.println("Link pos: " + link.getPos());
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
		return resource.replace(' ', '_');
	}
	*/
	
	@Override
	public void map(LongWritable key, Text value, Context context)
		throws IOException, InterruptedException {
/*		String xml = value.toString();
//		System.out.println("foo " + key);
		
		try {
			InputSource source = new InputSource(new StringReader(xml));
			Document doc = documentBuilder.parse(source);
			
			Object ns = xPath.evaluate("/page/ns", doc, XPathConstants.NODE);
			if (ns != null) {
				String nsText = ((Node)ns).getTextContent();
				if (nsText.equals("0")) {
					mos.write("xml", value, NullWritable.get());
				}
			}*/
			/*
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
			}*/
			
		/*} catch (XPathExpressionException e) {
			e.printStackTrace();
		} catch (SAXException e) {
			e.printStackTrace();
		}*/
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