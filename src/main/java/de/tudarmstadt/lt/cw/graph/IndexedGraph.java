package de.tudarmstadt.lt.cw.graph;

import java.util.HashMap;
import java.util.Map;

public class IndexedGraph<N, E> extends Graph<Integer, E> {
	protected Map<N, Integer> nodeIndices = new HashMap<N, Integer>();
	protected Map<Integer, N> nodeReverseIndices = new HashMap<Integer, N>();
	
	public void addNodeIndexed(N node) {
		Integer index = getIndex(node);
		addNode(index);
	}
	
	public void addEdgeIndexed(N from, N to, E weight) {
		Integer fromIndex = getIndex(from);
		Integer toIndex = getIndex(to);
		addEdge(fromIndex, toIndex, weight);
	}
	
	public Integer getIndex(N node) {
		Integer index = nodeIndices.get(node);
		if (index == null) {
			index = nodeIndices.size();
			nodeIndices.put(node, index);
			nodeReverseIndices.put(index, node);
		}
		return index;
	}

	public N getNode(Integer index) {
		return nodeReverseIndices.get(index);
	}
	
	@Override
	public String getNodeName(Integer node) {
		return getNode(node).toString();
	}
}
