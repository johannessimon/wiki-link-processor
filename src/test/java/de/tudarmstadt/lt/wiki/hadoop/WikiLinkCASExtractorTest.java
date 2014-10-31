package de.tudarmstadt.lt.wiki.hadoop;

import junit.framework.TestCase;

import org.apache.uima.UIMAException;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.junit.Test;

import de.tudarmstadt.lt.wiki.uima.type.WikiLink;

public class WikiLinkCASExtractorTest extends TestCase {
	WikiLinkCASExtractor extractor = new WikiLinkCASExtractor();
	
	@Test
	public void testExtractAnnotationsWithLinkText() throws UIMAException {
		String doc = "Cars are faster than motor cycles.\tCars@@Automobile@0:4  motor cycles@@Motorbike@22:33";
		JCas jCas = JCasFactory.createJCas();
		extractor.extractAnnotations(doc, jCas.getCas());
		WikiLink[] links = JCasUtil.select(jCas, WikiLink.class).toArray(new WikiLink[2]);
		assertEquals(2, links.length);
		assertEquals("Automobile", links[0].getResource());
		assertEquals("Motorbike", links[1].getResource());
	}
	
	@Test
	public void testExtractAnnotationsWithoutLinkText() throws UIMAException {
		String doc = "Cars are faster than motor cycles.\tAutomobile@0:4  Motorbike@22:33";
		JCas jCas = JCasFactory.createJCas();
		extractor.extractAnnotations(doc, jCas.getCas());
		WikiLink[] links = JCasUtil.select(jCas, WikiLink.class).toArray(new WikiLink[2]);
		assertEquals(2, links.length);
		assertEquals("Automobile", links[0].getResource());
		assertEquals("Motorbike", links[1].getResource());
	}
}
