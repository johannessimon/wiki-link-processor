package de.tudarmstadt.lt.cw.io;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.junit.Test;

import de.tudarmstadt.lt.cw.graph.Graph;
import de.tudarmstadt.lt.cw.io.GraphReader;


public class GraphReaderTest {

	@Test
	public void test() throws IOException {
		String input = "a	b	1.0\n"
					 + "b	c	2.5\n"
					 + "a	c	1.7\n";
		Graph<String, Double> graph = GraphReader.readABC(new ByteArrayInputStream(input.getBytes("UTF-8")), true);
		System.out.println(graph);
	}

}
