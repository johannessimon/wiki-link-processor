package de.tudarmstadt.lt.wiki.eval;

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
import de.tudarmstadt.lt.util.IndexUtil;
import de.tudarmstadt.lt.util.MapUtil;
import de.tudarmstadt.lt.util.MonitoredFileReader;
import de.tudarmstadt.lt.util.WikiUtil;
import de.tudarmstadt.lt.wiki.hadoop.WikiLinkCASExtractor;
import de.tudarmstadt.lt.wiki.uima.DependencyHolingAnnotator;
import de.tudarmstadt.lt.wiki.uima.type.WikiLink;
import de.tudarmstadt.lt.wsi.Cluster;
import de.tudarmstadt.lt.wsi.ClusterReaderWriter;
import de.tudarmstadt.lt.wsi.WSD;
import de.tudarmstadt.ukp.dkpro.core.maltparser.MaltParser;
import de.tudarmstadt.ukp.dkpro.core.opennlp.OpenNlpPosTagger;
import de.tudarmstadt.ukp.dkpro.core.opennlp.OpenNlpSegmenter;
import de.tudarmstadt.ukp.dkpro.core.stanfordnlp.StanfordLemmatizer;

/**
 * Evaluation for DT clusters based on Wikipedia links.<br/>
 * <br/>
 * <b>How it works:</b><br/>
 * Upfront, Wikipedia sentences are extracted that contain links having certain
 * target words as text. These are the words that you want to evaluate against.
 * The targets of these links are then assumed to be the "gold" senses of the
 * words in their specific context.<br/>
 * <br/>
 * The evaluation has two stages: train and test. During training, it iterates
 * over the given sentences and attempts to assign clusters to your target
 * words in context. It then counts how many times each cluster appears with
 * which gold sense. Based on this, it assigns a sense to each cluster.<br/>
 * <br/>
 * These assignments are then used in the test stage to determine the
 * discrepancy between the "Wikipedia senses" and the clusters that are assigned
 * in context. The evaluation does so by assuming the most frequent sense
 * that appeared with each cluster to be the Wikipedia sense alignment of the
 * cluster. After this, it simply counts how often there is a match between
 * this aligned sense and the actual gold sense in this context.
 * 
 * @author Johannes Simon
 *
 */
public class ClusterEvaluation {
	static Logger log = Logger.getLogger("de.tudarmstadt.lt.wiki.eval");
	
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
	
	Set<String> words;
	
	// (jo, sense) -> resource -> count
	Map<Cluster<Integer>, Map<String, Integer>> conceptMappings = new HashMap<Cluster<Integer>, Map<String, Integer>>();
	// jo -> resource -> count
	Map<Integer, Map<String, Integer>> baselineConceptMappings = new HashMap<Integer, Map<String, Integer>>();
	
	public static void main(String[] args) {
		if (args.length != 6) {
			System.out.println("Usage: ClusterEvaluation <linked sentence file> <cluster file> <instance output file> <cluster mapping file> <test mode> <word file>");
			return;
		}
		String linkedSentenceFile = args[0];
		String clusterFileName = args[1];
		String instanceOutputFile = args[2];
		String clusterMappingFile = args[3];
		boolean testMode = args[4].toLowerCase().equals("true");
		String wordFile = args[5];
		ClusterEvaluation mapper = new ClusterEvaluation(clusterFileName, instanceOutputFile, clusterMappingFile, testMode, wordFile);

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

	public ClusterEvaluation(String clusterFileName, String instanceOutputFile, String clusterMappingFile, boolean testMode, String wordFile) {
		this.testMode = testMode;
		this.clusterMappingFile = clusterMappingFile;
		this.baselineMappingFile = clusterMappingFile + "-baseline";
		try {
			words = MapUtil.readSetFromFile(wordFile);
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

	public void processCas(JCas aJCas) {
		for (Sentence s : select(aJCas, Sentence.class)) {
			numProcessesSentences++;
			if (numProcessesSentences % 1000 == 0) {
				evaluateConceptMapping();
			}
			for (WikiLink link : JCasUtil.selectCovered(WikiLink.class, s)) {
				String resource = WikiUtil.getLinkedResource(link.getResource());
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
				
				if (jo != null && words.contains(jo)) {
					handleInstance(s.getCoveredText(), jo, bims, resource);
				}
			}
		}
	}
	
	private void handleInstance(String sentence, String jo, Set<String> bims, String goldSense) {
		Integer joIndex = strIndex.getIndex(jo);
		Set<Integer> bimsIndices = new HashSet<Integer>(IndexUtil.mapToIndices(bims, strIndex));
		String mappedResourceBaseline = null;
		String mappedResourceCluster = null;
		if (testMode) {
			mappedResourceBaseline = baselineMapping.get(joIndex);
			mappedResourceCluster = mappedResourceBaseline;
		} else {
			registerMapping(baselineConceptMappings, joIndex, goldSense);
		}
		
		numInstances++;
		List<Cluster<Integer>> senseClusters = clusters.get(joIndex);
		if (senseClusters == null) {
			numMissingSenseClusters++;
		} else {
			Cluster<Integer> highestRankedSense = WSD.chooseCluster(senseClusters, bimsIndices);

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
					registerMapping(conceptMappings, highestRankedSense, goldSense);
				}
			}
			
			try {
				String sense = highestRankedSense != null ? Integer.toString(highestRankedSense.clusterId) : "NULL";
				writer.write(strIndex.get(joIndex) + "\t" + sense + "\t" + goldSense + "\t" + sentence + "\n");
			} catch (IOException e) {
				log.error("Failed to write to output", e);
			}
		}
		
		if (testMode) {
			if (mappedResourceCluster != null && mappedResourceCluster.equals(goldSense)) {
				numMappingMatches++;
			} else {
				numMappingMatchesFails++;
			}
			if (mappedResourceBaseline != null && mappedResourceBaseline.equals(goldSense)) {
				numBaselineMappingMatches++;
			} else {
				numBaselineMappingMatchesFails++;
			}
		}
	}
	
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
		log.info("# Correct mappings:                    " + numMappingMatches + "/" + numInstances);
		log.info("# Incorrect mappings:                  " + numMappingMatchesFails + "/" + numInstances);
		log.info("");
		log.info("# Baseline comparison");
		log.info("# Correct baseline mappings:           " + numBaselineMappingMatches + "/" + numInstances);
		log.info("# Incorrect baseline mappings:         " + numBaselineMappingMatchesFails + "/" + numInstances);
	}
}
