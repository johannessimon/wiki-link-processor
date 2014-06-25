package de.tudarmstadt.lt.wsi;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
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
import de.tudarmstadt.lt.cw.graph.StringIndexGraphWrapper;
import de.tudarmstadt.lt.cw.io.GraphReader;

public class CWD {
	protected Graph<Integer, Float> graph;
	protected StringIndexGraphWrapper<Float> graphWrapper;
	protected CW<Integer> cw;
	
	public CWD(StringIndexGraphWrapper<Float> graphWrapper) {
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
		String nodeName = graphWrapper.get(node);
		try {
			subgraph.writeDot(new BufferedOutputStream(new FileOutputStream(new File("/Users/jsimon/Desktop/graph-" + nodeName + ".dot"))), graphWrapper);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
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
			String nodeName = graphWrapper.get(node);
			String labelName = graphWrapper.get(label);
			Set<String> clusterNodeNames = new LinkedHashSet<String>();
			for (Integer clusterNode : cluster) {
				clusterNodeNames.add(graphWrapper.get(clusterNode));
			}
			Cluster<String> c = new Cluster<String>(nodeName, senseNr, labelName, clusterNodeNames);
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
		String nodes = null;
		if (args.length > 3) {
			nodes = args[3];
		}
		InputStream is = new FileInputStream(args[0]);
		OutputStream os = new FileOutputStream(args[1]);
		float minEdgeWeight = Float.parseFloat(args[2]);
		StringIndexGraphWrapper<Float> graphWrapper = GraphReader.readABCIndexed(is, false, false, 1100000, 100, minEdgeWeight);
		CWD cwd = new CWD(graphWrapper);
		System.out.println("Running CW sense clustering...");
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os));
		if (nodes != null) {
			String[] nodesArr = nodes.split(",");
			for (String node : nodesArr) {
				node = node.trim();
				Integer nodeIndex = graphWrapper.getIndex(node);
				cwd.findSenseClusters(writer, nodeIndex);
			}
		} else {
			cwd.findSenseClusters(writer);
		}
		writer.close();
		is.close();
	}
}
