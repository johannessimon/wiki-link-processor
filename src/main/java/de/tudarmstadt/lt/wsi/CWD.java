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

public class CWD {
	protected Graph<Integer, Float> graph;
	protected String2IntegerGraphWrapper<Float> graphWrapper;
	protected CW<Integer> cw;
	
	public CWD(String2IntegerGraphWrapper<Float> graphWrapper) {
		this.graph = graphWrapper.getGraph();
		this.graphWrapper = graphWrapper;
		if (graph instanceof ArrayBackedGraph) {
			ArrayBackedGraph<Float> abg = (ArrayBackedGraph<Float>)graph;
			cw = new ArrayBackedGraphCW(abg.getArraySize());
		} else {
			cw = new CW<Integer>();
		}
	}
	
	public List<Integer> getTransitiveNeighbors(Integer node, int numHops) {
		List<Integer> neighbors = new LinkedList<Integer>();
		neighbors.add(node);
		for (int i = 0; i < numHops; i++) {
			Set<Integer> _neighbors = new HashSet<Integer>();
			for (Integer neighbor : neighbors) {
				Iterator<Integer> neighborIt = graph.getNeighbors(neighbor);
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
	
	public Map<Integer, Set<Integer>> findSenseClusters(Integer node) {
		List<Integer> neighbors = getTransitiveNeighbors(node, 1);
		Graph<Integer, Float> subgraph = graph.undirectedSubgraph(neighbors);
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
	
	public void findSenseClusters(Writer writer, Integer node) throws IOException {
		Map<Integer, Set<Integer>> clusters = findSenseClusters(node);
		int senseNr = 0;
		for (Integer label : clusters.keySet()) {
			Set<Integer> cluster = clusters.get(label);
			String nodeName = graphWrapper.getNodeName(node);
			String labelName = graphWrapper.getNodeName(label);
			Set<String> clusterNodeNames = new LinkedHashSet<String>();
			for (Integer clusterNode : cluster) {
				clusterNodeNames.add(graphWrapper.getNodeName(clusterNode));
			}
			Cluster c = new Cluster(nodeName, senseNr, labelName, clusterNodeNames);
			ClusterReaderWriter.writeCluster(writer, c);
			senseNr++;
		}
	}
	
	public void findSenseClusters(Writer writer) throws IOException {
		Iterator<Integer> nodeIt = graph.iterator();
		while (nodeIt.hasNext()) {
			Integer node = nodeIt.next();
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
		CWD cwd = new CWD(graphWrapper);
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
