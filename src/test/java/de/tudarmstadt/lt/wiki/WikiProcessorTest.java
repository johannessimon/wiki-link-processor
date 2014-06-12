package de.tudarmstadt.lt.wiki;

import static junitx.framework.FileAssert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.junit.Test;

public class WikiProcessorTest {

	@Test
	public void test() throws IOException {
		File in = new File("src/test/resources/pages/Auguste_Magdalene_of_Hessen-Darmstadt.txt");
		File outExpected = new File("src/test/resources/pages/Auguste_Magdalene_of_Hessen-Darmstadt-sentences.txt");
		File outActual = new File("src/test/resources/tmp/Auguste_Magdalene_of_Hessen-Darmstadt-sentences.txt");
		outActual.getParentFile().mkdirs();
		WikiProcessor p = new WikiProcessor();
		String page = FileUtils.readFileToString(in, "UTF-8");
		List<String> sentences = new LinkedList<String>();
		p.parse(page, sentences, null);
		FileUtils.writeLines(outActual, "UTF-8", sentences);
		assertEquals(outExpected, outActual);
	}

}