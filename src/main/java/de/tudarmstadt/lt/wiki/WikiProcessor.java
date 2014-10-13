package de.tudarmstadt.lt.wiki;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import de.tudarmstadt.lt.util.MapUtil;
import de.tudarmstadt.lt.util.WikiUtil;
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
		
		refRemovePattern = Pattern.compile("<" + tagName + "([^<]*?)>([^<]*?)</" + tagName + ">", Pattern.DOTALL);
		String newContent;
		// Loop due to possible nested constructs like "{{ a {{ b }} c }}"
		while (!(newContent = refRemovePattern.matcher(xml).replaceAll("")).equals(xml)) {
			xml = newContent;
		}
		return xml;
	}
	
	private String preprocessXml(String xml) {
		// remove HTML comments:
		// the (-{2,3}) is there to match "-->" as well as "--->" (coldfusion comment)
		Pattern commentPattern = Pattern.compile("<!--.*?(-{2,3})>", Pattern.DOTALL);
		xml = commentPattern.matcher(xml).replaceAll("");
		
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
		
		// Remove code blocks
		xml = xml.replaceAll("\\n .*", "");
		
		return xml;
	}
	
	/**
	 * Returns a textual reference of this link relative to its sentence
	 */
	private String getLinkRef(LinkOccurrence lo) {
		String target = WikiUtil.formatResourceName(lo.target);
		int start = lo.start - lo.s.getStart();
		int end = lo.end - lo.s.getStart();
		return target + "@" + start + ":" + end;
	}
	
	/**
	 * Represents a link occurrence within a given span of a paragraph
	 */
	private class LinkOccurrence {
		int start;
		int end;
		String target;
		Span s;
		int sIndex;
		Paragraph p;
		
		public LinkOccurrence(int start, int end, String target, Paragraph p, Span s, int sIndex) {
			this.start = start;
			this.end = end;
			this.target = target;
			this.p = p;
			this.s = s;
			this.sIndex = sIndex;
		}
		
		@Override public String toString() { return target + "@" + start + ":" + end; }

		private WikiProcessor getOuterType() {
			return WikiProcessor.this;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + getOuterType().hashCode();
			result = prime * result + end;
			result = prime * result + ((p == null) ? 0 : p.hashCode());
			result = prime * result + ((s == null) ? 0 : s.hashCode());
			result = prime * result + sIndex;
			result = prime * result + start;
			result = prime * result
					+ ((target == null) ? 0 : target.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			LinkOccurrence other = (LinkOccurrence) obj;
			if (!getOuterType().equals(other.getOuterType()))
				return false;
			if (end != other.end)
				return false;
			if (p == null) {
				if (other.p != null)
					return false;
			} else if (!p.equals(other.p))
				return false;
			if (s == null) {
				if (other.s != null)
					return false;
			} else if (!s.equals(other.s))
				return false;
			if (sIndex != other.sIndex)
				return false;
			if (start != other.start)
				return false;
			if (target == null) {
				if (other.target != null)
					return false;
			} else if (!target.equals(other.target))
				return false;
			return true;
		}
	}
	
	/**
	 * Extracts all explicit links from a paragraph, given its sentence spans
	 */
	private Collection<LinkOccurrence> extractLinks(Paragraph p, Span[] spans) {
		Collection<LinkOccurrence> links = new LinkedList<>();
		for (int i = 0; i < spans.length; i++) {
			Span span = spans[i];
			int sStart = span.getStart();
			int sEnd = span.getEnd();
			for (Link link : p.getLinks(Link.type.INTERNAL)) {
				de.tudarmstadt.ukp.wikipedia.parser.Span lSpan = link.getPos();
				int lStart = lSpan.getStart();
				int lEnd = lSpan.getEnd();
				int spanLength = lEnd - lStart;
				if (spanLength > 0 && lStart >= sStart && lEnd < sEnd) {
					links.add(new LinkOccurrence(lStart, lEnd, link.getTarget(), p, span, i));
				}
			}
		}
		return links;
	}
	
	/**
	 * Finds a word occuring in a given text, excluding all partial matches, i.e. all occurrences
	 * that have at least one more letter appearing directly in front or after it.<br/>
	 * <br/>
	 * <b>Example:</b><br/>
	 * <code>findWord("aba ab xyzab", "ab", 0)</code> finds only the 2nd occurrence of "ab",
	 * as the others are considered to be parts of other words.
	 */
	private int findWord(String text, String word, int startFrom) {
		while (startFrom < text.length()) {
			int start = text.indexOf(word, startFrom);
			if (start < 0) {
				return -1;
			}
			int end = start + word.length();
			boolean hasLettersInFront = start > 0 && 
					Character.isLetter(text.charAt(start - 1));
			boolean hasLettersAfter = end < text.length() - 1 && 
					Character.isLetter(text.charAt(end));
			// Make sure this is not part of another word
			if (!hasLettersInFront && !hasLettersAfter) {
				return start;
			}
			startFrom = end;
		}
		return -1;
	}

	/**
	 * Extracts all links implied by an explicit link (based on the "one sense per discourse"
	 * assumption)
	 */
	private Collection<LinkOccurrence> extractImplicitLinks(LinkOccurrence link, Paragraph p, Span[] spans) {
		Collection<LinkOccurrence> implicitLinks = new LinkedList<>();
		String linkText = link.p.getText().substring(link.start, link.end);
		for (int i = 0; i < spans.length; i++) {
			Span span = spans[i];
			String sentence = p.getText().substring(span.getStart(), span.getEnd());
			int sStart = span.getStart();
			int searchFrom = 0;
			int lStart;
			while ((lStart = findWord(sentence, linkText, searchFrom)) >= 0) {
				int lStartInP = lStart + sStart;
				int lEndInP = lStartInP + linkText.length();
				// Do not add the explicit link itself (which we will of course find again in this
				// way) as implicit link
				implicitLinks.add(new LinkOccurrence(lStartInP, lEndInP, link.target, p, span, i));
				searchFrom = lStart + linkText.length();
			}
		}
		return implicitLinks;
	}
	
	public void parse(String xml,
			          List<String> sentences,
			          Map<Integer, List<String>> sentenceLinks,
			          Map<Integer, List<String>> implicitSentenceLinks) {
		xml = preprocessXml(xml);
		ParsedPage pp = parser.parse(xml);
		if (pp == null || pp.getParagraphs() == null) {
			return;
		}

		Set<LinkOccurrence> links = new HashSet<LinkOccurrence>();
		Map<Paragraph, Span[]> paragraphsWithSentenceSpans = new HashMap<>();
		Map<Paragraph, Integer> paragraphsSentenceOffsets = new HashMap<>();
		int sOffset = 0;
		for (Paragraph p : pp.getParagraphs()) {
			paragraphsSentenceOffsets.put(p, sOffset);
			// Replace newlines by spaces. This keeps all spans (e.g. of links) intact,
			// as the number of characters does not change anywhere.
			String pText = p.getText().replace('\n', ' ');
			Span[] sentenceSpans = sentenceDetector.sentPosDetect(pText);
			paragraphsWithSentenceSpans.put(p, sentenceSpans);
			links.addAll(extractLinks(p, sentenceSpans));
			for (Span sSpan : sentenceSpans) {
				sentences.add(pText.substring(sSpan.getStart(), sSpan.getEnd()));
			}
			sOffset += sentenceSpans.length;
		}

		Set<LinkOccurrence> implicitLinks = new HashSet<LinkOccurrence>();
		for (LinkOccurrence link : links) {
			if (sentenceLinks != null) {
				int sIndex = link.sIndex + paragraphsSentenceOffsets.get(link.p);
				MapUtil.addTo(sentenceLinks, sIndex, getLinkRef(link), LinkedList.class);
			}
			for (Paragraph p : paragraphsWithSentenceSpans.keySet()) {
				Span[] spans = paragraphsWithSentenceSpans.get(p);
				implicitLinks.addAll(extractImplicitLinks(link, p, spans));
			}
		}
		implicitLinks.removeAll(links);
		if (implicitSentenceLinks != null) {
			for (LinkOccurrence link : implicitLinks) {
				int sIndex = link.sIndex + paragraphsSentenceOffsets.get(link.p);
				MapUtil.addTo(implicitSentenceLinks, sIndex, getLinkRef(link), LinkedList.class);
			}
		}
	}
}
