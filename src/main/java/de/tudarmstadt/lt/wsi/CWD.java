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
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.tudarmstadt.lt.cw.CW;
import de.tudarmstadt.lt.cw.graph.ArrayBackedGraph;
import de.tudarmstadt.lt.cw.graph.ArrayBackedGraphCW;
import de.tudarmstadt.lt.cw.graph.Graph;
import de.tudarmstadt.lt.cw.graph.String2IntegerGraphWrapper;
import de.tudarmstadt.lt.cw.io.GraphReader;

public class CWD<N> {
	protected Graph<N, Float> graph;
	protected CW<N> cw;
	
	@SuppressWarnings("unchecked")
	public CWD(Graph<N, Float> graph) {
		this.graph = graph;
		if (graph instanceof ArrayBackedGraph) {
			// ArrayBackedGraph --> N == Integer
			ArrayBackedGraph<Float> abg = (ArrayBackedGraph<Float>)graph;
			cw = (CW<N>)new ArrayBackedGraphCW(abg.getArraySize());
		} else {
			cw = new CW<N>();
		}
	}
	
	public List<N> getTransitiveNeighbors(N node, int numHops) {
		List<N> neighbors = new LinkedList<N>();
		neighbors.add(node);
		for (int i = 0; i < numHops; i++) {
			Set<N> _neighbors = new HashSet<N>();
			for (N neighbor : neighbors) {
				Iterator<N> neighborIt = graph.getNeighbors(neighbor);
				while (neighborIt.hasNext()) {
					_neighbors.add(neighborIt.next());
				}
			}
			
			neighbors.addAll(_neighbors);
		}
		
		// neighborhood excludes node itself
		neighbors.remove(node);
		return neighbors;
	}
	
	public Map<N, Set<N>> findSenseClusters(N node) {
		List<N> neighbors = getTransitiveNeighbors(node, 1);
		Graph<N, Float> subgraph = graph.undirectedSubgraph(neighbors);
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
		Iterator<N> nodeIt = graph.iterator();
		while (nodeIt.hasNext()) {
			N node = nodeIt.next();
			findSenseClusters(writer, node);
		}
	}
	
	public static void main(String args[]) throws IOException {
		if (args.length < 3) {
			System.out.println("Usage: CWD input output min-edge-weight [node]");
			return;
		}
		String node = null;
		if (args.length > 3) {
			node = args[3];
		}
		InputStream is = new FileInputStream(args[0]);
		OutputStream os = new FileOutputStream(args[1]);
		float minEdgeWeight = Float.parseFloat(args[2]);
		String2IntegerGraphWrapper<Float> graphWrapper = GraphReader.readABCIndexed(is, false, false, 1100000, 100, minEdgeWeight);
		CWD<Integer> cwd = new CWD<Integer>(graphWrapper.getGraph());
		System.out.println("Running CW sense clustering...");
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os));
		if (node != null) {
			Integer nodeIndex = graphWrapper.getIndex(node);
			cwd.findSenseClusters(writer, nodeIndex);
		} else {
			cwd.findSenseClusters(writer);
		}
		writer.close();
		is.close();
	}
}
