package de.tudarmstadt.lt.cw.graph;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


public class Graph<N, E> {
	final static Charset UTF_8 = Charset.forName("UTF-8");
	
	protected Set<N> nodes;
	protected Map<N, Set<Edge<N, E>>> edges;
		
	public Graph() {
		nodes = new HashSet<N>();
		edges = new HashMap<N, Set<Edge<N, E>>>();
	}
	
	public Set<N> getNodes() {
		return nodes;
	}
	
	public void addNode(N node) {
		nodes.add(node);
	}
	
	public String getNodeName(N node) {
		return node.toString();
	}
	
	public void addEdgeUndirected(N from, N to, E weight) {
		addEdge(from, to, weight);
		addEdge(to, from, weight);
	}
	
	public void addEdge(N from, N to, E weight) {
		Edge<N, E> e = new Edge<N, E>(to, weight);
		
		Set<Edge<N, E>> outEdges = edges.get(from);
		if (outEdges == null) {
			outEdges = new HashSet<Edge<N, E>>();
			edges.put(from, outEdges);
		}
		outEdges.add(e);
	}
	
	public Set<N> getNeighbors(N node) {
		Set<Edge<N, E>> outEdges = getEdges(node);
		Set<N> neighbors = new HashSet<N>();
		for (Edge<N, E> edge : outEdges) {
			neighbors.add(edge.getTarget());
		}
		return neighbors;
	}
	
	public Set<Edge<N, E>> getEdges(N node) {
		Set<Edge<N, E>> outEdges = edges.get(node);
		Set<Edge<N, E>> outEdgesFiltered = new HashSet<Edge<N, E>>();
		if (outEdges != null) {
			for (Edge<N, E> e : outEdges) {
				if (nodes.contains(e.getTarget())) {
					outEdgesFiltered.add(e);
				}
			}
		}
		return outEdgesFiltered;
	}
	
	public Graph<N, E> subgraph(Set<N> subgraphNodes) {
		Graph<N, E> sg = new Graph<N, E>();
		sg.nodes = subgraphNodes;
		sg.edges = edges;
		return sg;
	}
	
	public Graph<N, E> undirected() {
		Graph<N, E> ug = new Graph<N, E>();
		ug.nodes = nodes;
		for (N from : nodes) {
			Set<Edge<N, E>> outEdges = edges.get(from);
			if (outEdges != null) {
				for (Edge<N, E> outEdge : outEdges) {
					N to = outEdge.getTarget();
					ug.addEdge(from, to, outEdge.getWeight());
					ug.addEdge(to, from, outEdge.getWeight());
				}
			}
		}
		return ug;
	}
	
	public void writeDot(OutputStream os) throws IOException {
		Writer writer = new BufferedWriter(new OutputStreamWriter(os, UTF_8));
		writer.write("digraph g {\n");
		for (N node : nodes) {
			writer.write("\t" + node + " [label=\"" + getNodeName(node) + "\"];\n");
		}
		for (N node : nodes) {
			for (N neighbor : getNeighbors(node)) {
				writer.write("\t" + node + " -> " + neighbor + ";\n");
			}
		}
		writer.write("}\n");
		writer.flush();
		return;
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("Graph {\n");
		for (N node : nodes) {
			sb.append("\t" + getNodeName(node) + ": ");
			for (N neighbor : getNeighbors(node)) {
				sb.append(getNodeName(neighbor) + ",");
			}
			sb.append("\n");
		}
		sb.append("}\n");
		return sb.toString();
	}
}
