package de.tudarmstadt.lt.cw.graph;

import java.util.Iterator;
import java.util.List;

import de.tudarmstadt.lt.util.IndexUtil.StringIndex;

public class StringIndexGraphWrapper<E> extends StringIndex {
	protected Graph<Integer, E> base;
	
	public StringIndexGraphWrapper(Graph<Integer, E> base) {
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
}
