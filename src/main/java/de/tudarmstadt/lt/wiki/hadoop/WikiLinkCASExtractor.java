package de.tudarmstadt.lt.wiki.hadoop;

import java.util.InputMismatchException;

import org.apache.hadoop.io.Text;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.jcas.JCas;

import de.tudarmstadt.lt.wiki.uima.type.WikiLink;
import de.tudarmstadt.ukp.dkpro.bigdata.io.hadoop.MultiLineText2CASInputFormat.AnnotationExtractor;
import de.tudarmstadt.ukp.dkpro.bigdata.io.hadoop.MultiLineText2CASInputFormat.DocumentTextExtractor;

public class WikiLinkCASExtractor implements DocumentTextExtractor, AnnotationExtractor {

	public void extractAnnotations(Text key, Text value, CAS cas) {
		extractAnnotations(value.toString(), cas);
	}

	public Text extractDocumentText(Text key, Text value) {
		return new Text(extractDocumentText(value.toString()));
	}

	public void extractAnnotations(String doc, CAS cas) {
		JCas jCas;
		try {
			jCas = cas.getJCas();
		} catch (CASException e) {
			e.printStackTrace();
			return;
		}
		int offset = 0;
		String[] lines = doc.split("\n");
		for (String line : lines) {
			String cols[] = line.split("\t");
			if (cols.length != 2) {
//				System.err.println("Malformatted sentence-links line: " + line);
				continue;
			}
			String sentence = cols[0];
			String linkStrings[] = cols[1].split("  ");
			for (String linkStr : linkStrings) {
				try {
					WikiLink link = new WikiLink(jCas);
					// remove word itself from link (e.g. in "car@@Automobile@18:21")
					linkStr = linkStr.replaceAll(".*@@", "");
					String[] linkStrParts = linkStr.split("@");
					String[] beginEnd = linkStrParts[1].split(":");
					String resource = linkStrParts[0];
					link.setResource(resource);
					int begin = Integer.parseInt(beginEnd[0]);
					int end = Integer.parseInt(beginEnd[1]);
					link.setBegin(begin + offset);
					link.setEnd(end + offset);
					link.addToIndexes();
				} catch (InputMismatchException e) {
//					System.err.println("Malformatted link column: " + links);
					break;
				}
			}
			offset += sentence.length() + 1; // +1 due to \n character
		}
	}

	public String extractDocumentText(String doc) {
		String[] lines = doc.split("\n");
		StringBuilder documentText = new StringBuilder();
		for (String line : lines) {
			String[] cols = line.split("\t");
			if (cols.length != 2) {
//				System.err.println("Malformatted sentence-links line: " + line);
				continue;
			}
			documentText.append(cols[0]);
			documentText.append("\n");
		}
		return documentText.toString();
	}

}
