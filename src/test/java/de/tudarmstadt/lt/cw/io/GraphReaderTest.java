package de.tudarmstadt.lt.cw.io;
import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;

import de.tudarmstadt.lt.cw.graph.ArrayBackedGraph;
import de.tudarmstadt.lt.cw.graph.Graph;
import de.tudarmstadt.lt.cw.graph.StringIndexGraphWrapper;


public class GraphReaderTest {
	Graph<Integer, Float> testGraph;
	StringIndexGraphWrapper<Float> testGraphIndexed;
	@Before
	public void setup() {
		testGraph = new ArrayBackedGraph<Float>(3, 2);
		testGraphIndexed = new StringIndexGraphWrapper<Float>(testGraph);
		testGraphIndexed.addNode("a");
		testGraphIndexed.addNode("b");
		testGraphIndexed.addNode("c");
		testGraphIndexed.addEdge("a", "b", 1.0f);
		testGraphIndexed.addEdge("b", "c", 2.5f);
		testGraphIndexed.addEdge("a", "c", 1.7f);
	}

	@Test
	public void testIndexed() throws IOException {
		File in = new File("src/test/resources/graph/test.txt");
		String input = FileUtils.readFileToString(in, "UTF-8");
		StringIndexGraphWrapper<Float> graphWrapper = GraphReader.readABCIndexed(new ByteArrayInputStream(input.getBytes("UTF-8")), true, true, 3, 2, 0.0f);
		assertEquals(graphWrapper.getGraph(), testGraph);
	}

}
