package de.tudarmstadt.lt.wsi;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import de.tudarmstadt.lt.cw.CW;
import de.tudarmstadt.lt.cw.graph.ArrayBackedGraph;
import de.tudarmstadt.lt.cw.graph.ArrayBackedGraphCW;
import de.tudarmstadt.lt.cw.graph.Graph;
import de.tudarmstadt.lt.cw.graph.StringIndexGraphWrapper;
import de.tudarmstadt.lt.cw.io.GraphReader;
import de.tudarmstadt.lt.util.FileUtil;
import de.tudarmstadt.lt.util.MonitoredFileReader;
import de.tudarmstadt.lt.util.ProgressMonitor;

public class CWD {
	protected Graph<Integer, Float> graph;
	protected StringIndexGraphWrapper<Float> graphWrapper;
	protected CW<Integer> cw;
	
	public CWD(StringIndexGraphWrapper<Float> graphWrapper) {
		this(graphWrapper, CW.UNLIMITED_NUMBER_EDGES);
	}
	
	public CWD(StringIndexGraphWrapper<Float> graphWrapper, int maxEdgesPerNode) {
		this.graph = graphWrapper.getGraph();
		this.graphWrapper = graphWrapper;
		// FIXME: Handle maxEdgesPerNode
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
		/*String nodeName = graphWrapper.get(node);
		try {
			subgraph.writeDot(new BufferedOutputStream(new FileOutputStream(new File("/Users/jsimon/Desktop/graph-" + nodeName + ".dot"))), graphWrapper);
		} catch (Exception e) {
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
		int processedNodes = 0;
		ProgressMonitor monitor = new ProgressMonitor("word graph", "nodes", graph.getSize(), 0.01);
		while (nodeIt.hasNext()) {
			Integer node = nodeIt.next();
			findSenseClusters(writer, node);
			processedNodes++;
			monitor.reportProgress(processedNodes);
		}
	}
	
	@SuppressWarnings("static-access")
	public static void main(String args[]) throws IOException {
		CommandLineParser clParser = new BasicParser();
		Options options = new Options();
		options.addOption(OptionBuilder.withArgName("file")
		                  .hasArg()
                          .withDescription("input graph in ABC format (uncompressed or gzipped)")
                          .isRequired()
                          .create("in"));
		options.addOption(OptionBuilder.withArgName("file")
		                  .hasArg()
                          .withDescription("name of cluster output file (add .gz for compressed output)")
                          .isRequired()
                          .create("out"));
		options.addOption(OptionBuilder.withArgName("integer")
		                  .hasArg()
		                  .withDescription("max. number of edges to process for each similar word (word subgraph connectivity)")
		                  .isRequired()
		                  .create("n"));
		options.addOption(OptionBuilder.withArgName("integer")
		                  .hasArg()
                          .withDescription("max. number of similar words to process for a given word (size of word subgraph to be clustered)")
                          .isRequired()
                          .create("N"));
		options.addOption(OptionBuilder.withArgName("float")
		                  .hasArg()
                          .withDescription("min. edge weight")
                          .create("e"));
		options.addOption(OptionBuilder.withArgName("node-list")
                .hasArgs()
                .withDescription("comma-separated list of nodes to cluster")
                .withValueSeparator(',')
                .create("nodes"));
		CommandLine cl = null;
		boolean success = false;
		try {
			cl = clParser.parse(options, args);
			success = true;
		} catch (ParseException e) {
			System.out.println(e.getMessage());
		}
		
		if (!success) {
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp("CWD", options, true);
			System.exit(1);
		}
		String[] nodes = cl.getOptionValues("nodes");
		String inFile = cl.getOptionValue("in");
		String outFile = cl.getOptionValue("out");
		Reader inReader = new MonitoredFileReader(inFile);
		Writer writer = FileUtil.createWriter(outFile);
		float minEdgeWeight = cl.hasOption("e") ? Float.parseFloat(cl.getOptionValue("e")) : 0.0f;
		int N = Integer.parseInt(cl.getOptionValue("N"));
		int n = Integer.parseInt(cl.getOptionValue("n"));
		StringIndexGraphWrapper<Float> graphWrapper = GraphReader.readABCIndexed(inReader, false, N, minEdgeWeight);
		CWD cwd = new CWD(graphWrapper, n);
		System.out.println("Running CW sense clustering...");
		if (nodes != null) {
			for (String node : nodes) {
				node = node.trim();
				Integer nodeIndex = graphWrapper.getIndex(node);
				cwd.findSenseClusters(writer, nodeIndex);
			}
		} else {
			cwd.findSenseClusters(writer);
		}
		writer.close();
	}
}
