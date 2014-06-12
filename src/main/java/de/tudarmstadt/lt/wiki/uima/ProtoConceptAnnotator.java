package de.tudarmstadt.lt.wiki.uima;

import static org.apache.uima.fit.util.JCasUtil.select;

import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Collection;
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

import de.tudarmstadt.lt.util.MapHelper;
import de.tudarmstadt.lt.wiki.uima.type.WikiLink;
import de.tudarmstadt.lt.wsi.Cluster;
import de.tudarmstadt.lt.wsi.ClusterReaderWriter;

public class ProtoConceptAnnotator extends JCasAnnotator_ImplBase {
	public static final String PARAM_EXTRACTOR_CONFIGURATION_FILE = "ExtractorConfigurationFile";
	public static final String PARAM_CLUSTER_FILE = "ClusterFile";
	public static final String PARAM_REDIRECTS_FILE = "RedirectsFile";
	public static final String PARAM_OUTPUT_FILE = "OutputFile";
	
	JobimAnnotationExtractor extractor;
	Map<String, List<Cluster>> clusters;
	Map<String, String> redirects;
	BufferedWriter writer;
	String outputFileName;
	
	int numRedirectsReplaced = 0;
	int numProcessesSentences = 0;
	int numSingleWords = 0;
	int numSingleWordsMatched = 0;
	int numMappingFails = 0;
	int numMappingSuccesses = 0;
	int numMissingSenseClusters = 0;
	Map<String, Map<String, Integer>> baselineConceptMappings = new HashMap<String, Map<String, Integer>>();
	Map<String, Map<String, Integer>> conceptMappings = new HashMap<String, Map<String, Integer>>();
	
	private void registerMapping(Map<String, Map<String, Integer>> map, String concept, String resource) {
		Map<String, Integer> conceptMapping = map.get(concept);
		if (conceptMapping == null) {
			conceptMapping = new HashMap<String, Integer>();
			map.put(concept, conceptMapping);
		}
		Integer mappingCount = conceptMapping.get(resource);
		if (mappingCount == null) {
			mappingCount = 0;
		}
		mappingCount++;
		conceptMapping.put(resource, mappingCount);
	}

