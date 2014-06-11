package de.tudarmstadt.lt.wsi;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

import de.tudarmstadt.lt.cw.graph.Graph;
import de.tudarmstadt.lt.wsi.CWD;


public class CWDTest {

	@Test
	public void test() throws IOException {
		Graph<String, Double> g = new Graph<String, Double>();
		g.addNode("VW");
		g.addNode("Jaguar");
		g.addNode("Lion");
		
		g.addEdgeUndirected("Jaguar", "Lion", 1.0);
		g.addEdgeUndirected("Jaguar", "VW", 1.0);
		
		Set<String> cluster1 = new HashSet<String>();
		cluster1.add("Lion");
		
		Set<String> cluster2 = new HashSet<String>();
		cluster2.add("VW");
		
		CWD<String> cwd = new CWD<String>(g);
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(System.out));
		cwd.findSenseClusters(writer);
		writer.close();
	}

}
