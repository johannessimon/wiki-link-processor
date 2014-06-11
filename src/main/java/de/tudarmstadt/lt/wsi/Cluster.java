package de.tudarmstadt.lt.wsi;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Cluster {
	public String name;
	public int clusterId;
	public String label;
	public Set<String> nodes;
	public Map<String, Integer> featureCounts;
	public int processedNodes;
	
	public Cluster(String name, int clusterId, String label, Set<String> nodes) {
		this(name, clusterId, label, nodes, new HashMap<String, Integer>());
	}
	
	public Cluster(String name, int clusterId, String label, Set<String> nodes, Map<String, Integer> featureCounts) {
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