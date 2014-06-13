package de.tudarmstadt.lt.wiki;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.util.Span;
import de.tudarmstadt.ukp.wikipedia.api.WikiConstants.Language;
import de.tudarmstadt.ukp.wikipedia.parser.Link;
import de.tudarmstadt.ukp.wikipedia.parser.Paragraph;
import de.tudarmstadt.ukp.wikipedia.parser.ParsedPage;
import de.tudarmstadt.ukp.wikipedia.parser.mediawiki.MediaWikiParser;
import de.tudarmstadt.ukp.wikipedia.parser.mediawiki.MediaWikiParserFactory;

public class WikiProcessor {
	private MediaWikiParser parser;
	private SentenceDetectorME sentenceDetector;
	private XPath xPath;
	private DocumentBuilder documentBuilder;
	
	public WikiProcessor() {
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
	
	public String getLinkedResource(String target, Map<String, String> redirects) {
		int startIndex = target.indexOf('#');
		String subsection = "";
		if (startIndex >= 0) {
			subsection = target.substring(startIndex);
			target = target.substring(0, startIndex);
		}
		String redirectedTarget = redirects.get(target);
		if (redirectedTarget != null)
			return target = redirectedTarget;
		target += subsection;
		return target;
	}
	
	public class WikiXmlRecord {
		public String text;
		public String title;
		public String redirect;
		public String ns;
	}
	
	public WikiXmlRecord parseXml(String xml) {
		WikiXmlRecord res = new WikiXmlRecord();
		try {
			InputSource source = new InputSource(new StringReader(xml));
			Document doc = documentBuilder.parse(source);
			
			Object ns = xPath.evaluate("/page/ns", doc, XPathConstants.NODE);
			if (ns != null) {
				String nsText = ((Node)ns).getTextContent();
				if (!nsText.equals("0")) {
					return null;
				}
			}
			Object title = xPath.evaluate("/page/title", doc, XPathConstants.NODE);
			Object text = xPath.evaluate("/page/revision/text", doc, XPathConstants.NODE);
			Object redirect = xPath.evaluate("/page/redirect/@title", doc, XPathConstants.NODE);
			
			if (title != null) {
				String titleText = ((Node)title).getTextContent();
				res.title = titleText;
			}
			if (redirect != null) {
				String redirectTitle = ((Node)redirect).getTextContent();
				res.redirect = redirectTitle;
			}
			if (text != null) {
				String textText = ((Node)text).getTextContent();
				res.text = textText;
			}
			
		} catch (XPathExpressionException e) {
			e.printStackTrace();
		} catch (SAXException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return res;
	}
	
	private String removeAllTags(String xml, String tagName) {
		Pattern refRemovePattern = Pattern.compile("<" + tagName + "([^<]*?)/>", Pattern.DOTALL);
		xml = refRemovePattern.matcher(xml).replaceAll("");
		refRemovePattern = Pattern.compile("<" + tagName + "[^<]*?>([^<]*?)</" + tagName + ">", Pattern.DOTALL);
		xml = refRemovePattern.matcher(xml).replaceAll("");
		return xml;
	}
	
	private String preprocessXml(String xml) {
		// Replace non-line-breaking characters etc. as described in
		// http://en.wikipedia.org/wiki/Wikipedia:Line-break_handling#Preventing_and_controlling_word_wraps
		xml = xml.replaceAll("&nbsp;", " "); // non-breaking space (nbsp)
		xml = xml.replaceAll("&#8209;", "-"); // hyphen
		// {{nowrap|<text>}} -> <text>
		Pattern nowrapTemplatePattern = Pattern.compile("\\{\\{nowrap\\|([^\\{]*?)\\}\\}", Pattern.DOTALL);
		xml = nowrapTemplatePattern.matcher(xml).replaceAll("$1");
		
		// List taken from http://en.wikipedia.org/wiki/Help:HTML_in_wikitext#Parser_and_extension_tags
		String[] tagsToRemove = new String[] { "gallery", "nowiki", "pre", "categorytree", "charinsert",
				"hiero", "imagemap", "inputbox", "math", "poem", "ref", "references", "score",
				"syntaxhighlight", "source", "timeline" };
		for (String tag : tagsToRemove) {
			xml = removeAllTags(xml, tag);
		}
		// in the following regex, "[^\\{]" matches all charactars but '{' to ensure a minimum
		// match spanning only one pattern at a time (and not nested ones)
		Pattern templatePattern = Pattern.compile("\\{\\{([^\\{]*?)\\}\\}", Pattern.DOTALL);
		String newContent;
		// Loop due to possible nested constructs like "{{ a {{ b }} c }}"
		while (!(newContent = templatePattern.matcher(xml).replaceAll("")).equals(xml)) {
			xml = newContent;
		}
		
		return xml;
	}
	
	public void parse(String xml, List<String> sentences, Map<Integer, List<String>> sentenceLinks) {
		xml = preprocessXml(xml);
		ParsedPage pp = parser.parse(xml);
		
		if (pp == null || pp.getParagraphs() == null) {
			return;
		}
		
		for (Paragraph p : pp.getParagraphs()){
			String pText = p.getText();
			// Replace newlines by spaces. This keeps all spans (e.g. of links) intact,
			// as the number of characters does not change anywhere.
			pText = pText.replace('\n', ' ');
			Span[] sentenceSpans = sentenceDetector.sentPosDetect(pText);
		
			for (Span sSpan : sentenceSpans) {
				int sStart = sSpan.getStart();
				int sEnd = sSpan.getEnd();
				String sentence = pText.substring(sStart, sEnd);
				sentences.add(sentence);
				
				if (sentenceLinks != null) {
					List<String> links = new LinkedList<String>();
					for (Link link : p.getLinks(Link.type.INTERNAL)) {
						de.tudarmstadt.ukp.wikipedia.parser.Span lSpan = link.getPos();
						int lStart = lSpan.getStart();
						int lEnd = lSpan.getEnd();
						int spanLength = lEnd - lStart;
						if (spanLength > 0 && lStart >= sStart && lEnd < sEnd) {
							String target = formatResourceName(link.getTarget());
							String linkRef = target + "@" + (lStart - sStart) + ":" + (lEnd - sStart);
							links.add(linkRef);
						}
					}
					if (!links.isEmpty()) {
						sentenceLinks.put(sentences.size() - 1, links);
					}
				}
			}
		}
	}
	
	public String formatResourceName(String resource) {
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
}
