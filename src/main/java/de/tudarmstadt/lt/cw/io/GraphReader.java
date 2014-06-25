package de.tudarmstadt.lt.cw.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;

import de.tudarmstadt.lt.cw.graph.ArrayBackedGraph;
import de.tudarmstadt.lt.cw.graph.StringIndexGraphWrapper;

public class GraphReader {
	final static Charset UTF_8 = Charset.forName("UTF-8");
	
	public static StringIndexGraphWrapper<Float> readABCIndexed(InputStream is, boolean includeSelfEdges, boolean undirected, int numNodes, int numEdgesPerNode, float minEdgeWeight) throws IOException {
		System.out.println("Reading input graph...");
		ArrayBackedGraph<Float> g = new ArrayBackedGraph<Float>(numNodes, numEdgesPerNode);
		StringIndexGraphWrapper<Float> gWrapper = new StringIndexGraphWrapper<Float>(g);
		BufferedReader reader = new BufferedReader(new InputStreamReader(is, UTF_8));
		String line;
		int lineCount = 0;
		ArrayList<String> targets = new ArrayList<String>(numEdgesPerNode);
		ArrayList<Float> weights = new ArrayList<Float>(numEdgesPerNode);
		String lastNode = null;
		while ((line = reader.readLine()) != null) {
			if (lineCount % 1000000 == 0) {
				System.out.print(".");
			}
			lineCount++;
			String[] lineSplits = line.split("\t");
			if (lineSplits.length != 3) {
				System.err.println("Warning: Found " + lineSplits.length + " columns instead of 3!");
				continue;
			}
			String from = lineSplits[0];
			String to = lineSplits[1];

			if (lastNode != null && !from.equals(lastNode) && !targets.isEmpty()) {
				gWrapper.addNode(lastNode, targets, weights, undirected);
				targets.clear();
				weights.clear();
			}
			
			float weight = Float.parseFloat(lineSplits[2]);
			if (weight >= minEdgeWeight) {
				if (includeSelfEdges || !to.equals(from)) {
					targets.add(to);
				}
				weights.add(weight);
			}
			
			lastNode = from;
		}
		
		gWrapper.addNode(lastNode, targets, weights, undirected);
		targets.clear();
		weights.clear();

		System.out.println();
		return gWrapper;
	}
}
