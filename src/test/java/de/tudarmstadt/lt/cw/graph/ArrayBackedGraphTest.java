package de.tudarmstadt.lt.cw.graph;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import de.tudarmstadt.lt.wsi.CWD;

public class ArrayBackedGraphTest {

	@Test
	public void test() {
		int numNodes = 100;
		int numEdges = 100;
		long start = System.nanoTime();
		System.out.println("Instantiating graph with " + numNodes + " nodes...");
		ArrayBackedGraph<Float> g = new ArrayBackedGraph<Float>(numNodes, numEdges);
		System.out.println("Elapsed time in seconds: " + (System.nanoTime() - start) / 1000000000.0);
		System.out.println("Adding " + numNodes + " nodes with " + numEdges + " random edges per node...");
		for (int i = 0; i < numNodes; i++) {
			List<Integer> targets = new ArrayList<Integer>(numEdges);
			List<Float> weights = new ArrayList<Float>(numEdges);
			for (int j = 0; j < numEdges; j++) {
				int to = (int)(Math.random() * numNodes);
				float weight = (float)(Math.random() * 100);
				targets.add(to);
				weights.add(weight);
				g.addEdgeUndirected(i, to, weight);
			}
		}
		System.out.println("Elapsed time in seconds: " + (System.nanoTime() - start) / 1000000000.0);
		System.out.println("Clustering all nodes...");
		CWD<Integer> cwd = new CWD<Integer>(g);
		for (int i = 0; i < numNodes; i++) {
			cwd.findSenseClusters(i);
		}
		System.out.println("Elapsed time in seconds: " + (System.nanoTime() - start) / 1000000000.0);
	}

}
