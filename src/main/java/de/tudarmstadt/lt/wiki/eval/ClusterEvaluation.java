package de.tudarmstadt.lt.wiki.eval;

import static org.apache.uima.fit.factory.TypeSystemDescriptionFactory.createTypeSystemDescription;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.SequenceInputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.apache.log4j.Logger;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.CasCreationUtils;
import org.jobimtext.holing.extractor.JobimAnnotationExtractor;
import org.jobimtext.holing.extractor.JobimExtractorConfiguration;
import org.jobimtext.holing.type.Sentence;

import de.tudarmstadt.lt.util.FileUtil;
import de.tudarmstadt.lt.util.IndexUtil;
import de.tudarmstadt.lt.util.IndexUtil.StringIndex;
import de.tudarmstadt.lt.util.MapUtil;
import de.tudarmstadt.lt.util.MonitoredFileReader;
import de.tudarmstadt.lt.util.WikiUtil;
import de.tudarmstadt.lt.wiki.hadoop.WikiLinkCASExtractor;
import de.tudarmstadt.lt.wiki.uima.type.WikiLink;
import de.tudarmstadt.lt.wsi.Cluster;
import de.tudarmstadt.lt.wsi.ClusterReaderWriter;
import de.tudarmstadt.lt.wsi.WSD;
import de.tudarmstadt.lt.wsi.WSD.ContextClueScoreAggregation;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Lemma;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
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
	final static StringIndex strIndex = new StringIndex();
	static Map<Integer, List<Cluster<Integer>>> clusters;
	// (jo, sense) -> resource (MFS)
	Map<Cluster<Integer>, String> clusterMapping;
	// jo -> resource (MFS)
	Map<Integer, String> baselineMapping;
	BufferedWriter writer;
	boolean testMode;
	
	static Set<String> words;
	
	Statistics stats;
	
	// (jo, sense) -> resource -> count
	Map<Cluster<Integer>, Map<String, Integer>> conceptMappings = new HashMap<Cluster<Integer>, Map<String, Integer>>();
	// jo -> resource -> count
	Map<Integer, Map<String, Integer>> baselineConceptMappings = new HashMap<Integer, Map<String, Integer>>();
	
	public static void main(String[] args) throws Exception {
		if (args.length != 6) {
			System.out.println("Usage: ClusterEvaluation <linked sentence file> <cluster file> <instance output file> <cluster mapping file> <word file>");
			return;
		}
		String linkedSentenceFile = args[0];
		String clusterFileName = args[1];
		String instanceOutputFile = args[2];
		String clusterMappingFile = args[3];
		String wordFile = args[4];
		String wikiSenseFile = args[5];
		AnalysisEngine engine = AnalysisEngineFactory.createEngine(buildAnalysisEngine());
		
		Statistics trainStats = new Statistics();
		Statistics testStats = new Statistics();
		
		FileFilter fileFilter = new WildcardFileFilter(linkedSentenceFile);
		File[] files = new File(".").listFiles(fileFilter);

		words = MapUtil.readSetFromFile(wordFile);
		Set<String> wikiSenses = MapUtil.readSetFromFile(wikiSenseFile);
		clusters = ClusterReaderWriter.readClusters(new MonitoredFileReader(clusterFileName), strIndex, words);
		for (int i = 0; i < files.length; i++) {
			File testFile = files[i];
			Collection<InputStream> trainInputs = new ArrayList<InputStream>();
			BufferedReader testReader = new BufferedReader(new InputStreamReader(new FileInputStream(testFile), org.apache.commons.io.Charsets.UTF_8));
			for (int j = 0; j < files.length; j++) {
				if (i == j) continue;

				trainInputs.add(new FileInputStream(files[j]));
			}
			SequenceInputStream trainInput = new SequenceInputStream(Collections.enumeration(trainInputs));
			BufferedReader trainReader = new BufferedReader(new InputStreamReader(trainInput, org.apache.commons.io.Charsets.UTF_8));

			ClusterEvaluation train = new ClusterEvaluation(instanceOutputFile, clusterMappingFile, false, trainStats);
			train.run(trainReader, engine, wikiSenses);
			ClusterEvaluation test = new ClusterEvaluation(instanceOutputFile, clusterMappingFile, true, testStats);
			test.run(testReader, engine, wikiSenses);
			trainStats.print();
		}
		
		log.info("===== TEST RESULTS =====");
		testStats.print();
	}

	public ClusterEvaluation(String instanceOutputFile, String clusterMappingFile, boolean testMode, Statistics stats) {
		this.testMode = testMode;
		this.stats = stats;
		this.clusterMappingFile = clusterMappingFile;
		this.baselineMappingFile = clusterMappingFile + "-baseline";
		try {
//			postProcessClusters(clusters); // this turned out not to work too well
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
			log.error("Couldn't create ClusterEvaluation", e);
		}
		
		JobimExtractorConfiguration extractorConf = new JobimExtractorConfiguration();
		extractorConf.holeSymbol = "@@";
		extractorConf.valueDelimiter = ",";
		extractorConf.valueRelationPattern = "$relation($values)";
		extractor = new LemmaTextExtractor(extractorConf);
	}
	
	public void run(BufferedReader reader, AnalysisEngine engine, Set<String> wikiSenses) {
		try {
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
				processCas(jCas, wikiSenses);
			}
			reader.close();
			writeResults();
		} catch (Exception e) {
			log.error("Processing failed", e);
		}
	}

	public void processCas(JCas aJCas, Set<String> wikiSenses) {
		for (Sentence s : JCasUtil.select(aJCas, Sentence.class)) {
			stats.numProcessesSentences++;
//			if (stats.numProcessesSentences % 1000 == 0) {
//				evaluateConceptMapping();
//			}
			for (WikiLink link : JCasUtil.selectCovered(WikiLink.class, s)) {
				String resource = WikiUtil.getLinkedResource(link.getResource());
				
				if (!wikiSenses.contains(resource)) {
					continue;
				}
				
				List<Token> tokens = JCasUtil.selectCovered(Token.class, link);
				
				String jo = null;
				for (Token token : tokens) {
					// Ignore multi-words (we can't handle them right now)
					if (link.getBegin() == token.getBegin() &&
						link.getEnd() == token.getEnd()) {
						if (jo == null) {
							jo = token.getLemma().getValue();
						}
					}
				}
				Set<String> coocs = new HashSet<String>();
				for (Token t : JCasUtil.selectCovered(Token.class, s)) {
					coocs.add(t.getLemma().getValue());
				}
				coocs.remove(jo);
				
				if (jo != null && words.contains(jo)) {
					handleInstance(s.getCoveredText(), jo, coocs, resource);
				}
			}
		}
	}
	
	private void handleInstance(String sentence, String jo, Set<String> bims, String goldSense) {
		Integer joIndex = strIndex.getIndex(jo);
		Set<Integer> bimsIndices = new HashSet<Integer>(IndexUtil.mapToIndices(bims, strIndex));
		String mappedResourceBaseline = null;
		String mappedResourceCluster = null;
		String mappedResourceClusterWithBaselineBacking = null;
		if (testMode) {
			mappedResourceBaseline = baselineMapping.get(joIndex);
			mappedResourceClusterWithBaselineBacking = mappedResourceBaseline;
		} else {
			registerMapping(baselineConceptMappings, joIndex, goldSense);
		}
		
		stats.numInstances++;
		List<Cluster<Integer>> senseClusters = clusters.get(joIndex);
		if (senseClusters == null) {
			stats.numMissingSenseClusters++;
		} else {
			Map<Integer, Float> contextOverlap = new HashMap<Integer, Float>();
			Cluster<Integer> highestRankedSense = WSD.chooseCluster(senseClusters, bimsIndices, contextOverlap, ContextClueScoreAggregation.Sum);
			List<String> contextOverlapStr = new ArrayList<String>();
			Map<Integer, Float> contextOverlapSorted = MapUtil.sortMapByValue(contextOverlap);
			for (Integer contextOverlapFeature : contextOverlapSorted.keySet()) {
				float score = contextOverlap.get(contextOverlapFeature);
				String contextOverlapFeatureStr = strIndex.get(contextOverlapFeature);
				contextOverlapStr.add(contextOverlapFeatureStr + ":" + score);
			}

			if (highestRankedSense == null) {
				stats.numMappingFails++;
			} else {
				stats.numMappingSuccesses++;
				
				if (testMode) {
					mappedResourceCluster = clusterMapping.get(highestRankedSense);
					if (mappedResourceCluster != null) {
						mappedResourceClusterWithBaselineBacking = mappedResourceCluster;
					}
				} else {
					registerMapping(conceptMappings, highestRankedSense, goldSense);
				}
			}
			
			try {
				String sense = highestRankedSense != null ? Integer.toString(highestRankedSense.clusterId) : "NULL";
				writer.write(jo + "\t" + sense + "\t" + goldSense + "\t" + sentence + "\t" + contextOverlapStr + "\n");
			} catch (IOException e) {
				log.error("Failed to write to output", e);
			}
		}
		
		if (testMode) {
			if (goldSense.equals(mappedResourceCluster)) {
				stats.numMappingMatches++;
			} else if(mappedResourceCluster != null) {
				stats.numMappingMatchesFails++;
			}
			if (mappedResourceClusterWithBaselineBacking != null &&
				mappedResourceClusterWithBaselineBacking.equals(goldSense)) {
				stats.numBaselineBackedMappingMatches++;
			} else {
				stats.numBaselineBackedMappingMatchesFails++;
			}
			if (mappedResourceBaseline != null &&
				mappedResourceBaseline.equals(goldSense)) {
				stats.numBaselineMappingMatches++;
			} else {
				stats.numBaselineMappingMatchesFails++;
			}
		}
	}
	
	private <T> void registerMapping(Map<T, Map<String, Integer>> map, T key, String resource) {
		Map<String, Integer> conceptMapping = MapUtil.getOrCreate(map, key, HashMap.class);
		MapUtil.addIntTo(conceptMapping, resource, 1);
	}
	
	private static class LemmaTextExtractor extends JobimAnnotationExtractor {
		public LemmaTextExtractor(JobimExtractorConfiguration configuration) {
			super(configuration);
		}

		public String extract(Annotation a) {
			Lemma t = ((Token)a).getLemma();
			String text = t.getValue();
			return text;
		}
	}

	public static AnalysisEngineDescription buildAnalysisEngine() throws ResourceInitializationException {
		AnalysisEngineDescription segmenter = AnalysisEngineFactory.createEngineDescription(OpenNlpSegmenter.class);
		AnalysisEngineDescription pos = AnalysisEngineFactory.createEngineDescription(OpenNlpPosTagger.class);
		AnalysisEngineDescription lemmatizer = AnalysisEngineFactory.createEngineDescription(StanfordLemmatizer.class);
//		AnalysisEngineDescription deps = AnalysisEngineFactory.createEngineDescription(MaltParser.class);
//		AnalysisEngineDescription depHoling = AnalysisEngineFactory.createEngineDescription(DependencyHolingAnnotator.class);

		return AnalysisEngineFactory.createEngineDescription(
				segmenter, pos, lemmatizer/*, deps, depHoling*/);
	}

	public void writeResults() {
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
}
