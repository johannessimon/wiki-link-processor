package de.tudarmstadt.lt.cw.graph;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Iterator;

public interface Graph<N, E> extends Iterable<N>{

	public abstract void addNode(N node);

	public abstract void addEdgeUndirected(N from, N to, E weight);

	public abstract void addEdge(N from, N to, E weight);

	public abstract Iterator<N> getNeighbors(N node);

	public abstract Iterator<Edge<N, E>> getEdges(N node);

	/**
	 * Returns a non-modifiable undirected subgraph of this graph.<br>
	 * 
	 * <b>NOTE: The behaviour of this graph when nodes are added or removed is undefined!</b>
	 */
	public abstract Graph<N, E> undirectedSubgraph(Collection<N> subgraphNodes);

	/**
	 * Returns a non-modifiable subgraph of this graph.<br>
	 * 
	 * <b>NOTE: The behaviour of this graph when nodes are added or removed is undefined!</b>
	 */
	public abstract Graph<N, E> subgraph(Collection<N> subgraphNodes);

	public abstract void writeDot(OutputStream os) throws IOException;

	public abstract String toString();
}