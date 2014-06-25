package de.tudarmstadt.lt.wsi;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Cluster<N> {
	public N name;
	public int clusterId;
	public N label;
	public Set<N> nodes;
	public Map<N, Integer> featureCounts;
	public int processedNodes;
	
	public Cluster(N name, int clusterId, N label, Set<N> nodes) {
		this(name, clusterId, label, nodes, new HashMap<N, Integer>());
	}
	
	public Cluster(N name, int clusterId, N label, Set<N> nodes, Map<N, Integer> featureCounts) {
		this.name = name;
		this.clusterId = clusterId;
		this.label = label;
		this.nodes = nodes;
		this.processedNodes = 0;
		this.featureCounts = featureCounts;
	}
	
	@Override
	public String toString() {
		return name + "." + clusterId + " = " + nodes;
	}
}