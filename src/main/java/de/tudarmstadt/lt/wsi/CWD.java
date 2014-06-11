package de.tudarmstadt.lt.wsi;

import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import de.tudarmstadt.lt.cw.CW;
import de.tudarmstadt.lt.cw.graph.Graph;
import de.tudarmstadt.lt.cw.io.GraphReader;

public class CWD<N> {
	protected Graph<N, Double> graph;
	protected CW<N> cw;
	
	public CWD(Graph<N, Double> graph) {
		this.graph = graph;
		cw = new CW<N>();
	}
	
	public Set<N> getTransitiveNeighbors(N node, int numHops) {
		Set<N> neighbors = new HashSet<N>();
		neighbors.add(node);
		for (int i = 0; i < numHops; i++) {
			Set<N> _neighbors = new HashSet<N>();
			for (N neighbor : neighbors) {
				_neighbors.addAll(graph.getNeighbors(neighbor));
			}
			
			neighbors.addAll(_neighbors);
		}
		
		// neighborhood excludes node itself
		neighbors.remove(node);
		return neighbors;
	}
	
	public Map<N, Set<N>> findSenseClusters(N node) {
		Set<N> neighbors = getTransitiveNeighbors(node, 1);
		Graph<N, Double> subgraph = graph.subgraph(neighbors).undirected();
		/*try {
			OutputStream os = new FileOutputStream("/Users/jsimon/No-Backup/wiki-holing-all/graph.dot");
			subgraph.writeDot(os);
			os.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}*/
		return cw.findClusters(subgraph);
	}
	
	public void findSenseClusters(Writer writer, N node) throws IOException {
		Map<N, Set<N>> clusters = findSenseClusters(node);
		int senseNr = 0;
		for (N label : clusters.keySet()) {
			Set<N> cluster = clusters.get(label);
			String nodeName = graph.getNodeName(node);
			String labelName = graph.getNodeName(label);
			Set<String> clusterNodeNames = new LinkedHashSet<String>();
			for (N clusterNode : cluster) {
				clusterNodeNames.add(graph.getNodeName(clusterNode));
			}
			Cluster c = new Cluster(nodeName, senseNr, labelName, clusterNodeNames);
			ClusterReaderWriter.writeCluster(writer, c);
			senseNr++;
		}
	}
	
	public void findSenseClusters(Writer writer) throws IOException {
		for (N node : graph.getNodes()) {
			findSenseClusters(writer, node);
		}
	}
	
	public static void main(String args[]) throws IOException {
		if (args.length < 2) {
			System.out.println("Usage: CWD input output [node]");
			return;
		}
		String node = null;
		if (args.length > 2) {
			node = args[2];
		}
		InputStream is = new FileInputStream(args[0]);
		OutputStream os = new FileOutputStream(args[1]);
		Graph<String, Double> graph = GraphReader.readABC(is, false);
		CWD<String> cwd = new CWD<String>(graph);
		System.out.println("Running CW sense clustering...");
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os));
		if (node != null) {
			cwd.findSenseClusters(writer, node);
		} else {
			cwd.findSenseClusters(writer);
		}
		writer.close();
		is.close();
	}
}
