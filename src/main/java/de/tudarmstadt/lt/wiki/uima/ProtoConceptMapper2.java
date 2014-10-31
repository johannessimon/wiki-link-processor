package de.tudarmstadt.lt.wiki.uima;

import static org.apache.uima.fit.factory.TypeSystemDescriptionFactory.createTypeSystemDescription;
import static org.apache.uima.fit.util.JCasUtil.select;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import org.apache.log4j.Logger;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.CasCreationUtils;
import org.jobimtext.holing.extractor.JobimAnnotationExtractor;
import org.jobimtext.holing.extractor.JobimExtractorConfiguration;
import org.jobimtext.holing.extractor.TokenExtractors;
import org.jobimtext.holing.type.JoBim;
import org.jobimtext.holing.type.Sentence;

import de.tudarmstadt.lt.util.FileUtil;
import de.tudarmstadt.lt.util.IndexUtil.StringIndex;
import de.tudarmstadt.lt.util.MapUtil;
import de.tudarmstadt.lt.util.MonitoredFileReader;
import de.tudarmstadt.lt.util.WikiUtil;
import de.tudarmstadt.lt.wiki.hadoop.WikiLinkCASExtractor;
import de.tudarmstadt.lt.wiki.uima.type.WikiLink;
import de.tudarmstadt.lt.wsi.Cluster;
import de.tudarmstadt.lt.wsi.ClusterReaderWriter;
import de.tudarmstadt.ukp.dkpro.core.maltparser.MaltParser;
import de.tudarmstadt.ukp.dkpro.core.opennlp.OpenNlpPosTagger;
import de.tudarmstadt.ukp.dkpro.core.opennlp.OpenNlpSegmenter;
import de.tudarmstadt.ukp.dkpro.core.stanfordnlp.StanfordLemmatizer;

public class ProtoConceptMapper2 {
	static Logger log = Logger.getLogger("de.tudarmstadt.lt.wiki.uima");
	
	String instanceOutputFile;
	String clusterMappingFile;
	String baselineMappingFile;
	JobimAnnotationExtractor extractor;
	final StringIndex strIndex = new StringIndex();
	Map<Integer, List<Cluster<Integer>>> clusters;
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

	public static AnalysisEngineDescription buildAnalysisEngine() throws ResourceInitializationException {
		AnalysisEngineDescription segmenter = AnalysisEngineFactory.createEngineDescription(OpenNlpSegmenter.class);
		AnalysisEngineDescription lemmatizer = AnalysisEngineFactory.createEngineDescription(StanfordLemmatizer.class);
		AnalysisEngineDescription pos = AnalysisEngineFactory.createEngineDescription(OpenNlpPosTagger.class);
		AnalysisEngineDescription deps = AnalysisEngineFactory.createEngineDescription(MaltParser.class);
		AnalysisEngineDescription depHoling = AnalysisEngineFactory.createEngineDescription(DependencyHolingAnnotator.class);

		return AnalysisEngineFactory.createEngineDescription(
				segmenter, lemmatizer, pos, deps, depHoling);
	}
	
	public static void main(String[] args) {
		if (args.length != 6) {
			System.out.println("Usage: ProtoConceptMapper <linked sentence file> <cluster file> <instance output file> <cluster mapping file> <test mode> <word file>");
			return;
		}
		String linkedSentenceFile = args[0];
		String clusterFileName = args[1];
		String instanceOutputFile = args[2];
		String clusterMappingFile = args[3];
		boolean testMode = args[4].toLowerCase().equals("test");
		String wordFile = args[5];
		ProtoConceptMapper2 mapper = new ProtoConceptMapper2(clusterFileName, instanceOutputFile, clusterMappingFile, testMode, wordFile);

		try {
			BufferedReader reader = new BufferedReader(new MonitoredFileReader(linkedSentenceFile));
			AnalysisEngine engine = AnalysisEngineFactory.createEngine(buildAnalysisEngine());
			JCas jCas = CasCreationUtils.createCas(createTypeSystemDescription(), null, null).getJCas();
			WikiLinkCASExtractor linkExtractor = new WikiLinkCASExtractor();
			String line;
			while ((line = reader.readLine()) != null) {
				jCas.reset();
				String doc = linkExtractor.extractDocumentText(line);
				jCas.setDocumentText(doc);
				jCas.setDocumentLanguage("en");
				Sentence s = new Sentence(jCas, 0, doc.length());
				s.addToIndexes();
				linkExtractor.extractAnnotations(line, jCas.getCas());
				engine.process(jCas);
				mapper.processCas(jCas);
			}
			reader.close();
			mapper.writeResults();
		} catch (Exception e) {
			log.error("Processing failed", e);
		}
	}

