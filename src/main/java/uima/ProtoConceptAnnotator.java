package uima;

import static org.apache.uima.fit.util.JCasUtil.select;

import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
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

import uima.type.WikiLink;
import de.tudarmstadt.lt.wsi.Cluster;
import de.tudarmstadt.lt.wsi.ClusterReaderWriter;

public class ProtoConceptAnnotator extends JCasAnnotator_ImplBase {
	public static final String PARAM_EXTRACTOR_CONFIGURATION_FILE = "ExtractorConfigurationFile";
	public static final String PARAM_CLUSTER_FILE = "ClusterFile";
	public static final String PARAM_OUTPUT_FILE = "OutputFile";
	
	JobimAnnotationExtractor extractor;
	Map<String, List<Cluster>> clusters;
	BufferedWriter writer;
	BufferedWriter mappingWriter;
	
	int numProcessesSentences = 0;
	int numSingleWords = 0;
	int numSingleWordsMatched = 0;
	int numMappingFails = 0;
	int numMappingSuccesses = 0;
	int numMissingSenseClusters = 0;
	Map<String, Map<String, Integer>> conceptMappings = new HashMap<String, Map<String, Integer>>();
	
	private void registerMapping(String concept, String resource) {
		Map<String, Integer> conceptMapping = conceptMappings.get(concept);
		if (conceptMapping == null) {
			conceptMapping = new HashMap<String, Integer>();
			conceptMappings.put(concept, conceptMapping);
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
		String outputFileName = (String) context
				.getConfigParameterValue(PARAM_OUTPUT_FILE);
		try {
			extractor = JobimExtractorConfiguration
					.getExtractorFromXmlFile(extractorConfFileName);
			clusters = ClusterReaderWriter.readClusters(new FileInputStream(clusterFileName));
			System.out.println("Writing ProtoConceptAnnotator results to " + outputFileName);
			writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFileName)));
			mappingWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFileName + ".mappings")));
		} catch (Exception e) {
			throw new ResourceInitializationException(e);
		}
	}

	@Override
	public void collectionProcessComplete() throws AnalysisEngineProcessException {
		System.out.println("=== Statistics ===");
		System.out.println("# processed sentences:\t" + numProcessesSentences);
		System.out.println("# single-token words:\t" + numSingleWords);
		System.out.println("# single-token words covered entirely by JoBim annotation:\t" + numSingleWordsMatched);
		System.out.println("# Failed mappings:\t" + numMappingFails);
		System.out.println("# Successful mappings:\t" + numMappingSuccesses);
		System.out.println("# Missing sense clusters:\t" + numMissingSenseClusters);
		super.collectionProcessComplete();
	}

	@Override
	public void batchProcessComplete() throws AnalysisEngineProcessException {
		try {
			writer.close();
			
			for (Entry<String, Map<String, Integer>> mappings : conceptMappings.entrySet()) {
				String[] keySplits = mappings.getKey().split("\\.");
				mappingWriter.write(keySplits[0] + "\t" + keySplits[1] + "\t");
				for (Entry<String, Integer> mapping : mappings.getValue().entrySet()) {
					mappingWriter.write(mapping.getKey() + ":" + mapping.getValue() + "  ");
				}
				mappingWriter.write("\n");
			}
			
			mappingWriter.flush();
			mappingWriter.close();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			super.batchProcessComplete();
		}
	}

	@Override
	public void process(JCas aJCas) throws AnalysisEngineProcessException {
		for (Sentence s : select(aJCas, Sentence.class)) {
			numProcessesSentences++;
			if (numProcessesSentences % 100 == 0) {
				System.out.println("Processed sentences: " + numProcessesSentences);
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
						if (highestRankedSense == -1) {
							numMappingFails++;
						} else {
							numMappingSuccesses++;
							registerMapping(jo + "." + highestRankedSense, resource);
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
