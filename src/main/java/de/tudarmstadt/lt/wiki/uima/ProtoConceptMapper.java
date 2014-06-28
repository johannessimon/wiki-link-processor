package de.tudarmstadt.lt.wiki.uima;

import static org.apache.uima.fit.util.JCasUtil.select;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.jobimtext.holing.extractor.JobimAnnotationExtractor;
import org.jobimtext.holing.extractor.JobimExtractorConfiguration;
import org.jobimtext.holing.type.JoBim;
import org.jobimtext.holing.type.Sentence;

import de.tudarmstadt.lt.util.FileUtil;
import de.tudarmstadt.lt.util.IndexUtil.StringIndex;
import de.tudarmstadt.lt.util.MapUtil;
import de.tudarmstadt.lt.util.MonitoredFileReader;
import de.tudarmstadt.lt.util.WikiUtil;
import de.tudarmstadt.lt.wiki.uima.type.WikiLink;
import de.tudarmstadt.lt.wsi.Cluster;
import de.tudarmstadt.lt.wsi.ClusterReaderWriter;

public class ProtoConceptMapper extends JCasAnnotator_ImplBase {
	public static final String PARAM_EXTRACTOR_CONFIGURATION_FILE = "ExtractorConfigurationFile";
	public static final String PARAM_CLUSTER_FILE = "ClusterFile";
	public static final String PARAM_INSTANCE_OUTPUT_FILE = "InstanceOutputFile";
	public static final String PARAM_CLUSTER_MAPPING_FILE = "ClusterMappingFile";
	public static final String PARAM_REDIRECTS_FILE = "RedirectsFile";
	public static final String PARAM_WORD_FILE = "WordFile";
	public static final String PARAM_TEST_MODE = "TestMode";
	
	String instanceOutputFile;
	String clusterMappingFile;
	String baselineMappingFile;
	JobimAnnotationExtractor extractor;
	StringIndex strIndex = new StringIndex();
	Map<Integer, List<Cluster<Integer>>> clusters;
	Map<String, String> redirects;
	// (jo, sense) -> resource (MFS)
	Map<Cluster<Integer>, String> clusterMapping;
	// jo -> resource (MFS)
	Map<Integer, String> baselineMapping;
	BufferedWriter writer;
	boolean testMode;
	
	int numProcessesSentences = 0;
	int numInstances = 0;
	int numMappingFails = 0;
	int numMappingSuccesses = 0;
	int numMissingSenseClusters = 0;
	int numMappingMatches = 0;
	int numMappingMatchesFails = 0;
	int numBaselineMappingMatches = 0;
	int numBaselineMappingMatchesFails = 0;
	
	// (jo, sense) -> resource -> count
	Map<Cluster<Integer>, Map<String, Integer>> conceptMappings = new HashMap<Cluster<Integer>, Map<String, Integer>>();
	// jo -> resource -> count
	Map<Integer, Map<String, Integer>> baselineConceptMappings = new HashMap<Integer, Map<String, Integer>>();
	
	private <T> void registerMapping(Map<T, Map<String, Integer>> map, T key, String resource) {
		Map<String, Integer> conceptMapping = MapUtil.getOrCreate(map, key, HashMap.class);
		MapUtil.addIntTo(conceptMapping, resource, 1);
	}

