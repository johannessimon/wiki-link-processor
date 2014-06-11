package de.tudarmstadt.lt.cw;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import de.tudarmstadt.lt.cw.graph.Edge;
import de.tudarmstadt.lt.cw.graph.Graph;


public class CW<N> {
	// Copy of node list is used shuffling order of nodes
	protected List<N> nodes;
	protected Graph<N, Double> graph;
	protected Map<N, N> nodeLabels;
	protected boolean changeInPrevStep;
	private Map<N, Double> labelScores = new HashMap<N, Double>();
	
	protected void init(Graph<N, Double> graph) {
		this.graph = graph;
		// ArrayList provides linear time random access (used for shuffle in step())
		this.nodes = new ArrayList<N>();
		this.nodes.addAll(graph.getNodes());
		
		nodeLabels = new HashMap<N, N>();
		for (N node : nodes) {
			nodeLabels.put(node, node);
		}
	}
	
	protected void relabelNode(N node) {
		labelScores.clear();
		N oldLabel = nodeLabels.get(node);
		Set<Edge<N, Double>> edges = graph.getEdges(node);
		
		// There's nothing to do if there's no neighbors
		if (edges.isEmpty()) {
			return;
		}
		
		for (Edge<N, Double> edge : edges) {
			N label = nodeLabels.get(edge.getTarget());
			Double score = labelScores.get(label);
			if (score == null) {
				score = 0.0;
			}
			score += edge.getWeight();
			labelScores.put(label, score);
		}
		N newLabel = getKeyWithMaxValue(labelScores);
		if (!oldLabel.equals(newLabel)) {
			nodeLabels.put(node, newLabel);
			changeInPrevStep = true;
		}
	}
	
	private N getKeyWithMaxValue(Map<N, Double> map) {
		N maxKey = null;
		Double maxVal = -Double.MAX_VALUE;
		for (Entry<N, Double> entry : map.entrySet()) {
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
	
	Map<N, Set<N>> getClusters() {
		Map<N, Set<N>> clusters = new HashMap<N, Set<N>>();
		for (Entry<N, N> nodeLabel : nodeLabels.entrySet()) {
			N label = nodeLabel.getValue();
			N node = nodeLabel.getKey();
			Set<N> cluster = clusters.get(label);
			if (cluster == null) {
				cluster = new HashSet<N>();
				clusters.put(label, cluster);
			}
			cluster.add(node);
		}
		return clusters;
	}
	
	public Map<N, Set<N>> findClusters(Graph<N, Double> graph) {		
		init(graph);
		do {
			changeInPrevStep = false;
			step();
		} while (changeInPrevStep);
		
		return getClusters();
	}
}
