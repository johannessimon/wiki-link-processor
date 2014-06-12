package de.tudarmstadt.lt.cw.io;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.junit.Test;
import static junitx.framework.FileAssert.assertEquals;

import de.tudarmstadt.lt.cw.graph.Graph;


public class GraphReaderTest {

	@Test
	public void test() throws IOException {
		File in = new File("src/test/resources/graph/test.txt");
		File outActual = new File("src/test/resources/tmp/test.graph");
		File outExpected = new File("src/test/resources/graph/test.graph");
		outActual.getParentFile().mkdirs();
		String input = FileUtils.readFileToString(in, "UTF-8");
		Graph<String, Double> graph = GraphReader.readABC(new ByteArrayInputStream(input.getBytes("UTF-8")), true);
		String output = graph.toString();
		FileUtils.writeStringToFile(outActual, output, "UTF-8");
		assertEquals(outExpected, outActual);
	}

}
