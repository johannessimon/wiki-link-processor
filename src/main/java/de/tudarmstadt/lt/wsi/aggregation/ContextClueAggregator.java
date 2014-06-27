package de.tudarmstadt.lt.wsi.aggregation;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import de.tudarmstadt.lt.util.MapUtil;
import de.tudarmstadt.lt.util.MonitoredFileReader;
import de.tudarmstadt.lt.wsi.Cluster;
import de.tudarmstadt.lt.wsi.ClusterReaderWriter;

public class ContextClueAggregator {
	final static Charset UTF_8 = Charset.forName("UTF-8");
	Map<String, List<Cluster<String>>> clusters = new HashMap<String, List<Cluster<String>>>();
	BufferedWriter writer;
	
	public ContextClueAggregator(OutputStream os) {
		this.writer = new BufferedWriter(new OutputStreamWriter(os));
	}
	
	public void readContextFeatures(InputStream is) throws IOException {
		BufferedReader reader = new BufferedReader(new InputStreamReader(is, UTF_8));
		String line;
		List<Cluster<String>> finishedClusters = new LinkedList<Cluster<String>>();
		while ((line = reader.readLine()) != null) {
			StringTokenizer columns = new StringTokenizer(line, "\t");
			String clusterName = columns.nextToken();
			String node = columns.nextToken();
			columns.nextToken(); // Skip cluster label
			StringTokenizer features = new StringTokenizer(columns.nextToken(), "  ");
			List<Cluster<String>> clusterList = clusters.get(clusterName);
			finishedClusters.clear();
			if (clusterList != null) {
				for (Cluster<String> c : clusterList) {
					if (c.nodes.contains(node)) {
						while (features.hasMoreTokens()) {
							String feature = features.nextToken();
//							feature = feature.trim();
							MapUtil.addIntTo(c.featureCounts, feature, 1);
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
	
	public void writeClusters() throws IOException {
		ClusterReaderWriter.writeClusters(writer, clusters);
	}
	
	public static void main(String[] args) throws IOException {
		OutputStream os = new FileOutputStream("/Users/jsimon/No-Backup/wiki-holing-all/wiki-holing-all-cluster-withfeatures");
		ContextClueAggregator ccg = new ContextClueAggregator(os);
		Reader reader = new MonitoredFileReader("/Users/jsimon/No-Backup/wiki-holing-all/wiki-holing-all-simcounts-simsort-cluster");
//		System.out.println("Reading clusters...");
		ccg.clusters = ClusterReaderWriter.readClusters(reader);
		InputStream is2 = new FileInputStream("/Users/jsimon/No-Backup/wiki-holing-all/wiki-holing-all-simcounts-withfeatures");
		System.out.println("Processing context features...");
		ccg.readContextFeatures(is2);
		ccg.writeClusters();
	}
}
