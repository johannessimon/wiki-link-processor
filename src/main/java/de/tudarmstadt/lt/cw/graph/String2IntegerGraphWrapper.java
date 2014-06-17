package de.tudarmstadt.lt.cw.graph;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class String2IntegerGraphWrapper<E> {
	protected Map<String, Integer> nodeIndices = new HashMap<String, Integer>();
	protected Map<Integer, String> nodeReverseIndices = new HashMap<Integer, String>();
	protected Graph<Integer, E> base;
	
	public String2IntegerGraphWrapper(Graph<Integer, E> base) {
		this.base = base;
	}
	
	public Graph<Integer, E> getGraph() {
		return base;
	}
	
	public void addNode(String node) {
		Integer index = getIndex(node);
		base.addNode(index);
	}
	
	public void addNode(String node, List<String> targets, List<E> weights, boolean undirected) {
		Integer from = getIndex(node);
		Iterator<String> targetIt = targets.iterator();
		Iterator<E> weightIt = weights.iterator();
		while (targetIt.hasNext()) {
			Integer to = getIndex(targetIt.next());
			E weight = weightIt.next();
			
			if (undirected) {
				base.addEdgeUndirected(to, from, weight);
			} else {
				base.addEdge(from, to, weight);
			}
		}
		
	}
	
	public void addEdge(String from, String to, E weight) {
		Integer fromIndex = getIndex(from);
		Integer toIndex = getIndex(to);
		base.addEdge(fromIndex, toIndex, weight);
	}
	
	public Integer getIndex(String node) {
		Integer index = nodeIndices.get(node);
		if (index == null) {
			index = nodeIndices.size();
			nodeIndices.put(node, index);
			nodeReverseIndices.put(index, node);
		}
		return index;
	}

	public String getNode(Integer index) {
		return nodeReverseIndices.get(index);
	}
	
	public String getNodeName(Integer node) {
		return getNode(node).toString();
	}
}