	@Override
	public void initialize(UimaContext context)
			throws ResourceInitializationException {
		super.initialize(context);
		String extractorConfFileName = (String) context
				.getConfigParameterValue(PARAM_EXTRACTOR_CONFIGURATION_FILE);
		String clusterFileName = (String) context
				.getConfigParameterValue(PARAM_CLUSTER_FILE);
		outputFileName = (String) context
				.getConfigParameterValue(PARAM_OUTPUT_FILE);
		String redirectsFileName = (String) context
				.getConfigParameterValue(PARAM_REDIRECTS_FILE);
		try {
			extractor = JobimExtractorConfiguration
					.getExtractorFromXmlFile(extractorConfFileName);
			clusters = ClusterReaderWriter.readClusters(new FileInputStream(clusterFileName));
			System.out.println("Writing ProtoConceptAnnotator results to " + outputFileName);
			writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFileName)));
			redirects = MapHelper.readMapFromFile(redirectsFileName, "\t");
		} catch (Exception e) {
			throw new ResourceInitializationException(e);
		}
	}

	@Override
	public void collectionProcessComplete() throws AnalysisEngineProcessException {
		evaluateConceptMapping();
		super.collectionProcessComplete();
	}
	
	public void writeConceptMappings() {
		System.out.println("Writing concept mappings...");
		BufferedWriter mappingWriter;
		try {
			mappingWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFileName + ".mappings")));
			for (Entry<String, Map<String, Integer>> mappings : conceptMappings.entrySet()) {
				String[] keySplits = mappings.getKey().split("\\.");
				mappingWriter.write(keySplits[0] + "\t" + keySplits[1] + "\t");
				Map<String, Integer> sortedMappingCounts = MapHelper.sortMapByValue(mappings.getValue());
				for (Entry<String, Integer> mapping : sortedMappingCounts.entrySet()) {
					mappingWriter.write(mapping.getKey() + ":" + mapping.getValue() + "  ");
				}
				mappingWriter.write("\n");
			}
			
			mappingWriter.flush();
			mappingWriter.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void batchProcessComplete() throws AnalysisEngineProcessException {
		try {
			writer.close();
			writeConceptMappings();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			super.batchProcessComplete();
		}
	}
	
	private void evaluateConceptMapping() {
		System.out.println("=== Statistics ===");
		System.out.println("# redirects replaced:\t" + numRedirectsReplaced);
		System.out.println("# processed sentences:\t" + numProcessesSentences);
		System.out.println("# single-token words:\t" + numSingleWords);
		System.out.println("# single-token words covered entirely by JoBim annotation:\t" + numSingleWordsMatched);
		System.out.println("# Failed mappings:\t" + numMappingFails);
		System.out.println("# Successful mappings:\t" + numMappingSuccesses);
		System.out.println("# Missing sense clusters:\t" + numMissingSenseClusters);
		System.out.println("=== Evaluating baseline word mappings ===");
		evaluateMapping(baselineConceptMappings.values());
		System.out.println("=== Evaluating proto concept mappings ===");
		evaluateMapping(conceptMappings.values());
	}
	
	private void evaluateMapping(Collection<Map<String, Integer>> mappings) {
		int totalMappings = 0;
		int failMappings = 0;
		int nonTrivialTotalMappings = 0;
		int nonTrivialFailMappings = 0;
		int numTrivialCases = 0;
		int numCases = 0;
		for (Map<String, Integer> mapping : mappings) {
			numCases++;
			int total = 0;
			int max = -1;
			for (Integer count : mapping.values()) {
				if (count > max) {
					max = count;
				}
				total += count;
			}
			if (mapping.size() == 1) {
				numTrivialCases++;
			} else {
				nonTrivialTotalMappings += total;
				nonTrivialFailMappings += total - max;
				
			}
			totalMappings += total;
			failMappings += total - max;
		}
		System.out.println("# total mapping instances: " + totalMappings);
		System.out.println("# failed mapping instances: " + failMappings);
		System.out.println("# non-trivial total mapping instances: " + nonTrivialTotalMappings);
		System.out.println("# non-trivial failed mapping instances: " + nonTrivialFailMappings);
		System.out.println("# map entries: " + numCases);
		System.out.println("# trivial mappings (subset of map entries): " + numTrivialCases);
	}

	@Override
	public void process(JCas aJCas) throws AnalysisEngineProcessException {
		for (Sentence s : select(aJCas, Sentence.class)) {
			numProcessesSentences++;
			if (numProcessesSentences % 1000 == 0) {
				writeConceptMappings();
				evaluateConceptMapping();
			}
			for (WikiLink link : JCasUtil.selectCovered(WikiLink.class, s)) {
				List<JoBim> jobims = JCasUtil.selectCovered(JoBim.class, link);
				
				String jo = null;
				Set<String> bims = new HashSet<String>();
				for (JoBim jobim : jobims) {
					// Ignore multi-words (we can't handle them right now)
					if (link.getBegin() == jobim.getBegin() &&
						link.getEnd() == jobim.getEnd()) {
						if (jo == null) {
							jo = extractor.extractKey(jobim);
						}
						bims.add(extractor.extractValues(jobim));
					}
				}
				
				if (!link.getCoveredText().contains(" ")) {
					numSingleWords++;
				}
				
				if (jo != null) {
					numSingleWordsMatched++;
					Map<Integer, Integer> senseScores = new HashMap<Integer, Integer>();
					List<Cluster> senseClusters = clusters.get(jo);
					if (senseClusters == null) {
						numMissingSenseClusters++;
//						System.err.println("No sense cluster found for jo: " + jo);
					} else {
						for (Cluster cluster : senseClusters) {
							int score = 0;
							for (Entry<String, Integer> feature : cluster.featureCounts.entrySet()) {
								if (bims.contains(feature.getKey())) {
									score += feature.getValue();
								}
							}
							if (score > 0) {
								senseScores.put(cluster.clusterId, score);
							}
						}
						
	//					if (senseClusters.size() > 1) {
	//						System.out.println("Multi-cluster word!");
	//					}
						
						int highestRankedSense = -1;
						int highestScore = -1;
						for (int sense : senseScores.keySet()) {
							int score = senseScores.get(sense);
							if (score > highestScore) {
								highestRankedSense = sense;
								highestScore = score;
							}
						}

						String resource = link.getResource();
						String redirectedResource = redirects.get(resource);
						if (redirectedResource != null) {
							resource = redirectedResource;
							numRedirectsReplaced++;
						}
						if (highestRankedSense == -1) {
							numMappingFails++;
						} else {
							numMappingSuccesses++;
							registerMapping(baselineConceptMappings, jo, resource);
							registerMapping(conceptMappings, jo + "." + highestRankedSense, resource);
						}
						
						try {
							writer.write(jo + "." + highestRankedSense + " -> " + resource + "\t" + s.getCoveredText() + "\n");
						} catch (IOException e) {
							throw new AnalysisEngineProcessException(e);
						}
					}
				}
			}
		}
	}

}
