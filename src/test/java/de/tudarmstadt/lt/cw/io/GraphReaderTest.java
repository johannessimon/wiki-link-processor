package de.tudarmstadt.lt.cw.io;
import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;

import de.tudarmstadt.lt.cw.graph.Graph;
import de.tudarmstadt.lt.cw.graph.IGraph;
import de.tudarmstadt.lt.cw.graph.IndexedGraph;


public class GraphReaderTest {
	IGraph<String, Float> testGraph;
	IndexedGraph<String, Float> testGraphIndexed;
	@Before
	public void setup() {
		testGraph = new Graph<String, Float>();
		testGraph.addNode("a");
		testGraph.addNode("b");
		testGraph.addNode("c");
		testGraph.addEdge("a", "b", 1.0f);
		testGraph.addEdge("b", "c", 2.5f);
		testGraph.addEdge("a", "c", 1.7f);
		
		testGraphIndexed = new IndexedGraph<String, Float>(3, 2);
		testGraphIndexed.addNodeIndexed("a");
		testGraphIndexed.addNodeIndexed("b");
		testGraphIndexed.addNodeIndexed("c");
		testGraphIndexed.addEdgeIndexed("a", "b", 1.0f);
		testGraphIndexed.addEdgeIndexed("b", "c", 2.5f);
		testGraphIndexed.addEdgeIndexed("a", "c", 1.7f);
	}

	@Test
	public void test() throws IOException {
		File in = new File("src/test/resources/graph/test.txt");
		String input = FileUtils.readFileToString(in, "UTF-8");
		IGraph<String, Float> graph = GraphReader.readABC(new ByteArrayInputStream(input.getBytes("UTF-8")), true, false);
		assertEquals(testGraph, graph);
	}

	@Test
	public void testIndexed() throws IOException {
		File in = new File("src/test/resources/graph/test.txt");
		String input = FileUtils.readFileToString(in, "UTF-8");
		IGraph<Integer, Float> graph = GraphReader.readABCIndexed(new ByteArrayInputStream(input.getBytes("UTF-8")), true, true, 3, 2, 0.0f);
		assertEquals(testGraphIndexed, graph);
	}

}