	@Override
	public void initialize(UimaContext context)
			throws ResourceInitializationException {
		super.initialize(context);
		String extractorConfFileName = (String) context
				.getConfigParameterValue(PARAM_EXTRACTOR_CONFIGURATION_FILE);
		String clusterFileName = (String) context
				.getConfigParameterValue(PARAM_CLUSTER_FILE);
		instanceOutputFile = (String) context
				.getConfigParameterValue(PARAM_INSTANCE_OUTPUT_FILE);
		clusterMappingFile = (String) context
				.getConfigParameterValue(PARAM_CLUSTER_MAPPING_FILE);
		baselineMappingFile = clusterMappingFile + "-baseline";
		testMode = (Boolean) context
				.getConfigParameterValue(PARAM_TEST_MODE);
		String redirectsFileName = (String) context
				.getConfigParameterValue(PARAM_REDIRECTS_FILE);
		String wordFile = (String) context
				.getConfigParameterValue(PARAM_WORD_FILE);
		try {
			Set<String> words = MapUtil.readSetFromFile(wordFile);
			extractor = JobimExtractorConfiguration
					.getExtractorFromXmlFile(extractorConfFileName);
			clusters = ClusterReaderWriter.readClusters(new MonitoredFileReader(clusterFileName), strIndex, words);
			if (testMode) {
				System.out.println("Reading concept mappings from " + clusterMappingFile + " and " + baselineMappingFile);
				clusterMapping = ClusterReaderWriter.readClusterMapping(new MonitoredFileReader(clusterMappingFile), strIndex, words, clusters);
				baselineMapping = ClusterReaderWriter.readBaselineMapping(new MonitoredFileReader(baselineMappingFile), strIndex, words, clusters);
			} else {
				System.out.println("Writing concept mappings to " + clusterMappingFile + " and " + baselineMappingFile);
				writer = FileUtil.createWriter(instanceOutputFile);
			}
			System.out.println("Writing instance assignments to " + instanceOutputFile);
			redirects = MapUtil.readMapFromFile(redirectsFileName, "\t");
		} catch (Exception e) {
			throw new ResourceInitializationException(e);
		}
	}

	@Override
	public void collectionProcessComplete() throws AnalysisEngineProcessException {
		evaluateConceptMapping();
		super.collectionProcessComplete();
	}

