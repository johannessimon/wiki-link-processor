package de.tudarmstadt.lt.wsi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import de.tudarmstadt.lt.util.IndexUtil;
import de.tudarmstadt.lt.util.IndexUtil.Index;
import de.tudarmstadt.lt.util.MapHelper;

public class ClusterReaderWriter {
	final static Charset UTF_8 = Charset.forName("UTF-8");


	public static void writeClusters(Writer writer, Map<String, List<Cluster<String>>> clusters) throws IOException {
		writeClusters(writer, clusters, IndexUtil.<String>getIdentityIndex());
	}
	
	public static <N> void writeClusters(Writer writer, Map<String, List<Cluster<N>>> clusters, Index<String, N> index) throws IOException {
		for (Entry<String, List<Cluster<N>>> clusterList : clusters.entrySet()) {
			for (Cluster<N> c : clusterList.getValue()) {
				writeCluster(writer, c, index);
			}
		}
	}

	public static void writeCluster(Writer writer, Cluster<String> cluster) throws IOException {
		writeCluster(writer, cluster, IndexUtil.<String>getIdentityIndex());
	}

	public static <N> void writeCluster(Writer writer, Cluster<N> cluster, Index<String, N> index) throws IOException {
		writer.write(cluster.name + "\t" + cluster.clusterId + "\t" + cluster.label + "\t");
		boolean first = true;
		for (N node : cluster.nodes) {
			if (!first) {
				writer.write("  ");
			}
			writer.write(index.get(node));
			first = false;
		}
		if (!cluster.featureCounts.isEmpty()) {
			writer.write("\t");
			Map<N, Integer> sortedFeatureCountes = MapHelper.sortMapByValue(cluster.featureCounts);
			first = true;
			for (Entry<N, Integer> featureCount : sortedFeatureCountes.entrySet()) {
				if (!first) {
					writer.write("  ");
				}
				writer.write(index.get(featureCount.getKey()) + ":" + featureCount.getValue());
				first = false;
			}
		}
		writer.write("\n");
	}

	public static Map<String, List<Cluster<String>>> readClusters(Reader in) throws IOException {
		return readClusters(in, IndexUtil.<String>getIdentityIndex());
	}
	
	public static <N> Map<N, List<Cluster<N>>> readClusters(Reader in, Index<String, N> index) throws IOException {
		System.out.println("Reading clusters...");
		Map<N, List<Cluster<N>>> clusters = new HashMap<N, List<Cluster<N>>>();
		
		BufferedReader reader = new BufferedReader(in);
		String line;
		while ((line = reader.readLine()) != null) {
			String[] lineSplits = line.split("\t");
			N clusterName = index.getIndex(lineSplits[0]);
			int clusterId = Integer.parseInt(lineSplits[1]);
			N clusterLabel = index.getIndex(lineSplits[2]);
			String[] clusterNodes = lineSplits[3].split("  ");
			Set<N> clusterNodeSet = new HashSet<N>(5);
			for (String clusterNode : clusterNodes) {
				if (!clusterNode.isEmpty()) {
					clusterNodeSet.add(index.getIndex(clusterNode));
				}
			}
			Map<N, Integer> clusterFeatureCounts = new HashMap<N, Integer>();
			if (lineSplits.length >= 5) {
				String[] clusterFeatures = lineSplits[4].split("  ");
				for (String featureCountPair : clusterFeatures) {
					if (!featureCountPair.isEmpty()) {
						int sepIndex = featureCountPair.lastIndexOf(':');
						if (sepIndex >= 0) {
							try {
								N feature = index.getIndex(featureCountPair.substring(0, sepIndex));
								Integer featureCount = Integer.parseInt(featureCountPair.substring(sepIndex + 1));
								clusterFeatureCounts.put(feature, featureCount);
							} catch (NumberFormatException e) {
								System.err.println("Error (1): malformatted feature-count pair: " + featureCountPair);
							}
						} else {
							System.err.println("Error (2): malformatted feature-count pair: " + featureCountPair);
						}
					}
				}
			}
			addCluster(clusters, clusterName, new Cluster<N>(clusterName, clusterId, clusterLabel, clusterNodeSet, clusterFeatureCounts));
		}
		return clusters;
	}
	
	public static <N> void addCluster(Map<N, List<Cluster<N>>> clusters, N name, Cluster<N> cluster) {
		List<Cluster<N>> clusterSet = clusters.get(name);
		if (clusterSet == null) {
			clusterSet = new ArrayList<Cluster<N>>();
			clusters.put(name, clusterSet);
		}
		clusterSet.add(cluster);
	}
}
