package de.tudarmstadt.lt.wsi;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import de.tudarmstadt.lt.cw.graph.Graph;
import de.tudarmstadt.lt.cw.graph.IGraph;
import de.tudarmstadt.lt.cw.graph.IndexedGraph;


public class CWDTest {

	@Test
	public void test() throws IOException {
		IGraph<String, Float> g = new Graph<String, Float>();
		String vw = "VW";
		String jaguar = "Jaguar";
		String lion = "Lion";
		g.addNode(vw);
		g.addNode(jaguar);
		g.addNode(lion);
		
		g.addEdgeUndirected(jaguar, lion, 1.0f);
		g.addEdgeUndirected(jaguar, vw, 1.0f);
		
		Map<String, Set<String>> vwCluster = new HashMap<String, Set<String>>();
		Set<String> vwCluster0 = new HashSet<String>();
		vwCluster0.add(jaguar);
		vwCluster.put(jaguar, vwCluster0);
		Map<String, Set<String>> jaguarCluster = new HashMap<String, Set<String>>();
		Set<String> jaguarCluster0 = new HashSet<String>();
		jaguarCluster0.add(lion);
		jaguarCluster.put(lion, jaguarCluster0);
		Set<String> jaguarCluster1 = new HashSet<String>();
		jaguarCluster1.add(vw);
		jaguarCluster.put(vw, jaguarCluster1);
		Map<String, Set<String>> lionCluster = new HashMap<String, Set<String>>();
		Set<String> lionCluster0 = new HashSet<String>();
		lionCluster0.add(jaguar);
		lionCluster.put(jaguar, lionCluster0);
		
		CWD<String> cwd = new CWD<String>(g);
		assertEquals(vwCluster, cwd.findSenseClusters(vw));
		assertEquals(jaguarCluster, cwd.findSenseClusters(jaguar));
		assertEquals(lionCluster, cwd.findSenseClusters(lion));
	}

	@Test
	public void testIndexed() throws IOException {
		IndexedGraph<String, Float> g = new IndexedGraph<String, Float>(3, 2);
		g.addNodeIndexed("VW");
		g.addNodeIndexed("Jaguar");
		g.addNodeIndexed("Lion");
		int vw = g.getIndex("VW");
		int jaguar = g.getIndex("Jaguar");
		int lion = g.getIndex("Lion");
		
		g.addEdgeIndexed("Jaguar", "Lion", 1.0f);
		g.addEdgeIndexed("Lion", "Jaguar", 1.0f);
		g.addEdgeIndexed("Jaguar", "VW", 1.0f);
		g.addEdgeIndexed("VW", "Jaguar", 1.0f);
		
		Map<Integer, Set<Integer>> vwCluster = new HashMap<Integer, Set<Integer>>();
		Set<Integer> vwCluster0 = new HashSet<Integer>();
		vwCluster0.add(jaguar);
		vwCluster.put(jaguar, vwCluster0);
		Map<Integer, Set<Integer>> jaguarCluster = new HashMap<Integer, Set<Integer>>();
		Set<Integer> jaguarCluster0 = new HashSet<Integer>();
		jaguarCluster0.add(lion);
		jaguarCluster.put(lion, jaguarCluster0);
		Set<Integer> jaguarCluster1 = new HashSet<Integer>();
		jaguarCluster1.add(vw);
		jaguarCluster.put(vw, jaguarCluster1);
		Map<Integer, Set<Integer>> lionCluster = new HashMap<Integer, Set<Integer>>();
		Set<Integer> lionCluster0 = new HashSet<Integer>();
		lionCluster0.add(jaguar);
		lionCluster.put(jaguar, lionCluster0);
		
		CWD<Integer> cwd = new CWD<Integer>(g);
		assertClusterEquals(vwCluster, cwd.findSenseClusters(vw));
		assertClusterEquals(jaguarCluster, cwd.findSenseClusters(jaguar));
		assertClusterEquals(lionCluster, cwd.findSenseClusters(lion));
		
		g.addEdgeIndexed("Lion", "VW", 1.0f);
		g.addEdgeIndexed("VW", "Lion", 1.0f);
		
		System.out.println("fooo");
		jaguarCluster = new HashMap<Integer, Set<Integer>>();
		jaguarCluster0 = new HashSet<Integer>();
		jaguarCluster0.add(lion);
		jaguarCluster0.add(vw);
		jaguarCluster.put(vw, jaguarCluster0);
		assertClusterEquals(jaguarCluster, cwd.findSenseClusters(jaguar));
	}
	
	private void assertClusterEquals(Map<Integer, Set<Integer>> a, Map<Integer, Set<Integer>> b) {
		Collection<Set<Integer>> clustersA = a.values();
		Collection<Set<Integer>> clustersB = b.values();
		assertEquals(clustersA.size(), clustersB.size());
		for (Set<Integer> cluster : clustersA) {
			assertTrue(clustersB.contains(cluster));
		}
	}
}
