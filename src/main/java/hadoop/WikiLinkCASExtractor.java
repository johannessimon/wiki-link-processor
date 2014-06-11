package hadoop;

import java.util.InputMismatchException;
import java.util.Scanner;

import org.apache.hadoop.io.Text;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.jcas.JCas;

import uima.type.WikiLink;
import de.tudarmstadt.ukp.dkpro.bigdata.io.hadoop.Text2CASInputFormat.AnnotationExtractor;
import de.tudarmstadt.ukp.dkpro.bigdata.io.hadoop.Text2CASInputFormat.DocumentTextExtractor;

public class WikiLinkCASExtractor implements DocumentTextExtractor, AnnotationExtractor {

	public void extractAnnotations(Text key, Text value, CAS cas) {
		JCas jCas;
		try {
			jCas = cas.getJCas();
		} catch (CASException e) {
			e.printStackTrace();
			return;
		}
		int offset = 0;
		String[] lines = value.toString().split("\n");
		for (String line : lines) {
			String cols[] = line.split("\t");
			if (cols.length != 2) {
//				System.err.println("Malformatted sentence-links line: " + line);
				continue;
			}
			String sentence = cols[0];
			String links = cols[1];
			Scanner s = new Scanner(links);
			s.useDelimiter(",|:|@");
			while (s.hasNext()) {
				try {
					WikiLink link = new WikiLink(jCas);
					String resource = s.next();
					link.setResource(resource);
					if (!s.hasNext()) {
						break;
					}
					int from = s.nextInt();
					if (!s.hasNext()) {
						break;
					}
					int end = s.nextInt();
					link.setBegin(from + offset);
					link.setEnd(end + offset);
					link.addToIndexes();
				} catch (InputMismatchException e) {
//					System.err.println("Malformatted link column: " + links);
					break;
				}
			}
			s.close();
			offset += sentence.length() + 1; // +1 due to \n character
		}
	}

	public Text extractDocumentText(Text key, Text value) {
		String[] lines = value.toString().split("\n");
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
		return new Text(documentText.toString());
	}

}
