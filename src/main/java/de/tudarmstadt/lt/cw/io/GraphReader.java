package de.tudarmstadt.lt.cw.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

import de.tudarmstadt.lt.cw.graph.Graph;

public class GraphReader {
	final static Charset UTF_8 = Charset.forName("UTF-8");
	
	public static Graph<String, Double> readABC(InputStream is, boolean includeSelfEdges) throws IOException {
		System.out.println("Reading input graph...");
		Graph<String, Double> graph = new Graph<String, Double>();
		BufferedReader reader = new BufferedReader(new InputStreamReader(is, UTF_8));
		String line;
		int lineCount = 0;
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
			double weight = Double.parseDouble(lineSplits[2]);
			// Include self-edges only if requested
			if (!from.equals(to) || includeSelfEdges) {
				graph.addNode(from);
				graph.addNode(to);
				graph.addEdge(from, to, weight);
			}
		}

		System.out.println();
		return graph;
	}
	/*
	public static IndexedGraph<String, Double> readABCIndexed(InputStream is) throws IOException {
		System.out.println("Reading input graph...");
		IndexedGraph<String, Double> graph = new IndexedGraph<String, Double>();
		BufferedReader reader = new BufferedReader(new InputStreamReader(is, UTF_8));
		String line;
		int lineCount = 0;
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
			double weight = Double.parseDouble(lineSplits[2]);
			graph.addNodeIndexed(from);
			graph.addNodeIndexed(to);
			graph.addEdgeIndexed(from, to, weight);
		}

		System.out.println();
		return graph;
	}*/
}
