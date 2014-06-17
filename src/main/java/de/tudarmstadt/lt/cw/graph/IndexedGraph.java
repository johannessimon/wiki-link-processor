package de.tudarmstadt.lt.cw.graph;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class IndexedGraph<N, E> extends ArrayBackedGraph<E> {
	protected Map<N, Integer> nodeIndices;
	protected Map<Integer, N> nodeReverseIndices;
	
	public IndexedGraph(int size, int numEdgesPerNode) {
		super(size, numEdgesPerNode);
		nodeIndices = new HashMap<N, Integer>(size);
		nodeReverseIndices = new HashMap<Integer, N>(size);
	}
	
	public void addNodeIndexed(N node) {
		Integer index = getIndex(node);
		addNode(index);
	}
	
	public void addNodeIndexed(N node, List<N> targets, List<E> weights, boolean undirected) {
		Integer from = getIndex(node);
		Iterator<N> targetIt = targets.iterator();
		Iterator<E> weightIt = weights.iterator();
		while (targetIt.hasNext()) {
			Integer to = getIndex(targetIt.next());
			E weight = weightIt.next();
			
			if (undirected) {
				addEdgeUndirected(to, from, weight);
			} else {
				addEdge(from, to, weight);
			}
		}
		
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
