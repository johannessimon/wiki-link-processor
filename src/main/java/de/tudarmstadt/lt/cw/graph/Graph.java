package de.tudarmstadt.lt.cw.graph;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class Graph<N, E> extends GraphBase<N, E> {
	protected Set<N> nodes;
	protected Map<N, Set<Edge<N, E>>> edges;
		
	public Graph() {
		nodes = new HashSet<N>();
		edges = new HashMap<N, Set<Edge<N, E>>>();
	}
	
	/* (non-Javadoc)
	 * @see de.tudarmstadt.lt.cw.graph.IGraph#getNodes()
	 */
	public Iterator<N> iterator() {
		return nodes.iterator();
	}
	
	/* (non-Javadoc)
	 * @see de.tudarmstadt.lt.cw.graph.IGraph#addNode(N)
	 */
	public void addNode(N node) {
		nodes.add(node);
	}
	
	/* (non-Javadoc)
	 * @see de.tudarmstadt.lt.cw.graph.IGraph#getNodeName(N)
	 */
	public String getNodeName(N node) {
		return node.toString();
	}
	
	/* (non-Javadoc)
	 * @see de.tudarmstadt.lt.cw.graph.IGraph#addEdgeUndirected(N, N, E)
	 */
	public void addEdgeUndirected(N from, N to, E weight) {
		addEdge(from, to, weight);
		addEdge(to, from, weight);
	}
	
	/* (non-Javadoc)
	 * @see de.tudarmstadt.lt.cw.graph.IGraph#addEdge(N, N, E)
	 */
	public void addEdge(N from, N to, E weight) {
		Edge<N, E> e = new Edge<N, E>(to, weight);
		
		Set<Edge<N, E>> outEdges = edges.get(from);
		if (outEdges == null) {
			outEdges = new HashSet<Edge<N, E>>();
			edges.put(from, outEdges);
		}
		outEdges.add(e);
	}
	
	/* (non-Javadoc)
	 * @see de.tudarmstadt.lt.cw.graph.IGraph#getNeighbors(N)
	 */
	public Iterator<N> getNeighbors(N node) {
		Iterator<Edge<N, E>> outEdges = getEdges(node);
		List<N> neighbors = new LinkedList<N>();
		while (outEdges.hasNext()) {
			neighbors.add(outEdges.next().getTarget());
		}
		return neighbors.iterator();
	}
	
	/* (non-Javadoc)
	 * @see de.tudarmstadt.lt.cw.graph.IGraph#getEdges(N)
	 */
	public Iterator<Edge<N, E>> getEdges(N node) {
		Set<Edge<N, E>> outEdges = edges.get(node);
		if (outEdges != null) {
			return outEdges.iterator();
		} else {
			return Collections.emptyListIterator();
		}
	}
	
	/* (non-Javadoc)
	 * @see de.tudarmstadt.lt.cw.graph.IGraph#undirectedSubgraph(java.util.Set)
	 */
	/*
	public IGraph<N, E> undirectedSubgraph(Set<N> subgraphNodes) {
		Graph<N, E> sg = new Graph<N, E>();
		sg.nodes = subgraphNodes;
		for (N node : subgraphNodes) {
			Set<Edge<N, E>> nodeEdges = edges.get(node);
			if (nodeEdges != null) {
				for (Edge<N, E> edge : nodeEdges) {
					if (subgraphNodes.contains(edge.getTarget())) {
						sg.addEdge(node, edge.getTarget(), edge.getWeight());
						sg.addEdge(edge.getTarget(), node, edge.getWeight());
					}
				}
			}
		}
		return sg;
	}
	*/
	
	/* (non-Javadoc)
	 * @see de.tudarmstadt.lt.cw.graph.IGraph#subgraph(java.util.Set)
	 */
	public IGraph<N, E> subgraph(Collection<N> subgraphNodes) {
		Graph<N, E> sg = new Graph<N, E>();
		sg.nodes.addAll(subgraphNodes);
		for (N node : subgraphNodes) {
			Set<Edge<N, E>> nodeEdges = edges.get(node);
			Set<Edge<N, E>> nodeEdgesFiltered = new HashSet<Edge<N, E>>();
			for (Edge<N, E> edge : nodeEdges) {
				if (subgraphNodes.contains(edge.getTarget())) {
					nodeEdgesFiltered.add(edge);
				}
			}
			sg.edges.put(node, nodeEdgesFiltered);
		}
		return sg;
	}

	public IGraph<N, E> undirectedSubgraph(Collection<N> subgraphNodes) {
		// TODO Auto-generated method stub
		return null;
	}
}
