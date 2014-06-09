package wiki;
import java.io.IOException;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import util.WikiHelper;


public class WikiXmlContentHandler extends DefaultHandler {
	String pageTitle;
	boolean isRedirect = false;
	boolean skipPage = false;
	WikiXmlProcessor p;
	StringBuilder contentBuilder = null;

	public WikiXmlContentHandler(WikiXmlProcessor p) {
		this.p = p;
	}

	@Override
	public void startElement(String uri, String localName, String qName, Attributes attributes) {
		if (localName.equals("page")) {
			isRedirect = false;
		} else if(localName.equals("title")) {
			contentBuilder = new StringBuilder();
		} else if(localName.equals("ns")) {
			contentBuilder = new StringBuilder();
		} else if (localName.equals("text")) {
			if (!skipPage && !isRedirect) {
				contentBuilder = new StringBuilder();
			}
		} else if (localName.equals("redirect")) {
			isRedirect = true;
			String target = WikiHelper.formatResourceName(attributes.getValue("title"));
			if (target != null) {
				p.addRedirect(pageTitle, target);
			}
		}
	}

	@Override
	public void endElement(String uri, String localName, String qName) {
		if (localName.equals("text") && contentBuilder != null) {
			String content = contentBuilder.toString();
			try {
				p.processPage(pageTitle, content);
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else if (localName.equals("title")) {
			pageTitle = WikiHelper.formatResourceName(contentBuilder.toString());
		} else if (localName.equals("ns")) {
			String content = contentBuilder.toString();
			skipPage = !content.equals("0");
			if (!skipPage) {
				p.addPage(pageTitle);
			}
//			System.out.println(content);
		}
		contentBuilder = null;
	}

	public void characters (char ch[], int start, int length) throws SAXException
	{
		if (contentBuilder != null) {
			contentBuilder.append(ch, start, length);
		}
	}
}