	public ProtoConceptMapper2(String clusterFileName, String instanceOutputFile, String clusterMappingFile, boolean testMode, String wordFile) {
		this.instanceOutputFile = instanceOutputFile;
		this.clusterMappingFile = clusterMappingFile;
		this.baselineMappingFile = clusterMappingFile + "-baseline";
		try {
			Set<String> words = MapUtil.readSetFromFile(wordFile);
			clusters = ClusterReaderWriter.readClusters(new MonitoredFileReader(clusterFileName), strIndex, words);
			if (testMode) {
				log.info("Reading concept mappings from " + clusterMappingFile + " and " + baselineMappingFile);
				clusterMapping = ClusterReaderWriter.readClusterMapping(new MonitoredFileReader(clusterMappingFile), strIndex, words, clusters);
				baselineMapping = ClusterReaderWriter.readBaselineMapping(new MonitoredFileReader(baselineMappingFile), strIndex, words, clusters);
			} else {
				log.info("Writing concept mappings to " + clusterMappingFile + " and " + baselineMappingFile);
			}
			writer = FileUtil.createWriter(instanceOutputFile);
			log.info("Writing instance assignments to " + instanceOutputFile);
		} catch (Exception e) {
			log.error("Couldn't create ProtoConceptMapper2", e);
		}
		
		JobimExtractorConfiguration extractorConf = new JobimExtractorConfiguration();
		extractorConf.holeSymbol = "@@";
		extractorConf.valueDelimiter = ",";
		extractorConf.valueRelationPattern = "$relation($values)";
		extractor = new TokenExtractors.CoveredText(extractorConf);
	}

	public void writeResults() {
		evaluateConceptMapping();
		try {
			writer.close();
			if (!testMode) {
				writeBaselineConceptMappings();
				writeConceptMappings();
			}
		} catch (IOException e) {
			log.error("Failed to write results", e);
		}
	}
	
	public void writeBaselineConceptMappings() {
		log.info("Writing baseline concept mappings...");
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
			
			mappingWriter.close();
		} catch (IOException e) {
			log.error("Failed to write baseline mappings", e);
		}
	}
	
	public void writeConceptMappings() {
		log.info("Writing concept mappings...");
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
			
			mappingWriter.close();
		} catch (IOException e) {
			log.error("Failed to write concept mappings", e);
		}
	}
	
	private void evaluateConceptMapping() {
		log.info("=== Statistics ===");
		log.info("# processed sentences:\t" + numProcessesSentences);
		log.info("# words covered entirely by JoBim annotation:\t" + numInstances);
		log.info("# - Successful assignments of cluster: " + numMappingSuccesses + "/" + numInstances);
		log.info("# - Failed assignments of cluster:     " + numMappingFails + "/" + numInstances);
		log.info("# - No clusters found at all:          " + numMissingSenseClusters + "/" + numInstances);
		log.info("");
		log.info("# Evaluation");
		log.info("# Correct mappings:                    " + numMappingMatches + "/" + numMappingSuccesses);
		log.info("# Incorrect mappings:                  " + numMappingMatchesFails + "/" + numMappingSuccesses);
		log.info("");
		log.info("# Baseline comparison");
		log.info("# Correct baseline mappings:           " + numBaselineMappingMatches + "/" + numMappingSuccesses);
		log.info("# Incorrect baseline mappings:         " + numBaselineMappingMatchesFails + "/" + numMappingSuccesses);
	}

	public void processCas(JCas aJCas) {
		for (Sentence s : select(aJCas, Sentence.class)) {
			numProcessesSentences++;
			if (numProcessesSentences % 1000 == 0) {
				evaluateConceptMapping();
			}
			for (WikiLink link : JCasUtil.selectCovered(WikiLink.class, s)) {
				String resource = WikiUtil.getLinkedResource(link.getResource());
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
					String mappedResourceBaseline = null;
					String mappedResourceCluster = null;
					if (testMode) {
						mappedResourceBaseline = baselineMapping.get(jo);
						mappedResourceCluster = mappedResourceBaseline;
					} else {
						registerMapping(baselineConceptMappings, jo, resource);
					}
					
					numInstances++;
					Map<Cluster<Integer>, Integer> senseScores = new TreeMap<Cluster<Integer>, Integer>();
					List<Cluster<Integer>> senseClusters = clusters.get(jo);
					if (senseClusters == null) {
						numMissingSenseClusters++;
//						log.error("No sense cluster found for jo: " + jo);
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

						if (highestRankedSense == null) {
							numMappingFails++;
						} else {
							numMappingSuccesses++;
							
							if (testMode) {
								String _mappedResourceCluster = clusterMapping.get(highestRankedSense);
								if (_mappedResourceCluster != null) {
									mappedResourceCluster = _mappedResourceCluster;
								}
							} else {
								registerMapping(conceptMappings, highestRankedSense, resource);
							}
						}
						
						try {
							String sense = highestRankedSense != null ? Integer.toString(highestRankedSense.clusterId) : "NULL";
							writer.write(strIndex.get(jo) + "\t" + sense + "\t" + resource + "\t" + s.getCoveredText() + "\n");
						} catch (IOException e) {
							log.error("Failed to write to output", e);
						}
					}
					
					if (testMode) {
						if (mappedResourceCluster != null && mappedResourceCluster.equals(resource)) {
							numMappingMatches++;
						} else {
							numMappingMatchesFails++;
						}
						if (mappedResourceBaseline != null && mappedResourceBaseline.equals(resource)) {
							numBaselineMappingMatches++;
						} else {
							numBaselineMappingMatchesFails++;
						}
					}
				}
			}
		}
	}

}
