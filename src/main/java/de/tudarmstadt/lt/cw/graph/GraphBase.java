package de.tudarmstadt.lt.cw.graph;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.collections.IteratorUtils;

public abstract class GraphBase<N, E> implements Graph<N, E> {
	final static Charset UTF_8 = Charset.forName("UTF-8");

	public GraphBase() {
		super();
	}

	public void writeDot(OutputStream os) throws IOException {
		Writer writer = new BufferedWriter(new OutputStreamWriter(os, UTF_8));
		writer.write("digraph g {\n");
		Iterator<N> it = iterator();
		while (it.hasNext()) {
			N node = it.next();
			writer.write("\t" + node + " [label=\"" + getNodeName(node) + "\"];\n");
		}
		it = iterator();
		while (it.hasNext()) {
			N node = it.next();
			Iterator<N> neighborIt = getNeighbors(node);
			while (neighborIt.hasNext()) {
				N neighbor = neighborIt.next();
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
		Iterator<N> it = iterator();
		while (it.hasNext()) {
			N node = it.next();
			sb.append("\t" + getNodeName(node) + ": ");
			Iterator<N> neighborIt = getNeighbors(node);
			while (neighborIt.hasNext()) {
				N neighbor = neighborIt.next();
				if (neighbor == null) {
					break;
				}
				sb.append(getNodeName(neighbor) + ",");
			}
			sb.append("\n");
		}
		sb.append("}\n");
		return sb.toString();
	}

	@SuppressWarnings("unchecked")
	public boolean equals(Object other) {
		if (!(other instanceof Graph<?, ?>)) {
			return false;
		}
		Graph<?, ?> otherGraph = (Graph<?, ?>)other;
		List<N> nodes = IteratorUtils.toList(iterator());
		List<N> nodesOther = IteratorUtils.toList(otherGraph.iterator());
		if (!nodes.containsAll(nodesOther)) {
			return false;
		}
		if (!nodesOther.containsAll(nodes)) {
			return false;
		}

		List<Edge<N, E>> edges = IteratorUtils.toList(iterator());
		List<Edge<N, E>> edgesOther = IteratorUtils.toList(otherGraph.iterator());
		if (!edges.containsAll(edgesOther)) {
			return false;
		}
		if (!edgesOther.containsAll(edges)) {
			return false;
		}
		
		return true;
	}
}