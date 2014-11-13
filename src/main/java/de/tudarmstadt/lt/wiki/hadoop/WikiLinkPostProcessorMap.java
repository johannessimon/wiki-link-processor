package de.tudarmstadt.lt.wiki.hadoop;

import static org.apache.uima.fit.factory.TypeSystemDescriptionFactory.createTypeSystemDescription;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.log4j.Logger;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.cas.CASException;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.CasCreationUtils;

import de.tudarmstadt.lt.util.MapUtil;
import de.tudarmstadt.lt.util.MonitoredFileReader;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import de.tudarmstadt.ukp.dkpro.core.opennlp.OpenNlpPosTagger;
import de.tudarmstadt.ukp.dkpro.core.opennlp.OpenNlpSegmenter;
import de.tudarmstadt.ukp.dkpro.core.stanfordnlp.StanfordLemmatizer;

class WikiLinkPostProcessorMap extends Mapper<LongWritable, Text, Text, Text> {
	Logger log = Logger.getLogger("de.tudarmstadt.lt.wiki");
	
	Map<String, String> redirects = new HashMap<>();
	AnalysisEngine engine;
	JCas jCas;

	public AnalysisEngineDescription buildAnalysisEngine() throws ResourceInitializationException {
		AnalysisEngineDescription segmenter = AnalysisEngineFactory.createEngineDescription(OpenNlpSegmenter.class);
		AnalysisEngineDescription pos = AnalysisEngineFactory.createEngineDescription(OpenNlpPosTagger.class);
		AnalysisEngineDescription lemmatizer = AnalysisEngineFactory.createEngineDescription(StanfordLemmatizer.class);

		return AnalysisEngineFactory.createEngineDescription(
				segmenter, pos, lemmatizer);
	}
	
	@Override
	public void setup(Context context) throws IOException {
		Configuration conf = context.getConfiguration();
		FileSystem fs = FileSystem.get(conf);
		String redirectsFilePattern = conf.get("wiki.redirects.file");
		if (redirectsFilePattern != null) {
			log.info("Reading redirects file: " + redirectsFilePattern);
			try {
				Path filePath = new Path(redirectsFilePattern);//fileStat.getPath();
				FileStatus fileStat = fs.getFileStatus(filePath);
				long fileLen = fileStat.getLen();
				String fileName = filePath.getName();
				log.info("Processing redirects file: " + filePath);
				InputStream in = fs.open(filePath);
                BufferedReader reader = new BufferedReader(new MonitoredFileReader(fileName, in, fileLen, "UTF-8", 0.01));
                redirects = MapUtil.readMapFromReader(reader, "\t");
			} catch (Exception e) {
				log.error("Error reading redirect files", e);
			}
		} else {
			log.error("For redirects to be processed, you need to specify a redirects file"
					+ "pattern (on HDFS) using -Dwiki.redirects.file=...");
		}
		try {
			engine = AnalysisEngineFactory.createEngine(buildAnalysisEngine());
			jCas = CasCreationUtils.createCas(createTypeSystemDescription(), null, null).getJCas();
		} catch (ResourceInitializationException e) {
			log.error("Couldn't initialize analysis engine", e);
		} catch (CASException e) {
			log.error("Couldn't create new CAS", e);
		}
	}
	
	@Override
	public void map(LongWritable key, Text value, Context context)
		throws IOException, InterruptedException {
		try {
			String valueParts[] = value.toString().split("\t");
			String text = valueParts[0];
			String links[] = valueParts[1].split("  ");
			jCas.reset();
			jCas.setDocumentText(text);
			jCas.setDocumentLanguage("en");
			engine.process(jCas);
			LinkedList<String> redirectedLinks = new LinkedList<String>();
			for (String link : links) {
				String linkParts[] = link.split("@");
				String target = linkParts[0];
				String spanStr[] = linkParts[1].split(":");
				int begin = Integer.parseInt(spanStr[0]);
				int end = Integer.parseInt(spanStr[1]);
				List<Token> coveredTokens = JCasUtil.selectCovered(jCas, Token.class, begin, end);
				if (coveredTokens.size() == 1) {
					Token token = coveredTokens.get(0);
					String pos = token.getPos().getPosValue();
					if (pos.equals("NN") || pos.equals("NNS")) {
						String lemma = token.getLemma().getValue();
						String _target = redirects.get(target);
						if (_target != null) {
							target = _target;
						}
						redirectedLinks.add(lemma + "@@" + target + "@" + linkParts[1]);
					}
				}
			}
			if (!redirectedLinks.isEmpty()) {
				context.write(new Text(text), new Text(StringUtils.join(redirectedLinks, "  ")));
			}
		} catch (Exception e) {
			log.error("Can't process line: " + value.toString(), e);
		}
	}
}