	@Override
	public void batchProcessComplete() throws AnalysisEngineProcessException {
		try {
			writer.close();
			if (!testMode) {
				writeBaselineConceptMappings();
				writeConceptMappings();
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			super.batchProcessComplete();
		}
	}
	
	public void writeBaselineConceptMappings() {
		System.out.println("Writing concept mappings...");
		BufferedWriter mappingWriter;
		try {
			mappingWriter = FileUtil.createWriter(baselineMappingFile);
			for (Entry<Integer, Map<String, Integer>> mappings : baselineConceptMappings.entrySet()) {
				Integer jo = mappings.getKey();
				mappingWriter.write(strIndex.get(jo) + "\t");
				Map<String, Integer> sortedMappingCounts = MapUtil.sortMapByValue(mappings.getValue());
				boolean first = true;
				for (Entry<String, Integer> mapping : sortedMappingCounts.entrySet()) {
					if (!first) {
						mappingWriter.write("  ");
					}
					mappingWriter.write(mapping.getKey() + ":" + mapping.getValue());
					first = false;
				}
				mappingWriter.write("\n");
			}
			
			mappingWriter.flush();
			mappingWriter.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void writeConceptMappings() {
		System.out.println("Writing concept mappings...");
		BufferedWriter mappingWriter;
		try {
			mappingWriter = FileUtil.createWriter(clusterMappingFile);
			for (Entry<Cluster<Integer>, Map<String, Integer>> mappings : conceptMappings.entrySet()) {
				Cluster<Integer> c = mappings.getKey();
				Integer concept = c.name;
				Integer sense = c.clusterId;
				mappingWriter.write(strIndex.get(concept) + "\t" + sense + "\t");
				Map<String, Integer> sortedMappingCounts = MapUtil.sortMapByValue(mappings.getValue());
				boolean first = true;
				for (Entry<String, Integer> mapping : sortedMappingCounts.entrySet()) {
					if (!first) {
						mappingWriter.write("  ");
					}
					mappingWriter.write(mapping.getKey() + ":" + mapping.getValue());
					first = false;
				}
				mappingWriter.write("\n");
			}
			
			mappingWriter.flush();
			mappingWriter.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void evaluateConceptMapping() {
		System.out.println("=== Statistics ===");
		System.out.println("# processed sentences:\t" + numProcessesSentences);
		System.out.println("# words covered entirely by JoBim annotation:\t" + numInstances);
		System.out.println("# - Successful assignments of cluster: " + numMappingSuccesses + "/" + numInstances);
		System.out.println("# - Failed assignments of cluster:     " + numMappingFails + "/" + numInstances);
		System.out.println("# - No clusters found at all:          " + numMissingSenseClusters + "/" + numInstances);
		System.out.println();
		System.out.println("# Evaluation");
		System.out.println("# Correct mappings:                    " + numMappingMatches + "/" + numMappingSuccesses);
		System.out.println("# Incorrect mappings:                  " + numMappingMatchesFails + "/" + numMappingSuccesses);
		System.out.println();
		System.out.println("# Baseline comparison");
		System.out.println("# Correct baseline mappings:           " + numBaselineMappingMatches + "/" + numMappingSuccesses);
		System.out.println("# Incorrect baseline mappings:         " + numBaselineMappingMatchesFails + "/" + numMappingSuccesses);
	}

	@Override
	public void process(JCas aJCas) throws AnalysisEngineProcessException {
		for (Sentence s : select(aJCas, Sentence.class)) {
			numProcessesSentences++;
			if (numProcessesSentences % 1000 == 0) {
				evaluateConceptMapping();
			}
			for (WikiLink link : JCasUtil.selectCovered(WikiLink.class, s)) {
				List<JoBim> jobims = JCasUtil.selectCovered(JoBim.class, link);
				
				Integer jo = null;
				Set<Integer> bims = new HashSet<Integer>();
				for (JoBim jobim : jobims) {
					// Ignore multi-words (we can't handle them right now)
					if (link.getBegin() == jobim.getBegin() &&
						link.getEnd() == jobim.getEnd()) {
						if (jo == null) {
							jo = strIndex.getIndex(extractor.extractKey(jobim));
						}
						bims.add(strIndex.getIndex(extractor.extractValues(jobim)));
					}
				}
				
				if (jo != null) {
					numInstances++;
					Map<Cluster<Integer>, Integer> senseScores = new HashMap<Cluster<Integer>, Integer>();
					List<Cluster<Integer>> senseClusters = clusters.get(jo);
					if (senseClusters == null) {
						numMissingSenseClusters++;
//						System.err.println("No sense cluster found for jo: " + jo);
					} else {
						for (Cluster<Integer> cluster : senseClusters) {
							int score = 0;
							for (Entry<Integer, Integer> feature : cluster.featureCounts.entrySet()) {
								if (bims.contains(feature.getKey())) {
									score += feature.getValue();
								}
							}
							if (score > 0) {
								senseScores.put(cluster, score);
							}
						}
						
						Cluster<Integer> highestRankedSense = null;
						int highestScore = -1;
						for (Cluster<Integer> sense : senseScores.keySet()) {
							int score = senseScores.get(sense);
							if (score > highestScore) {
								highestRankedSense = sense;
								highestScore = score;
							}
						}

						String resource = WikiUtil.getLinkedResource(redirects, link.getResource());
						if (highestRankedSense == null) {
							numMappingFails++;
						} else {
							numMappingSuccesses++;
							
							if (clusterMapping != null) {
								String mappedResource = clusterMapping.get(highestRankedSense);
								if (mappedResource != null && mappedResource.equals(resource)) {
									numMappingMatches++;
								} else {
									numMappingMatchesFails++;
								}
							} else {
								registerMapping(conceptMappings, highestRankedSense, resource);
							}
							
							if (baselineMapping != null) {
								String mappedResource = baselineMapping.get(jo);
								if (mappedResource != null && mappedResource.equals(resource)) {
									numBaselineMappingMatches++;
								} else {
									numBaselineMappingMatchesFails++;
								}
							} else {
								registerMapping(baselineConceptMappings, jo, resource);
							}
						}
						
						try {
							String sense = highestRankedSense != null ? Integer.toString(highestRankedSense.clusterId) : "NULL";
							writer.write(strIndex.get(jo) + "\t" + sense + "\t" + resource + "\t" + s.getCoveredText() + "\n");
						} catch (IOException e) {
							throw new AnalysisEngineProcessException(e);
						}
					}
				}
			}
		}
	}

}
