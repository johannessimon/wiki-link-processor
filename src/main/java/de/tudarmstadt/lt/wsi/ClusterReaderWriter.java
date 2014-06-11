package de.tudarmstadt.lt.wsi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import util.MapHelper;

public class ClusterReaderWriter {
	final static Charset UTF_8 = Charset.forName("UTF-8");

	public static void writeClusters(Writer writer, Map<String, List<Cluster>> clusters) throws IOException {
		for (Entry<String, List<Cluster>> clusterList : clusters.entrySet()) {
			for (Cluster c : clusterList.getValue()) {
				writeCluster(writer, c);
			}
		}
	}

	public static void writeCluster(Writer writer, Cluster cluster) throws IOException {
		writer.write(cluster.name + "\t" + cluster.clusterId + "\t" + cluster.label + "\t");
		for (String node : cluster.nodes) {
			writer.write(node + "  ");
		}
		if (!cluster.featureCounts.isEmpty()) {
			writer.write("\t");
			Map<String, Integer> sortedFeatureCountes = MapHelper.sortMapByValue(cluster.featureCounts);
			for (Entry<String, Integer> featureCount : sortedFeatureCountes.entrySet()) {
				writer.write(featureCount.getKey() + ":" + featureCount.getValue() + "  ");
			}
		}
		writer.write("\n");
	}
	
	public static Map<String, List<Cluster>> readClusters(InputStream is) throws IOException {
		System.out.println("Reading clusters...");
		Map<String, List<Cluster>> clusters = new HashMap<String, List<Cluster>>();
		
		BufferedReader reader = new BufferedReader(new InputStreamReader(is, UTF_8));
		String line;
		while ((line = reader.readLine()) != null) {
			String[] lineSplits = line.split("\t");
			String clusterName = lineSplits[0];
			int clusterId = Integer.parseInt(lineSplits[1]);
			String clusterLabel = lineSplits[2];
			String[] clusterNodes = lineSplits[3].split("  ");
			Set<String> clusterNodeSet = new LinkedHashSet<String>();
			for (String clusterNode : clusterNodes) {
				if (!clusterNode.isEmpty()) {
					clusterNodeSet.add(clusterNode);
				}
			}
			Map<String, Integer> clusterFeatureCounts = new LinkedHashMap<String, Integer>();
			if (lineSplits.length >= 5) {
				String[] clusterFeatures = lineSplits[4].split("  ");
				for (String feature : clusterFeatures) {
					if (!feature.isEmpty()) {
						int sepIndex = feature.lastIndexOf(':');
						if (sepIndex >= 0) {
							try {
								String featureName = feature.substring(0, sepIndex);
								String featureCount = feature.substring(sepIndex + 1);
								clusterFeatureCounts.put(featureName, Integer.parseInt(featureCount));
							} catch (NumberFormatException e) {
								System.err.println("Error (1): malformatted feature-count pair: " + feature);
							}
						} else {
							System.err.println("Error (2): malformatted feature-count pair: " + feature);
						}
					}
				}
			}
			addCluster(clusters, clusterName, new Cluster(clusterName, clusterId, clusterLabel, clusterNodeSet, clusterFeatureCounts));
		}
		return clusters;
	}
	
	public static void addCluster(Map<String, List<Cluster>> clusters, String name, Cluster cluster) {
		List<Cluster> clusterSet = clusters.get(name);
		if (clusterSet == null) {
			clusterSet = new LinkedList<Cluster>();
			clusters.put(name, clusterSet);
		}
		clusterSet.add(cluster);
	}
}
