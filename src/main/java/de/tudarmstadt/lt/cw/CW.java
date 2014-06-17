package de.tudarmstadt.lt.cw;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import de.tudarmstadt.lt.cw.graph.Edge;
import de.tudarmstadt.lt.cw.graph.IGraph;


public class CW<N> {
	// Copy of node list is used shuffling order of nodes
	protected List<N> nodes;
	protected IGraph<N, Float> graph;
	protected Map<N, N> nodeLabels;
	protected boolean changeInPrevStep;
	protected Map<N, Float> labelScores = new HashMap<N, Float>();
	
	protected void init(IGraph<N, Float> graph) {
		this.graph = graph;
		// ArrayList provides linear time random access (used for shuffle in step())
		this.nodes = new ArrayList<N>();

		Iterator<N> nodeIt = graph.iterator();
		while (nodeIt.hasNext()) {
			this.nodes.add(nodeIt.next());
		}
		
		nodeLabels = new HashMap<N, N>();
		for (N node : nodes) {
			nodeLabels.put(node, node);
		}
	}
	
	protected void relabelNode(N node) {
		labelScores.clear();
		N oldLabel = nodeLabels.get(node);
		Iterator<Edge<N, Float>> edgeIt = graph.getEdges(node);
		
		// There's nothing to do if there's no neighbors
		if (!edgeIt.hasNext()) {
			return;
		}
		
		while (edgeIt.hasNext()) {
			Edge<N, Float> edge = edgeIt.next();
			if (edge == null) {
				break;
			}
			N label = nodeLabels.get(edge.getTarget());
			Float score = labelScores.get(label);
			if (score == null) {
				score = 0.0f;
			}
			score += edge.getWeight();
			labelScores.put(label, score);
		}
		// isEmpty() check in case e.g. node has no neighbors at all
		// (it will simply keep its own label then)
		if (!labelScores.isEmpty()) {
			N newLabel = getKeyWithMaxValue(labelScores);
			if (!oldLabel.equals(newLabel)) {
				nodeLabels.put(node, newLabel);
				changeInPrevStep = true;
			}
		}
	}
	
	protected N getKeyWithMaxValue(Map<N, Float> map) {
		N maxKey = null;
		Float maxVal = -Float.MAX_VALUE;
		for (Entry<N, Float> entry : map.entrySet()) {
			if (entry.getValue() > maxVal) {
				maxKey = entry.getKey();
				maxVal = entry.getValue();
			}
		}
		return maxKey;
	}
	
	protected void step() {
		Collections.shuffle(nodes);
		for (N node : nodes) {
			relabelNode(node);
		}
	}
	
	protected N getNodeLabel(N node) {
		return nodeLabels.get(node);
	}
	
	Map<N, Set<N>> getClusters() {
		Map<N, Set<N>> clusters = new HashMap<N, Set<N>>();
		for (N node : nodes) {
			N label = getNodeLabel(node);
			Set<N> cluster = clusters.get(label);
			if (cluster == null) {
				cluster = new HashSet<N>();
				clusters.put(label, cluster);
			}
			cluster.add(node);
		}
		return clusters;
	}
	
	public Map<N, Set<N>> findClusters(IGraph<N, Float> graph) {		
		init(graph);
		do {
			changeInPrevStep = false;
			step();
		} while (changeInPrevStep);
		
		return getClusters();
	}
}
