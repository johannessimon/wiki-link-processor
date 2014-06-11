package de.tudarmstadt.lt.cw;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import de.tudarmstadt.lt.cw.CW;
import de.tudarmstadt.lt.cw.graph.Graph;


public class CWTest {

	@Test
	public void test() {
		Graph<String, Double> g = new Graph<String, Double>();
		g.addNode("VW");
		g.addNode("Lion");
		g.addNode("scary_@");
		g.addNode("hunt_a_@");
		g.addNode("drive_a_@");
		g.addNode("my_@");
		
		g.addEdgeUndirected("Lion", "scary_@", 1.0);
		g.addEdgeUndirected("Lion", "hunt_a_@", 1.0);
		g.addEdgeUndirected("VW", "drive_a_@", 1.0);
		g.addEdgeUndirected("VW", "my_@", 1.0);
		
		Set<String> cluster1 = new HashSet<String>();
		cluster1.add("Lion");
		cluster1.add("scary_@");
		cluster1.add("hunt_a_@");
		
		Set<String> cluster2 = new HashSet<String>();
		cluster2.add("VW");
		cluster2.add("drive_a_@");
		cluster2.add("my_@");
		
		CW<String> cw = new CW<String>();
		Map<String, Set<String>> clusters = cw.findClusters(g);
		assertEquals(2, clusters.size());
		assertTrue(clusters.containsValue(cluster1));
		assertTrue(clusters.containsValue(cluster2));
	}

}
