package de.tudarmstadt.lt.wsi.aggregation;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import de.tudarmstadt.lt.wsi.Cluster;
import de.tudarmstadt.lt.wsi.ClusterReaderWriter;

public class ContextClueAggregator {
	final static Charset UTF_8 = Charset.forName("UTF-8");
	Map<String, List<Cluster>> clusters = new HashMap<String, List<Cluster>>();
	BufferedWriter writer;
	
	public ContextClueAggregator(BufferedWriter writer) {
		this.writer = writer;
	}
	
	class ValueComparator<K, V extends Comparable<V>> implements Comparator<K> {
	    Map<K, V> base;
	    public ValueComparator(Map<K, V> base) {
	        this.base = base;
	    }

	    // Note: this comparator imposes orderings that are inconsistent with equals.    
	    public int compare(K a, K b) {
	        if (base.get(a).compareTo(base.get(b)) > 0) {
	            return -1;
	        } else {
	            return 1;
	        } // returning 0 would merge keys
	    }
	}
	
	public void incrementCount(Map<String, Integer> map, String key) {
		Integer count = map.get(key);
		if (count == null) {
			count = 0;
		}
		count++;
		map.put(key, count);
	}
	
	public void readContextFeatures(InputStream is) throws IOException {
		BufferedReader reader = new BufferedReader(new InputStreamReader(is, UTF_8));
		String line;
		while ((line = reader.readLine()) != null) {
			StringTokenizer columns = new StringTokenizer(line, "\t");
			String clusterName = columns.nextToken();
			String node = columns.nextToken();
			columns.nextToken(); // Skip cluster label
			StringTokenizer features = new StringTokenizer(columns.nextToken(), "  ");
			List<Cluster> clusterList = clusters.get(clusterName);
			List<Cluster> finishedClusters = new LinkedList<Cluster>();
			if (clusterList != null) {
				for (Cluster c : clusterList) {
					if (c.nodes.contains(node)) {
						while (features.hasMoreTokens()) {
							String feature = features.nextToken();
//							feature = feature.trim();
							incrementCount(c.featureCounts, feature);
						}
						c.processedNodes++;
						if (c.processedNodes == c.nodes.size()) {
//							System.out.println("Early writing of cluster:\n" + c);
							ClusterReaderWriter.writeCluster(writer, c);
							finishedClusters.add(c);
						}
					}
				}
				clusterList.removeAll(finishedClusters);
				if (clusterList.isEmpty()) {
//					System.out.println("Early deletion of cluster: " + clusterName);
					clusters.remove(clusterName);
				}
			}
		}
	}
	
	public static void main(String[] args) throws IOException {
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("/Users/jsimon/No-Backup/wiki-holing-all/DT_senses_features-2")));
		ContextClueAggregator ccg = new ContextClueAggregator(writer);
		InputStream is = new FileInputStream("/Users/jsimon/No-Backup/wiki-holing-all/DT_senses-2");
//		System.out.println("Reading clusters...");
		ccg.clusters = ClusterReaderWriter.readClusters(is);
		InputStream is2 = new FileInputStream("/Users/jsimon/No-Backup/wiki-holing-all/DT_features");
		System.out.println("Processing context features...");
		ccg.readContextFeatures(is2);
		ClusterReaderWriter.writeClusters(writer, ccg.clusters);
	}
}
